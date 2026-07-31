package com.flarium.api.ui.hologram.impl;

import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.data.pdc.PDCManager;
import com.flarium.api.data.pdc.UUIDDataType;
import com.flarium.api.nms.DisplayAdapter;
import com.flarium.api.ui.hologram.AbstractHologramLine;
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
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public class FlariumHologram implements Hologram {

    private final Plugin plugin;
    private final Scheduler scheduler;
    private final PDCManager pdcManager;
    private final UUID hologramId;
    private final ArmorStand anchor;
    private final Interaction interaction;
    private final List<HologramLine> lines = new CopyOnWriteArrayList<>();
    private Consumer<Player> clickAction;

    private RenderMode renderMode = RenderMode.ALL;
    private final Set<UUID> viewers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> shownTo = ConcurrentHashMap.newKeySet();
    private Location lastSyncedLocation;
    private volatile boolean attached;

    public FlariumHologram(Plugin plugin, Scheduler scheduler, PDCManager pdcManager, UUID hologramId, ArmorStand anchor, Interaction interaction) {
        this.plugin = plugin;
        this.scheduler = scheduler;
        this.pdcManager = pdcManager;
        this.hologramId = hologramId;
        this.anchor = anchor;
        this.interaction = interaction;

        scheduler.runForEntity(anchor, () -> anchor.addPassenger(interaction));
    }

    @Override
    public UUID getId() {
        return hologramId;
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

    void clearPlayer(UUID uuid) {
        viewers.remove(uuid);
        shownTo.remove(uuid);
    }

    @Override
    public void updateVisibility() {
        scheduler.runForEntity(anchor, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                boolean shouldSee = shouldSee(player);

                if (shouldSee && shownTo.add(player.getUniqueId())) {
                    player.showEntity(plugin, anchor);
                    player.showEntity(plugin, interaction);
                    for (HologramLine line : lines) {
                        if (!(line instanceof AbstractHologramLine) && line.getEntity() != null) {
                            player.showEntity(plugin, line.getEntity());
                        }
                        DisplayAdapter displayAdapter = displayAdapterOf(line);
                        if (displayAdapter != null) displayAdapter.sendSpawn(player, displayAdapter.getLocation());
                    }
                } else if (!shouldSee && shownTo.remove(player.getUniqueId())) {
                    player.hideEntity(plugin, anchor);
                    player.hideEntity(plugin, interaction);
                    for (HologramLine line : lines) {
                        if (!(line instanceof AbstractHologramLine) && line.getEntity() != null) {
                            player.hideEntity(plugin, line.getEntity());
                        }
                        DisplayAdapter displayAdapter = displayAdapterOf(line);
                        if (displayAdapter != null) displayAdapter.sendDestroy(player);
                    }
                }
            }
            shownTo.removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        });
    }

    private boolean shouldSee(Player player) {
        return switch (renderMode) {
            case ALL -> true;
            case NONE -> false;
            case VIEWER_LIST -> viewers.contains(player.getUniqueId());
            case NOT_ATTACHED_PLAYER -> {
                Entity vehicle = anchor.getVehicle();
                yield !(vehicle instanceof Player attachedPlayer && attachedPlayer.getUniqueId().equals(player.getUniqueId()));
            }
        };
    }

    private void recalculateOffsets() {
        scheduler.runForEntity(anchor, this::recalculateOffsetsNow);
    }

    private void recalculateOffsetsNow() {
        Location baseLoc = anchor.getLocation();
        lastSyncedLocation = baseLoc;
        float currentY = 0;
        Collection<Player> shown = shownPlayers();

        for (int i = lines.size() - 1; i >= 0; i--) {
            HologramLine line = lines.get(i);
            currentY += line.getHeight() / 2;

            Location lineLoc = baseLoc.clone().add(0, currentY, 0);
            if (line instanceof AbstractHologramLine abstractLine) {
                DisplayAdapter displayAdapter = abstractLine.getDisplayAdapter();
                if (displayAdapter == null) {
                    abstractLine.setViewerSupplier(this::shownPlayers);
                    line.spawn(lineLoc);
                    displayAdapter = abstractLine.getDisplayAdapter();
                    if (displayAdapter != null) {
                        displayAdapter.setPosition(lineLoc);
                        for (Player player : shown) {
                            displayAdapter.sendSpawn(player, lineLoc);
                        }
                    }
                } else {
                    displayAdapter.setPosition(lineLoc);
                    for (Player player : shown) {
                        displayAdapter.sendTeleport(player, lineLoc);
                    }
                }
            } else if (line.getEntity() == null) {
                line.spawn(lineLoc);
                pdcManager.set(line.getEntity(), "hologram_id", UUIDDataType.INSTANCE, hologramId);
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
    }

    void syncLinesToAnchor() {
        scheduler.runForEntity(anchor, () -> {
            attached = anchor.getVehicle() != null;
            Location current = anchor.getLocation();
            if (lastSyncedLocation != null
                    && lastSyncedLocation.getWorld() == current.getWorld()
                    && lastSyncedLocation.distanceSquared(current) < 1.0E-6) {
                return;
            }
            recalculateOffsetsNow();
        });
    }

    private Collection<Player> shownPlayers() {
        List<Player> players = new ArrayList<>(shownTo.size());
        for (UUID uuid : shownTo) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) players.add(player);
        }
        return players;
    }

    private @Nullable DisplayAdapter displayAdapterOf(HologramLine line) {
        return line instanceof AbstractHologramLine abstractLine ? abstractLine.getDisplayAdapter() : null;
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
        scheduler.runForEntity(entity, () -> {
            entity.addPassenger(anchor);
            attached = true;
        });
    }

    boolean isAttached() {
        return attached;
    }

    @Override
    public void remove() {
        scheduler.runForEntity(anchor, () -> {
            lines.forEach(HologramLine::despawn);
            lines.clear();
            shownTo.clear();
            if (interaction != null && !interaction.isDead()) interaction.remove();
            if (anchor != null && !anchor.isDead()) anchor.remove();
        });
    }

    @Override
    public ArmorStand getAnchor() { return anchor; }
}
