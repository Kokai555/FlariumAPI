package com.flarium.api.ui.hologram;

import com.flarium.api.core.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class ItemLine extends AbstractHologramLine {

    private ItemStack item;

    public ItemLine(Scheduler scheduler, ItemStack item) {
        super(scheduler);
        this.item = item;
    }

    @Override
    public void spawn(Location location) {
        ItemDisplay display = location.getWorld().spawn(location, ItemDisplay.class, d -> {
            d.setItemStack(item);
            applyDisplayProperties(d);
            Transformation existing = d.getTransformation();
            d.setTransformation(new Transformation(
                    new Vector3f(0, -0.25f, 0),
                    existing.getLeftRotation(),
                    existing.getScale(),
                    existing.getRightRotation()
            ));
        });
        setEntity(display);
    }

    @Override
    public void despawn() {
        if (getEntity() != null && !getEntity().isDead()) getEntity().remove();
    }

    @Override
    public float getHeight() {
        return 0.5f;
    }

    public ItemLine item(ItemStack item) {
        this.item = item;
        if (getEntity() != null && getEntity().isValid()) {
            scheduler.runForEntity(getEntity(), () -> ((ItemDisplay) getEntity()).setItemStack(item));
        }
        return this;
    }
}