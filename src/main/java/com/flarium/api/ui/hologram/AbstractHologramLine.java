package com.flarium.api.ui.hologram;

import com.flarium.api.core.scheduler.Scheduler;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.util.Transformation;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

public abstract class AbstractHologramLine implements HologramLine {

    protected final Scheduler scheduler;

    private Entity entity;

    private Vector3f scale = new Vector3f(1, 1, 1);
    private Display.Billboard billboard = Display.Billboard.CENTER;

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

    protected void applyDisplayProperties(Display display) {
        display.setBillboard(billboard);
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