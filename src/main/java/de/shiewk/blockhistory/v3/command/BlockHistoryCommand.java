package de.shiewk.blockhistory.v3.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;
import de.shiewk.blockhistory.v3.BlockHistoryPlugin;
import de.shiewk.blockhistory.v3.StatManager;
import de.shiewk.blockhistory.v3.history.BlockHistoryElement;
import de.shiewk.blockhistory.v3.history.BlockHistorySearchCallback;
import de.shiewk.blockhistory.v3.util.CommandUtil;
import de.shiewk.blockhistory.v3.util.PlayerUtil;
import de.shiewk.blockhistory.v3.util.UnitUtil;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver;
import io.papermc.paper.math.BlockPosition;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import static de.shiewk.blockhistory.v3.BlockHistoryPlugin.*;
import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;
import static net.kyori.adventure.text.Component.*;

public final class BlockHistoryCommand {

    public @NotNull LiteralCommandNode<CommandSourceStack> getCommandNode() {
        return literal("blockhistory")
                .requires(CommandUtil.requirePermission("blockhistory.command.root"))
                .then(literal("stats")
                        .requires(CommandUtil.requirePermission("blockhistory.command.stats"))
                        .executes(this::statsCommand)
                )
                .then(literal("history")
                        .requires(CommandUtil.requirePermission("blockhistory.command.history"))
                        .then(argument("location", ArgumentTypes.blockPosition())
                                .executes(this::historyCommand)
                                .then(argument("world", ArgumentTypes.world())
                                        .executes(this::historyCommand)
                                )
                        )
                )
                .build();
    }

    private int statsCommand(CommandContext<CommandSourceStack> context) {
        CommandSender sender = context.getSource().getSender();
        StatManager statManager = BlockHistoryPlugin.instance().getStatManager();
        long millisSinceStart = statManager.getTimeMsSinceStart();
        sender.sendMessage(CHAT_PREFIX.append(
                text("The plugin has started up ", COLOR_PRIMARY)
                        .append(text(UnitUtil.formatTime(millisSinceStart), COLOR_SECONDARY))
                        .append(text(" ago."))
        ));
        sender.sendMessage(CHAT_PREFIX.append(
                text("So far, we have written ", COLOR_PRIMARY)
                        .append(text(statManager.getElementsWritten(), COLOR_SECONDARY))
                        .append(text(" history elements to disk. "))
                        .append(text("(%s elements per second)".formatted(statManager.getElementsWrittenPerSecond()), NamedTextColor.GRAY))
        ));
        sender.sendMessage(CHAT_PREFIX.append(
                text("These elements have a total size of ", COLOR_PRIMARY)
                        .append(text(UnitUtil.formatDataSize(statManager.getBytesWritten()), COLOR_SECONDARY))
                        .append(text(". "))
                        .append(text("(%s per second)".formatted(UnitUtil.formatDataSize(statManager.getBytesWrittenPerSecond())), NamedTextColor.GRAY))
        ));
        try {
            long usableDiskSpace = instance().getHistoryManager().getUsableDiskSpace();
            sender.sendMessage(CHAT_PREFIX.append(
                    text("There are ", COLOR_PRIMARY)
                            .append(text(UnitUtil.formatDataSize(usableDiskSpace), COLOR_SECONDARY))
                            .append(text(" of disk space available."))
            ));
        } catch (IOException e) {
            sender.sendMessage(CHAT_PREFIX.append(
                    text("Failed to get usable disk space", COLOR_FAIL)
            ));
            StringWriter strw = new StringWriter();
            e.printStackTrace(new PrintWriter(strw));

            BlockHistoryPlugin.logger().warn("Exception while getting usable disk space:");
            for (String s : strw.toString().split("\n")) {
                BlockHistoryPlugin.logger().warn(s);
            }
        }
        return Command.SINGLE_SUCCESS;
    }

    private int historyCommand(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack stack = context.getSource();
        CommandSender sender = stack.getSender();
        BlockPosition blockPosition = context.getArgument("location", BlockPositionResolver.class).resolve(stack);
        World world;
        try {
            world = context.getArgument("world", World.class);
        } catch (IllegalArgumentException ignored) {
            world = CommandUtil.getPlayerOf(stack, "You need to provide a world to search").getWorld();
        }
        int blockZ = blockPosition.blockZ();
        int blockY = blockPosition.blockY();
        int blockX = blockPosition.blockX();
        sender.sendMessage(CHAT_PREFIX.append(text("Searching in world %s at x=%s y=%s z=%s, please wait...\n".formatted(world.key(), blockX, blockY, blockZ), COLOR_PRIMARY)));
        try {
            World searchedWorld = world;
            long n = System.nanoTime();
            AtomicInteger foundElements = new AtomicInteger(0);
            BlockHistoryPlugin.instance().getHistoryManager().searchAsync(
                    new BlockHistoryCommandCallback(sender, foundElements),
                    world,
                    blockX,
                    blockY,
                    blockZ
            ).whenComplete((unused, throwable) -> {
                if (throwable != null){
                    StringWriter strw = new StringWriter();
                    throwable.printStackTrace(new PrintWriter(strw));

                    BlockHistoryPlugin.logger().warn("Exception while searching block at world {} x {} y {} z {}:", searchedWorld, blockX, blockY, blockZ);
                    for (String s : strw.toString().split("\n")) {
                        BlockHistoryPlugin.logger().warn(s);
                    }
                    sender.sendMessage(CHAT_PREFIX.append(text("An error occurred while searching, please check the server console.\n", COLOR_FAIL)));
                } else {
                    int elementsFound = foundElements.get();
                    sender.sendMessage((elementsFound > 0 ? newline() : empty()).append(CHAT_PREFIX.append(text("Search completed in %s ms, %s elements found".formatted((System.nanoTime() - n) / 1000000, elementsFound), COLOR_PRIMARY))));
                }
            });
        } catch (RejectedExecutionException e) {
            sender.sendMessage(text("The searching system is currently too busy, please try again later.", COLOR_FAIL));
        }
        return Command.SINGLE_SUCCESS;
    }

    private record BlockHistoryCommandCallback(CommandSender sender, AtomicInteger foundElements) implements BlockHistorySearchCallback {

        @Override
        public void onElementFound(BlockHistoryElement element) {
            sender.sendMessage(CHAT_PREFIX.append(element.toComponent(PlayerUtil::playerName)));
            foundElements.getAndIncrement();
        }

        @Override
        public void onNoFilePresent(FileNotFoundException e) {
            BlockHistoryPlugin.logger().info("No file present");
        }

    }
}
