package com.flarium.api.hologram;

import com.flarium.api.data.PDCManager;
import com.flarium.api.data.UUIDDataType;
import com.flarium.api.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class HologramManager {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final PDCManager pdcManager;
    private final ConcurrentHashMap<UUID, Hologram> holograms = new ConcurrentHashMap<>();

    public HologramManager(Plugin plugin, Scheduler scheduler, PDCManager pdcManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.pdcManager = pdcManager;
    }

    public CompletableFuture<Hologram> createHologram(Location location) {
        CompletableFuture<Hologram> future = new CompletableFuture<>();

        scheduler.runAtLocation(location, () -> {
            UUID hologramId = UUID.randomUUID();

            ArmorStand anchor = location.getWorld().spawn(location, ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setMarker(true);
                stand.setSmall(true);
                stand.setGravity(false);
                pdcManager.set(stand, "hologram_id", new UUIDDataType(), hologramId);
            });

            Hologram hologram = new Hologram(plugin, scheduler, anchor);
            holograms.put(hologramId, hologram);
            future.complete(hologram);
        });

        return future;
    }

    public java.util.Collection<Hologram> getHolograms() {
        return holograms.values();
    }

    public Hologram getHologram(UUID uuid) {
        return holograms.get(uuid);
    }

    public void removeHologram(UUID uuid) {
        Hologram hologram = holograms.remove(uuid);
        if (hologram != null) {
            scheduler.runAtLocation(hologram.getAnchor().getLocation(), hologram::remove);
        }
    }

    public void shutdown() {
        holograms.values().forEach(hologram -> {
            scheduler.runAtLocation(hologram.getAnchor().getLocation(), hologram::remove);
        });
        holograms.clear();
    }
}