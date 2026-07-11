package com.flarium.api.ui.menu.item;

import com.flarium.api.ui.menu.event.ItemClickEvent;
import org.bukkit.inventory.ItemStack;

public interface Item {
    ItemStack getItemProvider();
    void handleClick(ItemClickEvent event);
}