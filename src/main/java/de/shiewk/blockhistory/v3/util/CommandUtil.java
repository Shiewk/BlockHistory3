package de.shiewk.blockhistory.v3.util;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.MessageComponentSerializer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.Predicate;

public final class CommandUtil {
    private CommandUtil(){}

    public static @NotNull Predicate<CommandSourceStack> requirePermission(String permission) {
        return stack -> stack.getSender().hasPermission(permission);
    }

    public static @NotNull Player getPlayerOf(CommandSourceStack stack) throws CommandSyntaxException {
        return getPlayerOf(stack, "Only players can execute this (sub)command");
    }

    public static @NotNull Player getPlayerOf(CommandSourceStack stack, String errorMessage) throws CommandSyntaxException {
        if (stack.getSender() instanceof Player player){
            return player;
        } else throw new CommandSyntaxException(
                new SimpleCommandExceptionType(null),
                MessageComponentSerializer.message().serialize(
                        Component.text(errorMessage, NamedTextColor.RED)
                )
        );
    }
}
