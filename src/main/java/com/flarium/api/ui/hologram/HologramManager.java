package com.flarium.api.ui.hologram;

import org.bukkit.Location;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HologramManager {
    CompletableFuture<Hologram> createHologram(Location location);
    Hologram getHologram(UUID uuid);
    Collection<Hologram> getHolograms();
    void removeHologram(UUID uuid);
    void shutdown();
}