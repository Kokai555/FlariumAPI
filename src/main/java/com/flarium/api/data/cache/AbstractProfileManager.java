package com.flarium.api.data.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;

public abstract class AbstractProfileManager<V> implements Listener {

    private final JavaPlugin plugin;
    private final Cache<UUID, V> cache;
    private final ConcurrentHashMap<UUID, V> activeProfiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<V>> pendingLoads = new ConcurrentHashMap<>();
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
        V profile = activeProfiles.get(uuid);
        if (profile != null) {
            return profile;
        }
        return cache.getIfPresent(uuid);
    }

    public V getProfileOrThrow(UUID uuid) {
        V profile = getProfile(uuid);
        if (profile == null) {
            throw new IllegalStateException("Profile not loaded in memory for UUID: " + uuid);
        }
        return profile;
    }

    public CompletableFuture<Void> loadProfile(UUID uuid) {
        CompletableFuture<V> loadFuture = pendingLoads.computeIfAbsent(uuid, key -> {
            return loadFromDatabase(key)
                    .whenComplete((profile, throwable) -> pendingLoads.remove(key))
                    .thenApply(profile -> {
                        if (profile != null) {
                            cache.put(key, profile);
                            if (Bukkit.getPlayer(key) != null) {
                                activeProfiles.put(key, profile);
                            }
                        }
                        return profile;
                    });
        });
        return loadFuture.thenApply(profile -> null);
    }

    public CompletableFuture<Void> saveAndInvalidate(UUID uuid) {
        V profile = activeProfiles.remove(uuid);
        if (profile == null) {
            profile = cache.getIfPresent(uuid);
        }

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
        Set<UUID> keys = new HashSet<>(cache.asMap().keySet());
        keys.addAll(activeProfiles.keySet());
        for (UUID uuid : keys) {
            saveAndInvalidate(uuid);
        }
        try {
            CompletableFuture.allOf(pendingSaves.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            plugin.getLogger().severe("Profile save timeout on shutdown: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            loadProfile(event.getUniqueId()).join();
            V profile = getProfile(event.getUniqueId());
            if (profile != null) {
                activeProfiles.put(event.getUniqueId(), profile);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load profile for " + event.getUniqueId() + ": " + e.getMessage());
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