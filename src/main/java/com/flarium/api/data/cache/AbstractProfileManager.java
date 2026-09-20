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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class AbstractProfileManager<V> implements Listener {

    private final JavaPlugin plugin;
    private final Cache<UUID, V> cache;
    private final ConcurrentHashMap<UUID, V> activeProfiles = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<V>> pendingLoads = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<CompletableFuture<Void>> pendingSaves = new ConcurrentLinkedQueue<>();
    /** C28: set once shutdown starts; new loads are rejected and late completions no longer repopulate. */
    private volatile boolean shuttingDown;

    protected AbstractProfileManager(JavaPlugin plugin, long expireAfterAccessSeconds, long maxSize) {
        this.plugin = plugin;
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(expireAfterAccessSeconds, TimeUnit.SECONDS)
                .maximumSize(maxSize)
                .removalListener(this::handleRemoval)
                .build();
    }

    /**
     * C28: bound for the pre-login profile wait. Login must never block forever on a
     * hung database future. Override in tests for shorter bounds.
     */
    protected long loginTimeoutSeconds() {
        return 10;
    }

    /**
     * C28: total bound for the shutdown drain of pending saves and loads.
     * Override in tests for shorter bounds.
     */
    protected long shutdownTimeoutSeconds() {
        return 5;
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
        if (shuttingDown) {
            CompletableFuture<Void> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(new IllegalStateException("Profile manager is shut down: " + uuid));
            return rejected;
        }
        // C28: the map holds the terminal stage and removal runs after the cache
        // put, so a load arriving while one is in flight always coalesces onto it
        // instead of starting a duplicate database hit in a remove-then-put window.
        // The map is never touched inside the mapping function: when the database
        // future is already complete, a removal staged there would run inline and
        // trip ConcurrentHashMap's "Recursive update" guard.
        java.util.concurrent.atomic.AtomicReference<CompletableFuture<V>> created =
                new java.util.concurrent.atomic.AtomicReference<>();
        CompletableFuture<V> loadFuture = pendingLoads.computeIfAbsent(uuid, key -> {
            CompletableFuture<V> started;
            try {
                started = loadFromDatabase(key);
            } catch (Throwable t) {
                started = new CompletableFuture<>();
                started.completeExceptionally(t);
            }
            if (started == null) {
                started = new CompletableFuture<>();
                started.completeExceptionally(new IllegalStateException("loadFromDatabase returned null for " + key));
            }
            CompletableFuture<V> terminal = started.thenApply(profile -> {
                if (profile != null && !shuttingDown) {
                    cache.put(key, profile);
                    if (Bukkit.getPlayer(key) != null) {
                        activeProfiles.put(key, profile);
                    }
                }
                return profile;
            });
            created.set(terminal);
            return terminal;
        });
        if (created.get() != null) {
            loadFuture.whenComplete((profile, throwable) -> pendingLoads.remove(uuid, loadFuture));
        }
        return loadFuture.thenApply(profile -> null);
    }

    public CompletableFuture<Void> saveAndInvalidate(UUID uuid) {
        V profile = activeProfiles.remove(uuid);
        if (profile == null) {
            profile = cache.getIfPresent(uuid);
        }

        if (profile != null) {
            cache.invalidate(uuid);
            return trackSave(saveToDatabaseSafely(profile));
        }
        return CompletableFuture.completedFuture(null);
    }

    /**
     * C28: tracks a save future so shutdown can await it. Returns the tracked future.
     */
    private CompletableFuture<Void> trackSave(CompletableFuture<Void> saveFuture) {
        if (saveFuture == null) {
            return CompletableFuture.completedFuture(null);
        }
        pendingSaves.add(saveFuture);
        saveFuture.whenComplete((v, ex) -> pendingSaves.remove(saveFuture));
        return saveFuture;
    }

    /**
     * C28: the removal listener runs on Caffeine maintenance/caller threads, so a
     * synchronously-throwing implementation must not propagate into cache internals.
     */
    private CompletableFuture<Void> saveToDatabaseSafely(V profile) {
        try {
            CompletableFuture<Void> saveFuture = saveToDatabase(profile);
            return saveFuture != null ? saveFuture : CompletableFuture.completedFuture(null);
        } catch (Throwable t) {
            plugin.getLogger().severe("Profile save failed: " + t.getMessage());
            CompletableFuture<Void> failed = new CompletableFuture<>();
            failed.completeExceptionally(t);
            return failed;
        }
    }

    private void handleRemoval(UUID uuid, V profile, RemovalCause cause) {
        if (!cause.wasEvicted()) {
            return;
        }
        // C28: while an online copy exists, the quit/shutdown path owns persistence.
        // Saving the evicted snapshot here would race live mutations on the eviction thread.
        if (activeProfiles.containsKey(uuid)) {
            return;
        }
        trackSave(saveToDatabaseSafely(profile));
    }

    public void shutdown() {
        shuttingDown = true;
        Set<UUID> keys = new HashSet<>(cache.asMap().keySet());
        keys.addAll(activeProfiles.keySet());
        for (UUID uuid : keys) {
            saveAndInvalidate(uuid);
        }
        // C28: bounded drain of everything still in flight, including saves enqueued
        // by the removal listener during the drain and in-flight loads (whose late
        // completion no longer repopulates because shuttingDown is set). Never waits
        // longer than shutdownTimeoutSeconds() in total.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(shutdownTimeoutSeconds());
        try {
            while (true) {
                List<CompletableFuture<?>> pending = new ArrayList<>(pendingSaves);
                pending.addAll(pendingLoads.values());
                if (pending.isEmpty()) {
                    return;
                }
                long remaining = deadline - System.nanoTime();
                if (remaining <= 0) {
                    throw new TimeoutException("Profile save drain timed out with "
                            + pending.size() + " pending task(s)");
                }
                try {
                    CompletableFuture.allOf(pending.toArray(new CompletableFuture[0]))
                            .get(remaining, TimeUnit.NANOSECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    // Individual save/load failures are owned (and logged) by their
                    // initiators; keep draining the rest within the deadline.
                    plugin.getLogger().severe("Profile task failed on shutdown: " + e.getCause());
                }
            }
        } catch (TimeoutException e) {
            plugin.getLogger().severe("Profile save timeout on shutdown: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            plugin.getLogger().severe("Profile shutdown interrupted: " + e.getMessage());
        } catch (Exception e) {
            plugin.getLogger().severe("Profile save timeout on shutdown: " + e.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        try {
            // C28: this event already fires on Paper's async login thread, so waiting
            // here keeps DB I/O off the main thread — but the wait must be bounded so
            // a hung database future kicks the login instead of blocking it forever.
            loadProfile(event.getUniqueId()).get(loginTimeoutSeconds(), TimeUnit.SECONDS);
            V profile = getProfile(event.getUniqueId());
            if (profile != null) {
                activeProfiles.put(event.getUniqueId(), profile);
            }
        } catch (TimeoutException e) {
            plugin.getLogger().severe("Timed out loading profile for " + event.getUniqueId());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "Failed to load profile!");
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