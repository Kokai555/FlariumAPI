package com.flarium.api.ui.menu.item;

import org.bukkit.inventory.ItemStack;

public class SimpleItem extends AbstractItem {
    private final ItemStack itemStack;

    public SimpleItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public ItemStack getItemProvider() {
        return itemStack;
    }
}