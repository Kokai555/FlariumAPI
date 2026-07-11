package com.flarium.api.ui.menu.event;

import com.flarium.api.ui.menu.gui.Gui;
import com.flarium.api.ui.menu.window.Window;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

public class ItemClickEvent {
    private final Player player;
    private final InventoryClickEvent bukkitEvent;
    private final Gui gui;
    private final Window window;

    public ItemClickEvent(Player player, InventoryClickEvent bukkitEvent, Gui gui, Window window) {
        this.player = player;
        this.bukkitEvent = bukkitEvent;
        this.gui = gui;
        this.window = window;
    }

    public Player getPlayer() { return player; }
    public InventoryClickEvent getBukkitEvent() { return bukkitEvent; }
    public Gui getGui() { return gui; }
    public Window getWindow() { return window; }
}