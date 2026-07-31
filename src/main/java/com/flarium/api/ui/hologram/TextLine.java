package com.flarium.api.ui.hologram;

import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.nms.DisplayAdapter;
import com.flarium.api.nms.DisplayAdapters;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Player;
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
        DisplayAdapter displayAdapter;
        try {
            displayAdapter = DisplayAdapters.createText(location.getWorld());
        } catch (UnsupportedOperationException e) {
            Bukkit.getLogger().warning("[FlariumAPI] Skipping hologram text line: " + e.getMessage());
            return;
        }
        applyDisplayProperties(displayAdapter);
        displayAdapter.setText(text);
        displayAdapter.setBackgroundColor(backgroundColor.asARGB());
        displayAdapter.setAlignment(DisplayAdapter.Alignment.valueOf(alignment.name()));
        if (lineWidth != -1) displayAdapter.setLineWidth(lineWidth);
        if (opacity != -1) displayAdapter.setTextOpacity(opacity);
        setDisplayAdapter(displayAdapter);
    }

    @Override
    public void despawn() {
        DisplayAdapter displayAdapter = getDisplayAdapter();
        if (displayAdapter == null) return;
        for (Player player : getViewers()) {
            displayAdapter.sendDestroy(player);
        }
        setDisplayAdapter(null);
    }

    @Override
    public float getHeight() {
        return 0.3f;
    }

    public TextLine text(Component text) {
        this.text = text;
        if (getDisplayAdapter() != null) {
            getDisplayAdapter().setText(text);
            sendUpdate();
        }
        return this;
    }

    public TextLine backgroundColor(Color color) {
        this.backgroundColor = color;
        if (getDisplayAdapter() != null) {
            getDisplayAdapter().setBackgroundColor(color.asARGB());
            sendUpdate();
        }
        return this;
    }

    public TextLine alignment(TextDisplay.TextAlignment alignment) {
        this.alignment = alignment;
        if (getDisplayAdapter() != null) {
            getDisplayAdapter().setAlignment(DisplayAdapter.Alignment.valueOf(alignment.name()));
            sendUpdate();
        }
        return this;
    }

    public TextLine lineWidth(int lineWidth) {
        this.lineWidth = lineWidth;
        if (getDisplayAdapter() != null) {
            getDisplayAdapter().setLineWidth(lineWidth);
            sendUpdate();
        }
        return this;
    }

    public TextLine opacity(byte opacity) {
        this.opacity = opacity;
        if (getDisplayAdapter() != null) {
            getDisplayAdapter().setTextOpacity(opacity);
            sendUpdate();
        }
        return this;
    }
}
