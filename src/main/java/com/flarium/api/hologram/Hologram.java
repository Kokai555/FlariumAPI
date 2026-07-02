package com.flarium.api.hologram;

import com.flarium.api.scheduler.Scheduler;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Hologram {

    private final Scheduler scheduler;
    private final ArmorStand anchor;
    private final List<HologramLine> lines = new ArrayList<>();
    private Consumer<org.bukkit.entity.Player> clickAction;

    public Hologram(Scheduler scheduler, ArmorStand anchor) {
        this.scheduler = scheduler;
        this.anchor = anchor;
    }

    public void addLine(HologramLine line) {
        lines.add(line);
        recalculateOffsets();
    }

    public void removeLine(int index) {
        if (index < 0 || index >= lines.size()) return;
        HologramLine line = lines.remove(index);

        scheduler.runForEntity(anchor, line::despawn);
        recalculateOffsets();
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
        });
    }

    public void setClickAction(Consumer<org.bukkit.entity.Player> action) {
        this.clickAction = action;
    }

    public void handleClick(org.bukkit.entity.Player player) {
        if (clickAction != null) {
            clickAction.accept(player);
        }
    }

    public void attachTo(Entity entity) {
        scheduler.runForEntity(entity, () -> entity.addPassenger(anchor));
    }

    public void remove() {
        scheduler.runForEntity(anchor, () -> {
            lines.forEach(HologramLine::despawn);
            lines.clear();
            if (anchor != null && !anchor.isDead()) anchor.remove();
        });
    }

    public ArmorStand getAnchor() { return anchor; }
}