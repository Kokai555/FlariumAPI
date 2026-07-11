package com.flarium.api.ui.hologram.impl;

import com.flarium.api.data.pdc.PDCManager;
import com.flarium.api.data.pdc.UUIDDataType;
import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.ui.hologram.Hologram;
import com.flarium.api.ui.hologram.HologramManager;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

import java.time.Duration;
import java.util.UUID;

public class HologramListener implements Listener {

    private final HologramManager hologramManager;
    private final PDCManager pdcManager;
    private final Scheduler scheduler;

    public HologramListener(HologramManager hologramManager, PDCManager pdcManager, Scheduler scheduler) {
        this.hologramManager = hologramManager;
        this.pdcManager = pdcManager;
        this.scheduler = scheduler;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof ArmorStand || entity instanceof org.bukkit.entity.Display || entity instanceof Interaction) {
                UUID hologramId = pdcManager.get(entity, "hologram_id", new UUIDDataType());
                if (hologramId != null && hologramManager.getHologram(hologramId) == null) {
                    scheduler.runAtLocation(entity.getLocation(), entity::remove);
                }
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scheduler.runGlobalDelayed(() -> {
            hologramManager.getHolograms().forEach(Hologram::updateVisibility);
        }, Duration.ofSeconds(1));
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof ArmorStand) && !(clicked instanceof Interaction)) return;

        UUID hologramId = pdcManager.get(clicked, "hologram_id", new UUIDDataType());
        if (hologramId == null) return;

        Hologram hologram = hologramManager.getHologram(hologramId);
        if (hologram != null) {
            event.setCancelled(true);
            hologram.handleClick(event.getPlayer());
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        Entity damaged = event.getEntity();
        if (!(damaged instanceof ArmorStand) && !(damaged instanceof Interaction)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        UUID hologramId = pdcManager.get(damaged, "hologram_id", new UUIDDataType());
        if (hologramId == null) return;

        Hologram hologram = hologramManager.getHologram(hologramId);
        if (hologram != null) {
            event.setCancelled(true);
            hologram.handleClick(player);
        }
    }
}