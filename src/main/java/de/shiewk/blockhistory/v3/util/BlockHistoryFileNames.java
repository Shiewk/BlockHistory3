package de.shiewk.blockhistory.v3.util;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.nio.file.Path;

public final class BlockHistoryFileNames {
    private BlockHistoryFileNames(){}

    private static char base32encode(short i){
        return switch (i){
            case 0 -> '0';
            case 1 -> '1';
            case 2 -> '2';
            case 3 -> '3';
            case 4 -> '4';
            case 5 -> '5';
            case 6 -> '6';
            case 7 -> '7';
            case 8 -> '8';
            case 9 -> '9';
            case 10 -> 'a';
            case 11 -> 'b';
            case 12 -> 'c';
            case 13 -> 'd';
            case 14 -> 'e';
            case 15 -> 'f';
            case 16 -> 'g';
            case 17 -> 'h';
            case 18 -> 'i';
            case 19 -> 'j';
            case 20 -> 'k';
            case 21 -> 'l';
            case 22 -> 'm';
            case 23 -> 'n';
            case 24 -> 'o';
            case 25 -> 'p';
            case 26 -> 'q';
            case 27 -> 'r';
            case 28 -> 's';
            case 29 -> 't';
            case 30 -> 'u';
            case 31 -> 'v';
            default -> throw new IllegalStateException("Unexpected value: " + i);
        };
    }

    public static Path encode(Path parentDirectory, Location location){
        // encoded string is 13 characters long
        World world = location.getWorld();
        Chunk chunk = location.getChunk();
        int chunkX = chunk.getX();
        int chunkZ = chunk.getZ();
        // 20 bytes
        long packed = 0;

        packed |= chunkX & 0b1111111111111111111L;
        if (chunkX < 0) packed |= 0b10000000000000000000;

        packed |= (chunkZ & 0b1111111111111111111L) << 20;
        if (chunkZ < 0) packed |= 0b1000000000000000000000000000000000000000L;

        String encodedChunkFileName = new String(new char[]{
                base32encode((short) (packed >> 35 & 0b11111)),
                base32encode((short) (packed >> 30 & 0b11111)),
                base32encode((short) (packed >> 25 & 0b11111)),
                base32encode((short) (packed >> 20 & 0b11111)),
                base32encode((short) (packed >> 15 & 0b11111)),
                base32encode((short) (packed >> 10 & 0b11111)),
                base32encode((short) (packed >> 5 & 0b11111)),
                base32encode((short) (packed & 0b11111))
        });

        return Path.of(parentDirectory.toString(),
                world.getWorldFolder().getName(),
                encodedChunkFileName
        );
    }
}
