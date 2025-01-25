package de.shiewk.blockhistory.v3.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.UUID;

import static net.kyori.adventure.text.Component.text;

public final class PlayerUtil {
    private PlayerUtil(){}


    public static String offlinePlayerName(UUID uuid){
        if (uuid == null) return "Unknown Player";
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString() : player.getName();
    }

    public static Component playerName(UUID uuid) {
        Player player;
        if ((player = Bukkit.getPlayer(uuid)) != null){
            return player.displayName();
        } else {
            return text(offlinePlayerName(uuid));
        }
    }
}
