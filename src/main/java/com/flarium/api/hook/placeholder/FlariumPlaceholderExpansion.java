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
    public @Nullable String getRequiredPlugin() {
        return plugin != null ? plugin.getName() : null;
    }

    @Override
    public boolean persist() { return true; }

    /**
     * C117: explicit owner-disable lifecycle.
     *
     * <p>{@code persist() == true} is intentional: internal dependency-registered
     * expansions must survive PlaceholderAPI reloads ({@code /papi reload} calls
     * {@code LocalExpansionManager#unregisterAll}, which skips persistent expansions).
     * Owner-disable cleanup is therefore explicit: call {@link #close()} from the owning
     * plugin's {@code onDisable} (via {@code PlaceholderService#close()}). The
     * {@link #getRequiredPlugin()} override above is only a safety net so PAPI's own
     * {@code PluginDisableEvent} listener can auto-unregister if the owner forgets.</p>
     */
    public void close() {
        try {
            unregister();
        } catch (Throwable ignored) {
            // PAPI absent/disabled or already unregistered -> still clear local state below.
        }
        placeholders.clear();
    }

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