package com.flarium.api.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public abstract class AbstractProfileManager<V> implements Listener {

    private final JavaPlugin plugin;
    private final Cache<UUID, V> cache;
    private final ConcurrentLinkedQueue<CompletableFuture<Void>> pendingSaves = new ConcurrentLinkedQueue<>();

    protected AbstractProfileManager(JavaPlugin plugin, long expireAfterAccessSeconds, long maxSize) {
        this.plugin = plugin;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccessSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .removalListener(this::handleRemoval)
                .build();
    }

    public V getProfile(UUID uuid) {
        return cache.getIfPresent(uuid);
    }

    public CompletableFuture<Void> loadProfile(UUID uuid) {
        return loadFromDatabase(uuid).thenAccept(profile -> {
            if (profile != null) {
                cache.put(uuid, profile);
            }
        });
    }

    public CompletableFuture<Void> saveAndInvalidate(UUID uuid) {
        V profile = cache.getIfPresent(uuid);
        if (profile != null) {
            cache.invalidate(uuid);
            CompletableFuture<Void> saveFuture = saveToDatabase(profile);
            pendingSaves.add(saveFuture);
            saveFuture.whenComplete((v, ex) -> pendingSaves.remove(saveFuture));
            return saveFuture;
        }
        return CompletableFuture.completedFuture(null);
    }

    private void handleRemoval(UUID uuid, V profile, RemovalCause cause) {
        if (cause.wasEvicted()) {
            CompletableFuture<Void> saveFuture = saveToDatabase(profile);
            pendingSaves.add(saveFuture);
            saveFuture.whenComplete((v, ex) -> pendingSaves.remove(saveFuture));
        }
    }

    public void shutdown() {
        java.util.Set<UUID> keys = new java.util.HashSet<>(cache.asMap().keySet());
        for (UUID uuid : keys) {
            saveAndInvalidate(uuid);
        }
        CompletableFuture.allOf(pendingSaves.toArray(new CompletableFuture[0])).join();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            loadProfile(event.getUniqueId()).join();
        } catch (CompletionException e) {
            plugin.getLogger().severe("Failed to load profile for " + event.getUniqueId() + ": " + e.getCause().getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Failed to load profile!");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        saveAndInvalidate(player.getUniqueId());
    }

    protected abstract CompletableFuture<V> loadFromDatabase(UUID uuid);
    protected abstract CompletableFuture<Void> saveToDatabase(V profile);
}