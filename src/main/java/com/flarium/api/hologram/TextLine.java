package com.flarium.api.hologram;

import com.flarium.api.scheduler.Scheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.TextDisplay;

public class TextLine extends AbstractHologramLine {

    private Component text;
    private Color backgroundColor = Color.fromARGB(0, 0, 0, 0);
    private TextDisplay.TextAlignment alignment = TextDisplay.TextAlignment.CENTER;
    private int lineWidth = -1;
    private byte opacity = -1;

    public TextLine(Scheduler scheduler, Component text) {
        super(scheduler);
        this.text = text;
    }

    @Override
    public void spawn(Location location) {
        TextDisplay display = location.getWorld().spawn(location, TextDisplay.class, d -> {
            applyDisplayProperties(d);
            d.text(text);
            d.setBackgroundColor(backgroundColor);
            d.setAlignment(alignment);
            if (lineWidth != -1) d.setLineWidth(lineWidth);
            if (opacity != -1) d.setTextOpacity(opacity);
        });
        setEntity(display);
    }

    @Override
    public void despawn() {
        if (getEntity() != null && !getEntity().isDead()) getEntity().remove();
    }

    @Override
    public float getHeight() {
        return 0.3f;
    }

    public TextLine text(Component text) {
        this.text = text;
        if (getEntity() != null && getEntity().isValid()) {
            scheduler.runForEntity(getEntity(), () -> ((TextDisplay) getEntity()).text(text));
        }
        return this;
    }

    public TextLine backgroundColor(Color color) {
        this.backgroundColor = color;
        if (getEntity() != null && getEntity().isValid()) {
            scheduler.runForEntity(getEntity(), () -> ((TextDisplay) getEntity()).setBackgroundColor(color));
        }
        return this;
    }

    public TextLine alignment(TextDisplay.TextAlignment alignment) {
        this.alignment = alignment;
        if (getEntity() != null && getEntity().isValid()) {
            scheduler.runForEntity(getEntity(), () -> ((TextDisplay) getEntity()).setAlignment(alignment));
        }
        return this;
    }

    public TextLine lineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
        if (getEntity() != null && getEntity().isValid()) {
            scheduler.runForEntity(getEntity(), () -> ((TextDisplay) getEntity()).setLineWidth(lineWidth));
        }
        return this;
    }

    public TextLine opacity(byte opacity) {
        this.opacity = opacity;
        if (getEntity() != null && getEntity().isValid()) {
            scheduler.runForEntity(getEntity(), () -> ((TextDisplay) getEntity()).setTextOpacity(opacity));
        }
        return this;
    }
}