package com.flarium.api.ui.menu.item;

import org.bukkit.inventory.ItemStack;

public class SimpleItem extends AbstractItem {
    private ItemStack itemStack;

    public SimpleItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public ItemStack getItemProvider() {
        return itemStack;
    }

    public void setItemStack(ItemStack itemStack) {
        this.itemStack = itemStack;
    }
}