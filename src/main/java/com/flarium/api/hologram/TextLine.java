package com.flarium.api.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

public class TextLine implements HologramLine {

    private TextDisplay display;
    private final Component text;

    public TextLine(Component text) {
        this.text = text;
    }

    @Override
    public void spawn(Location location) {
        this.display = location.getWorld().spawn(location, TextDisplay.class, d -> {
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.text(text);
        });
    }

    @Override
    public void despawn() {
        if (display != null && !display.isDead()) display.remove();
    }

    @Override
    public float getHeight() {
        return 0.3f;
    }

    @Override
    public Entity getEntity() {
        return display;
    }

    public void setText(Component newText) {
        if (display != null) display.text(newText);
    }
}