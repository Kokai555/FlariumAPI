package com.flarium.api.ui.menu.window;

import com.flarium.api.ui.menu.gui.Gui;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;

public class AnvilWindow extends AbstractWindow {

    private Consumer<String> renameHandler;
    private Consumer<String> confirmHandler;

    public AnvilWindow(Player player, Gui gui, String title) {
        super(player, gui, InventoryType.ANVIL, title);
    }

    public void setRenameHandler(Consumer<String> renameHandler) {
        this.renameHandler = renameHandler;
    }

    public void setConfirmHandler(Consumer<String> confirmHandler) {
        this.confirmHandler = confirmHandler;
    }

    public void handleRename(String text) {
        if (renameHandler != null) {
            renameHandler.accept(text);
        }
    }

    @Override
    public void handleClick(InventoryClickEvent event) {
        super.handleClick(event);

        if (event.getRawSlot() == 2 && confirmHandler != null) {
            ItemStack result = event.getCurrentItem();
            if (result != null && result.hasItemMeta()) {
                String input = PlainTextComponentSerializer.plainText().serialize(result.displayName());
                confirmHandler.accept(input);
            }
        }
    }
}