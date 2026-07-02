package com.flarium.api.hologram;

import com.flarium.api.data.PDCManager;
import com.flarium.api.data.UUIDDataType;
import com.flarium.api.scheduler.Scheduler;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.EntitiesLoadEvent;

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
            if (entity instanceof ArmorStand || entity instanceof org.bukkit.entity.Display) {
                UUID hologramId = pdcManager.get(entity, "hologram_id", new UUIDDataType());
                if (hologramId != null && hologramManager.getHologram(hologramId) == null) {
                    scheduler.runAtLocation(entity.getLocation(), entity::remove);
                }
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        UUID hologramId = pdcManager.get(stand, "hologram_id", new UUIDDataType());
        if (hologramId == null) return;

        Hologram hologram = hologramManager.getHologram(hologramId);
        if (hologram != null) {
            event.setCancelled(true);
            hologram.handleClick(event.getPlayer());
        }
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof ArmorStand stand)) return;
        if (!(event.getDamager() instanceof Player player)) return;

        UUID hologramId = pdcManager.get(stand, "hologram_id", new UUIDDataType());
        if (hologramId == null) return;

        Hologram hologram = hologramManager.getHologram(hologramId);
        if (hologram != null) {
            event.setCancelled(true);
            hologram.handleClick(player);
        }
    }
}