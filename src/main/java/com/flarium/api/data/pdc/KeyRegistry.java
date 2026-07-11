package com.flarium.api.data.pdc;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class KeyRegistry {
    private static final Map<String, NamespacedKey> KEY_CACHE = new ConcurrentHashMap<>();

    public static NamespacedKey getKey(Plugin plugin, String name) {
        String mapKey = plugin.getName().toLowerCase() + ":" + name.toLowerCase();
        return KEY_CACHE.computeIfAbsent(mapKey, k -> new NamespacedKey(plugin, name.toLowerCase()));
    }
}