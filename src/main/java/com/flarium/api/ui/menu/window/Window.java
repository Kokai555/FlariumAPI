package com.flarium.api.ui.menu.window;

import com.flarium.api.ui.menu.gui.Gui;
import org.bukkit.entity.Player;

public interface Window {
    void open();
    void close();
    void updateContent();
    Gui getGui();
    Player getPlayer();
}