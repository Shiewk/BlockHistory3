package de.shiewk.blockhistory.v3.util;

import de.shiewk.blockhistory.v3.BlockHistoryPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

public final class SchedulerUtil {
    private SchedulerUtil(){}

    public static void scheduleGlobal(Plugin plugin, Runnable task){
        if (BlockHistoryPlugin.isFolia){
            Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
        } else {
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, task);
        }
    }

    public static void scheduleGlobalRepeating(Plugin plugin, Runnable task, int delay, int interval) {
        if (BlockHistoryPlugin.isFolia){
            Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin, t -> task.run(), delay, interval);
        } else {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, task, delay, interval);
        }
    }
}
