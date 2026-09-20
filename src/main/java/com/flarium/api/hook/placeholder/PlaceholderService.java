package com.flarium.api.hook.placeholder;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;

import java.util.function.Function;

public class PlaceholderService {

    private final boolean enabled;
    private InternalPAPIHook hookHandler;

    public PlaceholderService(Plugin plugin, String rootIdentifier) {
        this.enabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

        if (enabled) {
            this.hookHandler = new PAPIHookHandler(plugin, rootIdentifier);
            plugin.getLogger().info("PlaceholderAPI hook enabled.");
        } else {
            plugin.getLogger().warning("PlaceholderAPI not found, placeholders will not be parsed.");
        }
    }

    public void registerPlaceholder(String identifier, Function<OfflinePlayer, String> function) {
        if (!enabled) return;
        hookHandler.registerPlaceholder(identifier, function);
    }

    public void registerPlaceholder(FlariumPlaceholder placeholder) {
        if (!enabled) return;
        hookHandler.registerPlaceholder(placeholder);
    }

    public String parse(OfflinePlayer player, String text) {
        if (!enabled || text == null) return text;
        return hookHandler.parse(player, text);
    }

    /**
     * C117: explicit owner-disable lifecycle. Unregisters the expansion from
     * PlaceholderAPI (if registered) and releases cached placeholder handlers so
     * lambdas capturing the owning plugin can be GC'd. Idempotent and safe when
     * PlaceholderAPI is absent/disabled. Call from the owning plugin's onDisable.
     */
    public void close() {
        if (!enabled || hookHandler == null) return;
        try {
            hookHandler.close();
        } catch (Throwable ignored) {
            // PAPI already gone -> local state is still cleared by the handler.
        }
    }

    /**
     * C117 alias of {@link #close()} for callers expecting an unregister lifecycle.
     */
    public void unregister() {
        close();
    }

    private interface InternalPAPIHook {
        void registerPlaceholder(String identifier, Function<OfflinePlayer, String> function);
        void registerPlaceholder(FlariumPlaceholder placeholder);
        String parse(OfflinePlayer player, String text);
        void close();
    }

    private static class PAPIHookHandler implements InternalPAPIHook {
        private final FlariumPlaceholderExpansion expansion;

        public PAPIHookHandler(Plugin plugin, String rootIdentifier) {
            this.expansion = new FlariumPlaceholderExpansion(plugin, rootIdentifier);
            this.expansion.register();
        }

        @Override
        public void registerPlaceholder(String identifier, Function<OfflinePlayer, String> function) {
            expansion.registerPlaceholder(new FlariumPlaceholder() {
                @Override
                public String getIdentifier() { return identifier; }

                @Override
                public String process(OfflinePlayer player, String args) {
                    return function.apply(player);
                }
            });
        }

        @Override
        public void registerPlaceholder(FlariumPlaceholder placeholder) {
            expansion.registerPlaceholder(placeholder);
        }

        @Override
        public String parse(OfflinePlayer player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }

        @Override
        public void close() {
            try {
                expansion.close();
            } catch (Throwable ignored) {
                // PAPI already gone -> expansion.close() already cleared local state.
            }
        }
    }
}