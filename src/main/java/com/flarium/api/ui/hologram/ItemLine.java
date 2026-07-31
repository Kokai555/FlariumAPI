package com.flarium.api.ui.hologram;

import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.nms.DisplayAdapter;
import com.flarium.api.nms.DisplayAdapters;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.joml.Vector3f;

public class ItemLine extends AbstractHologramLine {

    private ItemStack item;

    public ItemLine(Scheduler scheduler, ItemStack item) {
        super(scheduler);
        this.item = item;
    }

    @Override
    public void spawn(Location location) {
        DisplayAdapter displayAdapter;
        try {
            displayAdapter = DisplayAdapters.createItem(location.getWorld());
        } catch (UnsupportedOperationException e) {
            Bukkit.getLogger().warning("[FlariumAPI] Skipping hologram item line: " + e.getMessage());
            return;
        }
        setTranslation(new Vector3f(0, -0.25f, 0));
        applyDisplayProperties(displayAdapter);
        displayAdapter.setItem(item);
        setDisplayAdapter(displayAdapter);
    }

    @Override
    public void despawn() {
        DisplayAdapter displayAdapter = getDisplayAdapter();
        if (displayAdapter == null) return;
        for (Player player : getViewers()) {
            displayAdapter.sendDestroy(player);
        }
        setDisplayAdapter(null);
    }

    @Override
    public float getHeight() {
        return 0.5f;
    }

    public ItemLine item(ItemStack item) {
        this.item = item;
        if (getDisplayAdapter() != null) {
            getDisplayAdapter().setItem(item);
            sendUpdate();
        }
        return this;
    }
}
