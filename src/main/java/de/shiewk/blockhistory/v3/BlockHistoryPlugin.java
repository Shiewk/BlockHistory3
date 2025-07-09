package de.shiewk.blockhistory.v3;

import de.shiewk.blockhistory.v3.command.BlockHistoryCommand;
import de.shiewk.blockhistory.v3.listener.BlockListener;
import de.shiewk.blockhistory.v3.util.SchedulerUtil;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static net.kyori.adventure.text.Component.text;

public final class BlockHistoryPlugin extends JavaPlugin {

    public static final boolean isFolia;

    static {
        boolean folia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            folia = true;
        } catch (ClassNotFoundException e) {
            folia = false;
        }
        isFolia = folia;
    }

    private static ComponentLogger LOGGER = null;
    private static BlockHistoryPlugin INSTANCE = null;

    public static final TextColor COLOR_PRIMARY = TextColor.color(0xff8500),
                                    COLOR_SECONDARY = TextColor.color(0xff0011),
                                    COLOR_FAIL = TextColor.color(0xCF0000);

    public static final Component CHAT_PREFIX = text("BlockHistory \u00BB ", COLOR_SECONDARY);

    private HistoryManager historyManager;
    private StatManager statManager;

    @Override
    public void onLoad() {
        INSTANCE = this;
        LOGGER = getComponentLogger();
    }

    @Override
    public void onEnable() {
        LOGGER.info("Folia: {}", isFolia ? "yes" : "no");
        statManager = new StatManager();
        Path saveDirectory = Path.of(getDataFolder().getPath(), "history");
        try {
            Files.createDirectories(saveDirectory);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        historyManager = new HistoryManager(LOGGER, saveDirectory, statManager);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, this::registerCommands);

        SchedulerUtil.scheduleGlobalRepeating(this, BlockListener::clearCache, 6000, 6000);

        listen(new BlockListener());
    }

    private void registerCommands(@NotNull ReloadableRegistrarEvent<Commands> event) {
        Commands commands = event.registrar();
        commands.register(new BlockHistoryCommand().getCommandNode());
    }

    private void listen(Listener listener){
        Bukkit.getPluginManager().registerEvents(listener, this);
    }

    @Override
    public void onDisable() {
        historyManager.shutdown();
    }

    public HistoryManager getHistoryManager() {
        return historyManager;
    }

    public StatManager getStatManager() {
        return statManager;
    }

    public static BlockHistoryPlugin instance() {
        return INSTANCE;
    }

    public static ComponentLogger logger() {
        return LOGGER;
    }
}
