package com.flarium.api.ui.hologram;

import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.nms.DisplayAdapter;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.Collections;
import java.util.function.Supplier;

public abstract class AbstractHologramLine implements HologramLine {

    protected final Scheduler scheduler;

    private DisplayAdapter displayAdapter;
    private Supplier<Collection<Player>> viewerSupplier = Collections::emptyList;

    private Vector3f translation = new Vector3f();
    private Vector3f scale = new Vector3f(1, 1, 1);
    private Display.Billboard billboard = Display.Billboard.CENTER;

    protected AbstractHologramLine(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public @Nullable Entity getEntity() {
        return displayAdapter == null ? null : displayAdapter.getBukkitEntity();
    }

    public @Nullable DisplayAdapter getDisplayAdapter() {
        return displayAdapter;
    }

    protected void setDisplayAdapter(DisplayAdapter displayAdapter) {
        this.displayAdapter = displayAdapter;
    }

    public void setViewerSupplier(Supplier<Collection<Player>> viewerSupplier) {
        this.viewerSupplier = viewerSupplier;
    }

    protected Collection<Player> getViewers() {
        return viewerSupplier.get();
    }

    protected void sendUpdate() {
        if (displayAdapter == null) return;
        for (Player player : getViewers()) {
            displayAdapter.sendUpdate(player);
        }
    }

    public AbstractHologramLine scale(float x, float y, float z) {
        this.scale = new Vector3f(x, y, z);
        if (displayAdapter != null) {
            displayAdapter.setTransformation(translation, scale);
            sendUpdate();
        }
        return this;
    }

    public AbstractHologramLine billboard(Display.Billboard billboard) {
        this.billboard = billboard;
        if (displayAdapter != null) {
            displayAdapter.setBillboard(billboard);
            sendUpdate();
        }
        return this;
    }

    protected void setTranslation(Vector3f translation) {
        this.translation = translation;
    }

    protected void applyDisplayProperties(DisplayAdapter displayAdapter) {
        displayAdapter.setBillboard(billboard);
        displayAdapter.setTransformation(translation, scale);
    }
}
