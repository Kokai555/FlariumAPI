package com.flarium.api.ui.menu.item;

import com.flarium.api.ui.menu.event.ItemClickEvent;
import com.flarium.api.ui.menu.gui.PaginatedGui;
import org.bukkit.inventory.ItemStack;

public class PageItem extends AbstractItem {

    private final ItemStack itemStack;
    private final PaginatedGui paginatedGui;
    private final boolean forward;

    public PageItem(ItemStack itemStack, PaginatedGui paginatedGui, boolean forward) {
        this.itemStack = itemStack;
        this.paginatedGui = paginatedGui;
        this.forward = forward;
    }

    @Override
    public ItemStack getItemProvider() {
        return itemStack;
    }

    @Override
    public void handleClick(ItemClickEvent event) {
        if (forward) {
            paginatedGui.nextPage();
        } else {
            paginatedGui.previousPage();
        }
        event.getWindow().updateContent();
    }
}