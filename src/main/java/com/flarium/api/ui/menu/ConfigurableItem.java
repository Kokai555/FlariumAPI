package com.flarium.api.ui.menu;

import com.flarium.api.ui.menu.event.ItemClickEvent;
import com.flarium.api.ui.menu.item.AbstractItem;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class ConfigurableItem extends AbstractItem {
    private final ItemStack itemStack;
    private final Consumer<ItemClickEvent> action;

    public ConfigurableItem(ItemStack itemStack, Consumer<ItemClickEvent> action) {
        this.itemStack = itemStack;
        this.action = action;
    }

    @Override
    public ItemStack getItemProvider() {
        return itemStack;
    }

    @Override
    public void handleClick(ItemClickEvent event) {
        if (action != null) {
            action.accept(event);
        }
    }
}