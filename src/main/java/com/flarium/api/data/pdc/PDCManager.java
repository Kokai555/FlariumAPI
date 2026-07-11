package com.flarium.api.data.pdc;

import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class PDCManager {

    private final Plugin plugin;

    public PDCManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public <P, C> void set(PersistentDataHolder holder, String key, PersistentDataType<P, C> type, C value) {
        if (holder == null) return;
        holder.getPersistentDataContainer().set(KeyRegistry.getKey(plugin, key), type, value);
    }

    public <P, C> C get(PersistentDataHolder holder, String key, PersistentDataType<P, C> type) {
        if (holder == null) return null;
        return holder.getPersistentDataContainer().get(KeyRegistry.getKey(plugin, key), type);
    }

    public <P, C> boolean has(PersistentDataHolder holder, String key, PersistentDataType<P, C> type) {
        if (holder == null) return false;
        return holder.getPersistentDataContainer().has(KeyRegistry.getKey(plugin, key), type);
    }

    public void remove(PersistentDataHolder holder, String key) {
        if (holder == null) return;
        holder.getPersistentDataContainer().remove(KeyRegistry.getKey(plugin, key));
    }

    public <P, C> void set(ItemStack item, String key, PersistentDataType<P, C> type, C value) {
        if (item == null || item.getType().isAir()) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        set(meta, key, type, value);
        item.setItemMeta(meta);
    }

    public <P, C> C get(ItemStack item, String key, PersistentDataType<P, C> type) {
        if (item == null || !item.hasItemMeta()) return null;
        return get(item.getItemMeta(), key, type);
    }

    public <P, C> boolean has(ItemStack item, String key, PersistentDataType<P, C> type) {
        if (item == null || !item.hasItemMeta()) return false;
        return has(item.getItemMeta(), key, type);
    }

    public void remove(ItemStack item, String key) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        remove(meta, key);
        item.setItemMeta(meta);
    }

    public <P, C> void set(Block block, String key, PersistentDataType<P, C> type, C value) {
        if (block == null || !(block.getState() instanceof TileState state)) return;
        set(state, key, type, value);
        state.update();
    }

    public <P, C> C get(Block block, String key, PersistentDataType<P, C> type) {
        if (block == null || !(block.getState() instanceof TileState state)) return null;
        return get(state, key, type);
    }

    public <P, C> boolean has(Block block, String key, PersistentDataType<P, C> type) {
        if (block == null || !(block.getState() instanceof TileState state)) return false;
        return has(state, key, type);
    }

    public void remove(Block block, String key) {
        if (block == null || !(block.getState() instanceof TileState state)) return;
        remove(state, key);
        state.update();
    }
}