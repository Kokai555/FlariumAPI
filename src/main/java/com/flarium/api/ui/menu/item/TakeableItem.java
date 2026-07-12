package com.flarium.api.ui.menu.item;

import com.flarium.api.ui.menu.event.ItemClickEvent;
import org.bukkit.inventory.ItemStack;

public class TakeableItem extends AbstractItem {
    private ItemStack itemStack;

    public TakeableItem(ItemStack itemStack) {
        this.itemStack = itemStack;
    }

    @Override
    public ItemStack getItemProvider() {
        return itemStack;
    }

    @Override
    public void handleClick(ItemClickEvent event) {
        event.getBukkitEvent().setCancelled(false);
        event.getGui().setItem(event.getBukkitEvent().getRawSlot(), null);
    }
}