package com.flarium.api.ui.hologram.impl;

import com.flarium.api.data.pdc.PDCManager;
import com.flarium.api.data.pdc.UUIDDataType;
import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.ui.hologram.Hologram;
import com.flarium.api.ui.hologram.HologramManager;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Interaction;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class FlariumHologramManager implements HologramManager {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final PDCManager pdcManager;
    private final ConcurrentHashMap<UUID, Hologram> holograms = new ConcurrentHashMap<>();

    public FlariumHologramManager(Plugin plugin, Scheduler scheduler, PDCManager pdcManager) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.pdcManager = pdcManager;
    }

    @Override
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

            Interaction interaction = location.getWorld().spawn(location, Interaction.class, inter -> {
                inter.setInteractionWidth(1.0f);
                inter.setInteractionHeight(1.0f);
                inter.setResponsive(true);
                pdcManager.set(inter, "hologram_id", new UUIDDataType(), hologramId);
            });

            Hologram hologram = new FlariumHologram(plugin, scheduler, anchor, interaction);
            holograms.put(hologramId, hologram);
            future.complete(hologram);
        });

        return future;
    }

    @Override
    public Hologram getHologram(UUID uuid) {
        return holograms.get(uuid);
    }

    @Override
    public Collection<Hologram> getHolograms() {
        return holograms.values();
    }

    @Override
    public void removeHologram(UUID uuid) {
        Hologram hologram = holograms.remove(uuid);
        if (hologram != null) {
            scheduler.runAtLocation(hologram.getAnchor().getLocation(), hologram::remove);
        }
    }

    @Override
    public void shutdown() {
        holograms.values().forEach(hologram -> {
            scheduler.runAtLocation(hologram.getAnchor().getLocation(), hologram::remove);
        });
        holograms.clear();
    }
}