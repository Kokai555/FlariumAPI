package com.flarium.api.ui.hologram;

import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.UUID;
import java.util.function.Consumer;

public interface Hologram {
    void addLine(HologramLine line);
    void removeLine(int index);
    void setRenderMode(RenderMode mode);
    void addViewer(UUID uuid);
    void removeViewer(UUID uuid);
    void updateVisibility();
    void setClickAction(Consumer<Player> action);
    void handleClick(Player player);
    void attachTo(Entity entity);
    void remove();
    ArmorStand getAnchor();
}