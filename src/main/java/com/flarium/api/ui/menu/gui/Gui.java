package com.flarium.api.ui.menu.gui;

import com.flarium.api.ui.menu.item.Item;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public interface Gui {
    void setItem(int slot, Item item);
    Item getItem(int slot);
    ItemStack[] getContent();
    int getSize();
    void addModifier(GuiModifier modifier);
    List<GuiModifier> getModifiers();
    void tick();

    @FunctionalInterface
    interface GuiModifier {
        void modify(Gui gui);
    }
}