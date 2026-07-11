package com.flarium.api.ui.menu.window;

import com.flarium.api.ui.menu.gui.Gui;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class AnvilWindow extends AbstractWindow {

    private final Consumer<String> callback;

    public AnvilWindow(Player player, Gui gui, String title, ItemStack inputItem, Consumer<String> callback) {
        super(player, gui, InventoryType.ANVIL, title);
        this.callback = callback;

        if (inputItem != null) {
            this.inventory.setItem(0, inputItem);
        }
    }

    @Override
    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public void close() {
        player.closeInventory();
    }

    @Override
    public void updateContent() {
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);

        if (event.getRawSlot() == 2) {
            ItemStack result = event.getCurrentItem();
            if (result != null && result.hasItemMeta()) {
                String input = PlainTextComponentSerializer.plainText().serialize(result.displayName());
                callback.accept(input);
            }
            close();
        }
    }
}