package com.flarium.api.ui.hologram.impl;

import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.ui.hologram.Hologram;
import com.flarium.api.ui.hologram.HologramLine;
import com.flarium.api.ui.hologram.RenderMode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class FlariumHologram implements Hologram {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final ArmorStand anchor;
    private final Interaction interaction;
    private final List<HologramLine> lines = new ArrayList<>();
    private Consumer<Player> clickAction;

    private RenderMode renderMode = RenderMode.ALL;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();

    public FlariumHologram(Plugin plugin, Scheduler scheduler, ArmorStand anchor, Interaction interaction) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.anchor = anchor;
        this.interaction = interaction;

        scheduler.runForEntity(anchor, () -> anchor.addPassenger(interaction));
    }

    @Override
    public void addLine(HologramLine line) {
        lines.add(line);
        recalculateOffsets();
        updateVisibility();
    }

    @Override
    public void removeLine(int index) {
        if (index < 0 || index >= lines.size()) return;
        HologramLine line = lines.remove(index);
        scheduler.runForEntity(anchor, line::despawn);
        recalculateOffsets();
    }

    @Override
    public void setRenderMode(RenderMode mode) {
        this.renderMode = mode;
        updateVisibility();
    }

    @Override
    public void addViewer(UUID uuid) {
        viewers.add(uuid);
        updateVisibility();
    }

    @Override
    public void removeViewer(UUID uuid) {
        viewers.remove(uuid);
        updateVisibility();
    }

    @Override
    public void updateVisibility() {
        scheduler.runForEntity(anchor, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean shouldSee = false;
                switch (renderMode) {
                    case ALL -> shouldSee = true;
                    case NONE -> shouldSee = false;
                    case VIEWER_LIST -> shouldSee = viewers.contains(player.getUniqueId());
                    case NOT_ATTACHED_PLAYER -> {
                        Entity vehicle = anchor.getVehicle();
                        shouldSee = !(vehicle instanceof Player attachedPlayer && attachedPlayer.getUniqueId().equals(player.getUniqueId()));
                    }
                }

                if (shouldSee) {
                    player.showEntity(plugin, anchor);
                    player.showEntity(plugin, interaction);
                    for (HologramLine line : lines) {
                        if (line.getEntity() != null) player.showEntity(plugin, line.getEntity());
                    }
                } else {
                    player.hideEntity(plugin, anchor);
                    player.hideEntity(plugin, interaction);
                    for (HologramLine line : lines) {
                        if (line.getEntity() != null) player.hideEntity(plugin, line.getEntity());
                    }
                }
            }
        });
    }

    private void recalculateOffsets() {
        scheduler.runForEntity(anchor, () -> {
            Location baseLoc = anchor.getLocation().clone();
            float currentY = 0;

            for (int i = lines.size() - 1; i >= 0; i--) {
                HologramLine line = lines.get(i);
                currentY += line.getHeight() / 2;

                Location lineLoc = baseLoc.clone().add(0, currentY, 0);
                if (line.getEntity() == null) {
                    line.spawn(lineLoc);
                    anchor.addPassenger(line.getEntity());
                } else {
                    line.getEntity().teleportAsync(lineLoc);
                }

                currentY += line.getHeight() / 2;
            }

            if (interaction != null && !interaction.isDead()) {
                interaction.setInteractionHeight(Math.max(0.5f, currentY));
                interaction.setInteractionWidth(2.0f);
            }
        });
    }

    @Override
    public void setClickAction(Consumer<Player> action) {
        this.clickAction = action;
    }

    @Override
    public void handleClick(Player player) {
        if (clickAction != null) {
            clickAction.accept(player);
        }
    }

    @Override
    public void attachTo(Entity entity) {
        scheduler.runForEntity(entity, () -> entity.addPassenger(anchor));
    }

    @Override
    public void remove() {
        scheduler.runForEntity(anchor, () -> {
            lines.forEach(HologramLine::despawn);
            lines.clear();
            if (interaction != null && !interaction.isDead()) interaction.remove();
            if (anchor != null && !anchor.isDead()) anchor.remove();
        });
    }

    @Override
    public ArmorStand getAnchor() { return anchor; }
}