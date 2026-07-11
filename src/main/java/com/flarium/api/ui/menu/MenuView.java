package com.flarium.api.ui.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public abstract class MenuView implements InventoryHolder {

    protected final MenuManager menuManager;
    protected final Player player;
    protected final Inventory inventory;
    protected final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    protected MenuView(MenuManager menuManager, Player player, MenuLayout layout) {
        this.menuManager = menuManager;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, layout.size(), layout.title());

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

    protected abstract Consumer<InventoryClickEvent> resolveAction(String actionName);

    public void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> action = actions.get(event.getRawSlot());
        if (action != null) {
            action.accept(event);
        }
    }

    public void open() {
        player.openInventory(inventory);
        menuManager.registerActiveMenu(this);
    }

    public void close() {
        menuManager.unregisterActiveMenu(this);
    }

    public void tick() {
        // Override in subclasses for dynamic updates
    }

    public Player getPlayer() {
        return player;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}