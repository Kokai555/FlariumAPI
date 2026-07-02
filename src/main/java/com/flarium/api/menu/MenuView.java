package com.flarium.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class MenuView implements InventoryHolder {

    protected final Player player;
    protected final Inventory inventory;
    protected final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected MenuView(Player player, MenuLayout layout) {
        this.player = player;
        this.inventory = org.bukkit.Bukkit.createInventory(this, layout.size(), layout.title());

        // Párosítjuk az itemet az actionnel a Layout alapján
        layout.items().forEach((slot, layoutItem) -> {
            inventory.setItem(slot, layoutItem.item());
            if (layoutItem.actionName() != null) {
                Consumer<InventoryClickEvent> action = resolveAction(layoutItem.actionName());
                if (action != null) {
                    actions.put(slot, action);
                }
            }
        });
    }

    // A pluginok implementálják ezt. A YAML string -> Java lambda fordítás.
    protected abstract Consumer<InventoryClickEvent> resolveAction(String actionName);

    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}