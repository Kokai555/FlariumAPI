package com.flarium.api.ui.menu.window;

import com.flarium.api.FlariumAPI;
import com.flarium.api.core.scheduler.Task;
import com.flarium.api.ui.menu.event.ItemClickEvent;
import com.flarium.api.ui.menu.gui.Gui;
import com.flarium.api.ui.menu.item.Item;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;

public abstract class AbstractWindow implements Window, InventoryHolder {

    protected final Player player;
    protected final Gui gui;
    protected Inventory inventory;
    private Task tickTask;

    public AbstractWindow(Player player, Gui gui, int size, String title) {
        this.player = player;
        this.gui = gui;
        this.inventory = Bukkit.createInventory(this, size, MiniMessage.miniMessage().deserialize(title));
    }

    public AbstractWindow(Player player, Gui gui, InventoryType type, String title) {
        this.player = player;
        this.gui = gui;
        this.inventory = Bukkit.createInventory(this, type, MiniMessage.miniMessage().deserialize(title));
    }

    @Override
    public void open() {
        updateContent();
        player.openInventory(inventory);
    }

    @Override
    public void close() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        player.closeInventory();
    }

    @Override
    public void updateContent() {
        if (gui == null || inventory.getType() == InventoryType.ANVIL) return;

        ItemStack[] content = gui.getContent();
        for (int i = 0; i < content.length; i++) {
            ItemStack current = inventory.getItem(i);
            ItemStack newItem = content[i];

            if (current == null && newItem == null) continue;
            if (current != null && current.equals(newItem)) continue;

            inventory.setItem(i, newItem);
        }
    }

    public void startTicking(Duration period) {
        if (tickTask != null) tickTask.cancel();
        tickTask = FlariumAPI.getInstance().getScheduler().runForEntityTimer(player, () -> {
            if (gui != null) gui.tick();
            updateContent();
        }, Duration.ZERO, period);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    @Override
    public Gui getGui() { return gui; }

    @Override
    public Player getPlayer() { return player; }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;

        int slot = event.getRawSlot();
        Item item = gui.getItem(slot);

        if (item != null) {
            item.handleClick(new ItemClickEvent(player, event, gui, this));
        }
    }
}