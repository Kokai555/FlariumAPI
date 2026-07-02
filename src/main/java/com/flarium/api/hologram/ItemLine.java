package com.flarium.api.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;

public class ItemLine implements HologramLine {

    private ItemDisplay display;
    private final ItemStack item;

    public ItemLine(ItemStack item) {
        this.item = item;
    }

    @Override
    public void spawn(Location location) {
        this.display = location.getWorld().spawn(location, ItemDisplay.class, d -> {
            d.setItemStack(item);
            d.setBillboard(Display.Billboard.CENTER);
            d.setTransformation(new Transformation(
                    new org.joml.Vector3f(0, -0.5f, 0),
                    new org.joml.Quaternionf(),
                    new org.joml.Vector3f(1, 1, 1),
                    new org.joml.Quaternionf()
            ));
        });
    }

    @Override
    public void despawn() {
        if (display != null && !display.isDead()) display.remove();
    }

    @Override
    public float getHeight() {
        return 0.5f;
    }

    @Override
    public Entity getEntity() {
        return display;
    }
}