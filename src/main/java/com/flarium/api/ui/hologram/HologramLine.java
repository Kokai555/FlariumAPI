package com.flarium.api.ui.hologram;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface HologramLine {
    void spawn(Location location);
    void despawn();
    float getHeight();
    Entity getEntity();
}