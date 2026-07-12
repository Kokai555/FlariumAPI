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
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractWindow implements Window, InventoryHolder {

    public static final Set<AbstractWindow> ACTIVE_WINDOWS = ConcurrentHashMap.newKeySet();

    protected final Player player;
    protected final Gui gui;
    protected Inventory inventory;
    private Task tickTask;

    private boolean handlesBottomInventory = false;
    private ItemStack[] playerInventoryBackup = null;

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
        this.handlesBottomInventory = gui.getSize() > inventory.getSize();

        if (handlesBottomInventory) {
            this.playerInventoryBackup = player.getInventory().getContents().clone();
        }

        player.openInventory(inventory);
        updateContent();
        ACTIVE_WINDOWS.add(this);
    }

    @Override
    public void close() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }

        if (handlesBottomInventory && playerInventoryBackup != null) {
            player.getInventory().setContents(playerInventoryBackup);
            player.updateInventory();
        }

        player.closeInventory();
        ACTIVE_WINDOWS.remove(this);
    }

    @Override
    public void updateContent() {
        InventoryView view = player.getOpenInventory();
        if (view.getTopInventory().getHolder() != this) return;

        ItemStack[] content = gui.getContent();
        for (int i = 0; i < content.length; i++) {
            if (i >= view.countSlots()) break;

            ItemStack current = view.getItem(i);
            ItemStack newItem = content[i];

            if (current == null && newItem == null) continue;
            if (current != null && current.equals(newItem)) continue;

            view.setItem(i, newItem);
        }
    }

    public void startTicking(Duration period) {
        if (tickTask != null) tickTask.cancel();
        tickTask = FlariumAPI.getInstance().getScheduler().runForEntityTimer(player, () -> {
            gui.tick();
            updateContent();
        }, Duration.ZERO, period);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
    @Override
    public Gui getGui() { return gui; }
    @Override
    public Player getPlayer() { return player; }

    public void handleClick(InventoryClickEvent event) {
        int rawSlot = event.getRawSlot();

        if (rawSlot >= 0 && rawSlot < gui.getSize()) {
            event.setCancelled(true);
            Item item = gui.getItem(rawSlot);
            if (item != null) {
                item.handleClick(new ItemClickEvent(player, event, gui, this));
            }
        } else {
            if (event.isShiftClick()) event.setCancelled(true);
        }
    }
}