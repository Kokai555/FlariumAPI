package com.flarium.api.nms;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

public interface DisplayAdapter {

    void sendSpawn(Player player, Location location);
    void sendUpdate(Player player);
    void sendTeleport(Player player, Location location);
    void sendDestroy(Player player);

    void setPosition(Location location);
    Location getLocation();
    int getEntityId();
    Entity getBukkitEntity();

    void setText(Component component);
    void setBackgroundColor(int argb);
    void setAlignment(Alignment alignment);
    void setLineWidth(int width);
    void setTextOpacity(byte opacity);

    void setItem(ItemStack item);

    void setBillboard(Display.Billboard billboard);
    void setTransformation(Vector3f translation, Vector3f scale);

    enum Alignment {
        LEFT,
        CENTER,
        RIGHT
    }
}
