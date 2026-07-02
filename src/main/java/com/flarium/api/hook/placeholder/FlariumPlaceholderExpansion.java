package com.flarium.api.hook.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

public class FlariumPlaceholderExpansion extends PlaceholderExpansion {

    private final Plugin plugin;
    private final String rootIdentifier;
    private final ConcurrentHashMap<String, FlariumPlaceholder> placeholders = new ConcurrentHashMap<>();

    public FlariumPlaceholderExpansion(Plugin plugin, String rootIdentifier) {
        this.plugin = plugin;
        this.rootIdentifier = rootIdentifier;
    }

    public void registerPlaceholder(FlariumPlaceholder placeholder) {
        placeholders.put(placeholder.getIdentifier().toLowerCase(), placeholder);
    }

    @Override
    public @NotNull String getIdentifier() { return rootIdentifier; }

    @Override
    public @NotNull String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }

    @Override
    public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }

    @Override
    public boolean persist() { return true; }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        // Null player ellenőrzés eltávolítva, hogy a konzolos parancsok is működjenek

        FlariumPlaceholder exactMatch = placeholders.get(params.toLowerCase());
        if (exactMatch != null) {
            return exactMatch.process(player, "");
        }

        int splitIndex = params.indexOf('_');
        if (splitIndex != -1) {
            String subId = params.substring(0, splitIndex);
            String args = params.substring(splitIndex + 1);

            FlariumPlaceholder parametrized = placeholders.get(subId.toLowerCase());
            if (parametrized != null) {
                return parametrized.process(player, args);
            }
        }

        return null;
    }
}