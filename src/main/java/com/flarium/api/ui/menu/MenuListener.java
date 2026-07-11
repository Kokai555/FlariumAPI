package com.flarium.api.ui.menu;

import com.flarium.api.ui.menu.window.AbstractWindow;
import com.flarium.api.ui.menu.window.AnvilWindow;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;

public class MenuListener implements Listener {

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AnvilWindow window) {
            ItemStack result = event.getResult();
            if (result != null && result.hasItemMeta()) {
                String text = PlainTextComponentSerializer.plainText().serialize(result.displayName());
                window.handleRename(text);
            } else {
                window.handleRename("");
            }
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof AbstractWindow window)) return;

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }

        window.handleClick(event);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof AbstractWindow) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof AbstractWindow window) {
            if (window.getPlayer().equals(event.getPlayer())) {
                window.close();
            }
        }
    }
}