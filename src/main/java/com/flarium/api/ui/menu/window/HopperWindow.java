package com.flarium.api.ui.menu.window;

import com.flarium.api.ui.menu.gui.Gui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

public class HopperWindow extends AbstractWindow {

    public HopperWindow(Player player, Gui gui, String title) {
        super(player, gui, InventoryType.HOPPER, title);
    }
}