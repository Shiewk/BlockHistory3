package de.shiewk.blockhistory.v3;

import de.shiewk.blockhistory.v3.exception.LowDiskSpaceException;
import de.shiewk.blockhistory.v3.history.BlockHistoryElement;
import de.shiewk.blockhistory.v3.history.BlockHistorySearchCallback;
import de.shiewk.blockhistory.v3.util.BlockHistoryFileNames;
import de.shiewk.blockhistory.v3.util.NamedLoggingThreadFactory;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class HistoryManager {

    private final Path saveDirectory;
    private final Logger logger;
    private final StatManager statManager;

    private final ThreadPoolExecutor writeExecutor;
    private final ThreadPoolExecutor readExecutor;

    HistoryManager(Logger logger, Path saveDirectory, StatManager statManager) {
        this.saveDirectory = saveDirectory;
        this.logger = logger;
        this.statManager = statManager;
        AtomicInteger threadNumber = new AtomicInteger();
        String threadName = "BlockHistoryIO";
        int threadPriority = 2;
        writeExecutor = new ThreadPoolExecutor(
                1,
                1,
                1,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(1000),
                new NamedLoggingThreadFactory(
                        threadName,
                        threadPriority,
                        logger,
                        "BlockHistory write I/O",
                        threadNumber
                )
        );
        readExecutor = new ThreadPoolExecutor(
                0,
                3,
                30,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(3),
                new NamedLoggingThreadFactory(
                        threadName,
                        threadPriority,
                        logger,
                        "BlockHistory read I/O",
                        threadNumber
                )
        );
    }

    public Path getSaveDirectory() {
        return saveDirectory;
    }

    void shutdown() {
        logger.info("Shutting down I/O executor...");
        long n = System.nanoTime();
        readExecutor.shutdown();
        writeExecutor.shutdown();
        try {
            if (!readExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                logger.warn("Read executor shutdown timed out after {}ms", (System.nanoTime() - n) / 1000000);
            }
            if (!writeExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                logger.warn("Read executor shutdown timed out after {}ms", (System.nanoTime() - n) / 1000000);
            }
        } catch (InterruptedException e){
            logger.warn("Thread interrupted while waiting for shutdown");
        }
        logger.info("Shutdown finished ({}ms)", (System.nanoTime() - n) / 1000000);
    }

    public CompletableFuture<Void> addHistoryElement(BlockHistoryElement element){
        Objects.requireNonNull(element);
        return CompletableFuture.runAsync(() -> {
            try {
                byte[] saveData = element.saveData();
                writeToDisk(
                        BlockHistoryFileNames.encode(saveDirectory, element.getLocation()),
                        saveData
                );
                statManager.bytesWritten(saveData.length);
                statManager.elementWritten();
            } catch (LowDiskSpaceException e) {
                BlockHistoryPlugin.logger().warn("Free disk space is too low to safely write further history elements: {} bytes", e.getFreeBytes());
            } catch (Exception e) {

                StringWriter strw = new StringWriter();
                e.printStackTrace(new PrintWriter(strw));

                BlockHistoryPlugin.logger().warn("Exception while writing history element:");
                for (String s : strw.toString().split("\n")) {
                    BlockHistoryPlugin.logger().warn(s);
                }
            }
        }, writeExecutor);
    }

    private void writeToDisk(Path path, byte[] saveData) throws LowDiskSpaceException, IOException {
        long usableDiskSpace = getUsableDiskSpace();
        if (usableDiskSpace < 8192){
            throw new LowDiskSpaceException(usableDiskSpace);
        }
        Files.createDirectories(path.getParent());
        Files.write(path, saveData, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
    }

    public long getUsableDiskSpace() throws IOException {
        return Files.getFileStore(saveDirectory).getUsableSpace();
    }

    public CompletableFuture<Void> searchAsync(BlockHistorySearchCallback callback, World world, int x, int y, int z) {
        return CompletableFuture.runAsync(() -> {
            try {
                this.search(callback, world, x, y, z);
            } catch (FileNotFoundException e) {
                callback.onNoFilePresent(e);
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }, readExecutor);
    }

    private void search(BlockHistorySearchCallback callback, World world, int x, int y, int z) throws IOException {
        Location location = new Location(world, x, y, z);
        Path path = BlockHistoryFileNames.encode(saveDirectory, location);
        try (FileInputStream fin = new FileInputStream(path.toFile())){
            try (DataInputStream dataIn = new DataInputStream(fin)){
                while (true) {
                    try {
                        int b = dataIn.readUnsignedByte();
                        BlockHistoryElement element = BlockHistoryElement.read(b, dataIn, world, location.getChunk().getX(), location.getChunk().getZ());
                        if (element.x() == x && element.y() == y && element.z() == z){
                            callback.onElementFound(element);
                        }
                    } catch (EOFException e) {
                        // done reading
                        return;
                    }
                }
            }
        }
    }

    public CompletableFuture<DiskSpaceApproximationVisitor> approximateDiskSpaceBytes() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                DiskSpaceApproximationVisitor visitor = new DiskSpaceApproximationVisitor();
                Files.walkFileTree(saveDirectory, visitor);
                return visitor;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, readExecutor);
    }

    public static class DiskSpaceApproximationVisitor implements FileVisitor<Path> {

        private long bytes = 0;
        private long directories = 0;
        private long files = 0;

        public long getDiskSpaceBytes(){
            return bytes;
        }

        public long getDirectoryCount() {
            return directories;
        }

        public long getFileCount() {
            return files;
        }

        @Override
        public @NotNull FileVisitResult preVisitDirectory(Path dir, @NotNull BasicFileAttributes attrs) {
            directories++;
            bytes += dir.getFileName().toString().getBytes().length;
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFile(Path file, @NotNull BasicFileAttributes attrs) {
            files++;
            bytes += file.getFileName().toString().getBytes().length;
            bytes += file.toFile().length();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public @NotNull FileVisitResult visitFileFailed(Path file, @NotNull IOException exc) throws IOException {
            throw exc;
        }

        @Override
        public @NotNull FileVisitResult postVisitDirectory(Path dir, @Nullable IOException exc) throws IOException {
            if (exc != null) throw exc;
            return FileVisitResult.CONTINUE;
        }
    }
}
