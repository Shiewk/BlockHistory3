package de.shiewk.blockhistory.v3.listener;

import de.shiewk.blockhistory.v3.BlockHistoryPlugin;
import de.shiewk.blockhistory.v3.history.BlockHistoryElement;
import de.shiewk.blockhistory.v3.history.BlockHistoryType;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.type.Bed;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;
import java.util.concurrent.RejectedExecutionException;

public final class BlockListener implements Listener {

    private static final Object2ObjectOpenHashMap<UUID, UUID> igniters = new Object2ObjectOpenHashMap<>();
    private static final Object2ObjectOpenHashMap<Block, UUID> blocks = new Object2ObjectOpenHashMap<>();

    public static void clearCache() {
        igniters.clear();
        blocks.clear();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event){
        final Block block = event.getBlock();
        createAndAddEntry(BlockHistoryType.BREAK, event.getPlayer(), block);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event){
        final Block block = event.getBlock();
        createAndAddEntry(BlockHistoryType.PLACE, event.getPlayer(), block);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBucketEmptied(PlayerBucketEmptyEvent event){
        Bukkit.getScheduler().scheduleSyncDelayedTask(BlockHistoryPlugin.instance(), () -> {
            final Block block = event.getBlock();
            createAndAddEntry(BlockHistoryType.EMPTY_BUCKET, event.getPlayer(), block);
        });
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBucketFilled(PlayerBucketFillEvent event){
        final Block block = event.getBlock();
        createAndAddEntry(BlockHistoryType.FILL_BUCKET, event.getPlayer(), block);
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityExplode(EntityExplodeEvent event){
        Entity entity = event.getEntity();
        switch (entity) {
            case TNTPrimed primed -> {
                for (Block block : event.blockList()) {
                    createAndAddEntry(BlockHistoryType.EXPLODE_TNT, primed.getSource() instanceof Player player ? player : null, block);
                }
            }
            case Creeper creeper -> {
                final UUID igniter = igniters.remove(creeper.getUniqueId());
                for (Block block : event.blockList()) {
                    createAndAddEntry(BlockHistoryType.EXPLODE_CREEPER, igniter, block);
                }
            }
            case EnderCrystal crystal -> {
                final UUID igniter = igniters.remove(crystal.getUniqueId());
                for (Block block : event.blockList()) {
                    createAndAddEntry(BlockHistoryType.EXPLODE_END_CRYSTAL, igniter, block);
                }
            }
            default -> {}
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockExplode(BlockExplodeEvent event){
        Block explodedBlock = event.getBlock();
        UUID exploder = blocks.remove(explodedBlock);
        BlockHistoryType type = BlockHistoryType.EXPLODE_BLOCK;
        for (Block block : event.blockList()) {
            createAndAddEntry(type, exploder, block);
        }
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamagedByEntity(EntityDamageByEntityEvent event){
        if (event.getDamager() instanceof Player player){
            if (event.getEntity() instanceof EnderCrystal enderCrystal){
                igniters.put(enderCrystal.getUniqueId(), player.getUniqueId());
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event){
        if (event.getRightClicked() instanceof Creeper creeper){
            if (event.getPlayer().getEquipment().getItemInMainHand().getAmount() > 0 || event.getPlayer().getEquipment().getItemInOffHand().getAmount() > 0){
                igniters.put(creeper.getUniqueId(), event.getPlayer().getUniqueId());
            }
        }
    }


    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event){
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK){
            final Block clickedBlock = event.getClickedBlock();
            if (clickedBlock.getType() == Material.RESPAWN_ANCHOR) {
                blocks.put(clickedBlock, event.getPlayer().getUniqueId());
            } else if (clickedBlock.getBlockData() instanceof Bed bed){
                final BlockFace facing = bed.getFacing();
                if (bed.getPart() == Bed.Part.FOOT){
                    Block actualBlock = new Location(clickedBlock.getWorld(), clickedBlock.getX() + (facing.getModX()), clickedBlock.getY() + (facing.getModY()), clickedBlock.getZ() + (facing.getModZ())).getBlock();
                    if (actualBlock.getBlockData() instanceof Bed){
                        blocks.put(actualBlock, event.getPlayer().getUniqueId());
                    } else {
                        BlockHistoryPlugin.logger().warn("Error: Bed does not exist");
                    }
                } else {
                    blocks.put(clickedBlock, event.getPlayer().getUniqueId());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSignChange(SignChangeEvent event){
        final Player player = event.getPlayer();
        final Block block = event.getBlock();
        final Location blockLocation = block.getLocation();
        final StringBuilder signData = new StringBuilder();
        final MiniMessage serializer = MiniMessage.miniMessage();
        for (Component line : event.lines()) {
            if (!signData.isEmpty()){
                signData.append(" ");
            }
            signData.append(serializer.serialize(line));
        }
        addEntryToManager(new BlockHistoryElement(
                block.getWorld(),
                blockLocation.getBlockX(),
                blockLocation.getBlockY(),
                blockLocation.getBlockZ(),
                BlockHistoryType.SIGN,
                System.currentTimeMillis(),
                player.getUniqueId(),
                block.getType(),
                signData.toString().getBytes()
        ));
    }

    private void addEntryToManager(BlockHistoryElement element){
        try {
            BlockHistoryPlugin.instance().getHistoryManager().addHistoryElement(element);
        } catch (RejectedExecutionException e){
            BlockHistoryPlugin.logger().warn("Too many actions are happening at the same time, skipping one");
        }
    }

    private void createAndAddEntry(BlockHistoryType type, Player player, Block block) {
        createAndAddEntry(type, player == null ? null : player.getUniqueId(), block);
    }

    private void createAndAddEntry(BlockHistoryType type, UUID player, Block block) {
        addEntryToManager(new BlockHistoryElement(
                block.getWorld(),
                block.getX(),
                block.getY(),
                block.getZ(),
                type,
                System.currentTimeMillis(),
                player,
                block.getType(),
                null
        ));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onContainerOpen(PlayerInteractEvent event){
        if (event.hasBlock() && event.getAction().isRightClick()){
            final Block block = event.getClickedBlock();
            if (block.getState() instanceof Container){
                createAndAddEntry(BlockHistoryType.CHEST_OPEN, event.getPlayer(), block);
            }
        }
    }
}
