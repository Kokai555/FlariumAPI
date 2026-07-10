package com.flarium.api.hologram;

import com.flarium.api.scheduler.Scheduler;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public abstract class AbstractHologramLine implements HologramLine {

    protected final Scheduler scheduler;

    private Entity entity;

    private Vector3f scale = new Vector3f(1, 1, 1);
    private Display.Billboard billboard = Display.Billboard.CENTER;
    private boolean seeThrough = false;

    protected AbstractHologramLine(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public @Nullable Entity getEntity() {
        return entity;
    }

    protected void setEntity(Entity entity) {
        this.entity = entity;
    }

    public AbstractHologramLine scale(float x, float y, float z) {
        this.scale = new Vector3f(x, y, z);
        if (entity != null && entity.isValid()) {
            scheduler.runForEntity(entity, () -> updateTransformation((Display) entity));
        }
        return this;
    }

    public AbstractHologramLine billboard(Display.Billboard billboard) {
        this.billboard = billboard;
        if (entity != null && entity.isValid()) {
            scheduler.runForEntity(entity, () -> ((Display) entity).setBillboard(billboard));
        }
        return this;
    }

    public AbstractHologramLine seeThrough(boolean seeThrough) {
        this.seeThrough = seeThrough;
        if (entity != null && entity.isValid()) {
            scheduler.runForEntity(entity, () -> ((Display) entity).setSeeThrough(seeThrough));
        }
        return this;
    }

    protected void applyDisplayProperties(Display display) {
        display.setBillboard(billboard);
        display.setSeeThrough(seeThrough);
        updateTransformation(display);
    }

    private void updateTransformation(Display display) {
        Transformation transformation = display.getTransformation();
        display.setTransformation(new Transformation(
                transformation.getTranslation(),
                transformation.getLeftRotation(),
                scale,
                transformation.getRightRotation()
        ));
    }
}