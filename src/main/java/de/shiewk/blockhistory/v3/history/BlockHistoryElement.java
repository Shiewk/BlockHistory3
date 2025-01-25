package de.shiewk.blockhistory.v3.history;

import de.shiewk.blockhistory.v3.util.UnitUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import static de.shiewk.blockhistory.v3.BlockHistoryPlugin.COLOR_PRIMARY;
import static de.shiewk.blockhistory.v3.BlockHistoryPlugin.COLOR_SECONDARY;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

public record BlockHistoryElement(
        @NotNull World world,
        int x,
        int y,
        int z,
        @NotNull BlockHistoryType type,
        long timestamp,
        @Nullable UUID player,
        @NotNull Material material,
        byte[] additionalData
) {


    public BlockHistoryElement(
            @NotNull World world,
            int x,
            int y,
            int z,
            @NotNull BlockHistoryType type,
            long timestamp,
            @Nullable UUID player,
            @NotNull Material material,
            byte @Nullable [] additionalData
    ) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(material, "material");
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
        this.timestamp = timestamp;
        this.player = player;
        this.material = material;
        this.additionalData = Objects.requireNonNullElse(additionalData, new byte[0]);
    }

    public static final DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.LONG);

    public Component toComponent(Function<UUID, Component> playerNameResolver) {
        Component playerName = playerNameResolver.apply(player);
        Date date = Date.from(Instant.ofEpochMilli(timestamp));
        TextComponent component = text("Block ", COLOR_PRIMARY)
                .append(translatable(material, COLOR_SECONDARY))
                .append(text(" was "))
                .append(text(type.displayName, COLOR_SECONDARY))
                .append(text(" by "))
                .append(playerName.colorIfAbsent(COLOR_SECONDARY))
                .append(text(" at "))
                .append(text(dateFormat.format(date)));
        if (additionalData.length > 0 && type == BlockHistoryType.SIGN){
            component = component.append(
                    text(": \"")
                            .append(MiniMessage.miniMessage().deserialize(new String(additionalData)))
                            .append(text("\""))
            );
        }
        return component;
    }

    public static BlockHistoryElement read(int dataVersion, DataInputStream dataIn, World world, int chunkX, int chunkZ) throws IOException {
        return switch (dataVersion) {
            case 0 -> readv0(dataIn, world, chunkX, chunkZ);
            default -> throw new IllegalStateException("Unknown data version: " + dataVersion);
        };
    }

    private static BlockHistoryElement readv0(DataInputStream dataIn, World world, int chunkX, int chunkZ) throws IOException {
        byte chunkRelativeX = dataIn.readByte();
        int realX = chunkX < 0 ? (chunkX+1) * 16 - (16 - chunkRelativeX) : chunkX * 16 + chunkRelativeX;
        byte chunkRelativeZ = dataIn.readByte();
        int realZ = chunkZ < 0 ? (chunkZ+1) * 16 - (16 - chunkRelativeZ) : chunkZ * 16 + chunkRelativeZ;
        int y = dataIn.readInt();
        BlockHistoryType type = BlockHistoryType.values()[dataIn.readInt()];
        long timestamp = dataIn.readLong();
        UUID player;
        if (dataIn.readBoolean()){
            player = new UUID(dataIn.readLong(), dataIn.readLong());
        } else player = null;
        String materialName = dataIn.readUTF();
        Material material = Material.getMaterial(materialName);
        if (material == null){
            throw new IllegalStateException("Material " + materialName + " not found");
        }
        int additionalLength = dataIn.readInt();
        byte[] additionalData;
        if (additionalLength > 0){
            additionalData = dataIn.readNBytes(additionalLength);
        } else {
            additionalData = new byte[0];
        }
        return new BlockHistoryElement(
            world, realX, y, realZ, type, timestamp, player, material, additionalData
        );
    }

    public byte[] saveData() throws IOException {
        ByteArrayOutputStream arrayOut = new ByteArrayOutputStream();
        try (DataOutputStream dataOut = new DataOutputStream(arrayOut)) {
            dataOut.writeByte(0); // data version
            dataOut.writeByte(UnitUtil.getBlockChunkLocation(x));
            dataOut.writeByte(UnitUtil.getBlockChunkLocation(z));
            dataOut.writeInt(y);
            dataOut.writeInt(type.ordinal());
            dataOut.writeLong(timestamp);
            boolean writePlayerData = player != null;
            dataOut.writeBoolean(writePlayerData);
            if (writePlayerData){
                dataOut.writeLong(player.getMostSignificantBits());
                dataOut.writeLong(player.getLeastSignificantBits());
            }
            dataOut.writeUTF(material.name());
            dataOut.writeInt(additionalData.length);
            dataOut.flush();
        }
        arrayOut.write(additionalData);
        arrayOut.flush();
        return arrayOut.toByteArray();
    }

    public Location getLocation() {
        return new Location(world, x, y, z);
    }
}
