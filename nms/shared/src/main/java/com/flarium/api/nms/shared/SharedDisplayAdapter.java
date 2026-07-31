package com.flarium.api.nms.shared;

import com.flarium.api.nms.DisplayAdapter;
import com.mojang.math.Transformation;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Set;

public class SharedDisplayAdapter implements DisplayAdapter {

    private final Display display;

    public SharedDisplayAdapter(World world, boolean text) {
        ServerLevel level = ((CraftWorld) world).getHandle();
        this.display = text
                ? new Display.TextDisplay(EntityType.TEXT_DISPLAY, level)
                : new Display.ItemDisplay(EntityType.ITEM_DISPLAY, level);
    }

    @Override
    public void sendSpawn(Player player, Location location) {
        setPosition(location);
        send(player, new ClientboundAddEntityPacket(
                display.getId(),
                display.getUUID(),
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getPitch(),
                location.getYaw(),
                display.getType(),
                0,
                Vec3.ZERO,
                0.0
        ));
        sendUpdate(player);
    }

    @Override
    public void sendUpdate(Player player) {
        List<SynchedEntityData.DataValue<?>> values = display.getEntityData().getNonDefaultValues();
        if (values != null) {
            send(player, new ClientboundSetEntityDataPacket(display.getId(), values));
        }
    }

    @Override
    public void sendTeleport(Player player, Location location) {
        setPosition(location);
        send(player, ClientboundTeleportEntityPacket.teleport(
                display.getId(),
                new PositionMoveRotation(
                        new Vec3(location.getX(), location.getY(), location.getZ()),
                        Vec3.ZERO,
                        location.getYaw(),
                        location.getPitch()
                ),
                Set.of(),
                false
        ));
    }

    @Override
    public void sendDestroy(Player player) {
        send(player, new ClientboundRemoveEntitiesPacket(display.getId()));
    }

    @Override
    public void setPosition(Location location) {
        display.setPos(location.getX(), location.getY(), location.getZ());
    }

    @Override
    public Location getLocation() {
        return new Location(display.level().getWorld(), display.getX(), display.getY(), display.getZ());
    }

    @Override
    public int getEntityId() {
        return display.getId();
    }

    @Override
    public Entity getBukkitEntity() {
        return display.getBukkitEntity();
    }

    @Override
    public void setText(Component component) {
        textDisplay().setText(PaperAdventure.asVanilla(component));
    }

    @Override
    public void setBackgroundColor(int argb) {
        textDisplay().getEntityData().set(Display.TextDisplay.DATA_BACKGROUND_COLOR_ID, argb);
    }

    @Override
    public void setAlignment(Alignment alignment) {
        Display.TextDisplay handle = textDisplay();
        byte flags = handle.getFlags();
        flags = (byte) (flags & ~(Display.TextDisplay.FLAG_ALIGN_LEFT | Display.TextDisplay.FLAG_ALIGN_RIGHT));
        switch (alignment) {
            case LEFT -> flags = (byte) (flags | Display.TextDisplay.FLAG_ALIGN_LEFT);
            case RIGHT -> flags = (byte) (flags | Display.TextDisplay.FLAG_ALIGN_RIGHT);
        }
        handle.setFlags(flags);
    }

    @Override
    public void setLineWidth(int width) {
        textDisplay().getEntityData().set(Display.TextDisplay.DATA_LINE_WIDTH_ID, width);
    }

    @Override
    public void setTextOpacity(byte opacity) {
        textDisplay().setTextOpacity(opacity);
    }

    @Override
    public void setItem(ItemStack item) {
        ((Display.ItemDisplay) display).setItemStack(CraftItemStack.asNMSCopy(item));
    }

    @Override
    public void setBillboard(org.bukkit.entity.Display.Billboard billboard) {
        display.setBillboardConstraints(Display.BillboardConstraints.valueOf(billboard.name()));
    }

    @Override
    public void setTransformation(Vector3f translation, Vector3f scale) {
        display.setTransformation(new Transformation(translation, new Quaternionf(), scale, new Quaternionf()));
    }

    private Display.TextDisplay textDisplay() {
        return (Display.TextDisplay) display;
    }

    private void send(Player player, Packet<?> packet) {
        ((CraftPlayer) player).getHandle().connection.send(packet);
    }
}
