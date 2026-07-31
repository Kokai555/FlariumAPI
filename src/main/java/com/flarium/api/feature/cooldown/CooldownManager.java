package com.flarium.api.feature.cooldown;

import com.flarium.api.data.sql.DatabaseManager;
import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.core.scheduler.Task;
import com.flarium.api.core.util.TimeFormat;
import com.flarium.api.core.util.TimeUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class CooldownManager {

    private final DatabaseManager databaseManager;
    private final Scheduler scheduler;

    private final Cache<CooldownKey, Long> ephemeralCache;
    private final ConcurrentHashMap<CooldownKey, Long> persistentCache;
    private final ConcurrentHashMap<CooldownKey, Task> activeExpireTasks;
    private final ConcurrentHashMap<UUID, Set<CooldownKey>> keysByUuid;

    public CooldownManager(DatabaseManager databaseManager, Scheduler scheduler) {
        this.databaseManager = databaseManager;
        this.scheduler = scheduler;
        this.ephemeralCache = Caffeine.newBuilder()
                .expireAfterWrite(24, TimeUnit.HOURS)
                .removalListener((CooldownKey key, Long expiry, RemovalCause cause) -> unindexKey(key))
                .build();
        this.persistentCache = new ConcurrentHashMap<>();
        this.activeExpireTasks = new ConcurrentHashMap<>();
        this.keysByUuid = new ConcurrentHashMap<>();

        databaseManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS flarium_cooldowns (uuid VARCHAR(36), namespace VARCHAR(64), expiry BIGINT, PRIMARY KEY (uuid, namespace))",
                ps -> {}
        );
    }

    public void set(UUID uuid, String namespace, Duration duration) {
        set(uuid, namespace, duration, null, null);
    }

    public void set(UUID uuid, String namespace, Duration duration, Runnable onExpire, Executor executor) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        long expiry = System.currentTimeMillis() + duration.toMillis();
        ephemeralCache.put(key, expiry);
        indexKey(uuid, key);

        Task existingTask = activeExpireTasks.remove(key);
        if (existingTask != null) {
            existingTask.cancel();
        }

        if (onExpire != null) {
            Task task = scheduler.runAsyncDelayed(() -> {
                if (executor != null) {
                    executor.execute(onExpire);
                } else {
                    onExpire.run();
                }
                activeExpireTasks.remove(key);
            }, duration);
            activeExpireTasks.put(key, task);
        }
    }

    public CompletableFuture<Void> setPersistent(UUID uuid, String namespace, Duration duration) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        long expiry = System.currentTimeMillis() + duration.toMillis();
        persistentCache.put(key, expiry);
        indexKey(uuid, key);

        String sql = switch (databaseManager.getDatabaseType()) {
            case SQLITE -> "INSERT OR REPLACE INTO flarium_cooldowns (uuid, namespace, expiry) VALUES (?, ?, ?)";
            case MYSQL -> "INSERT INTO flarium_cooldowns (uuid, namespace, expiry) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE expiry = VALUES(expiry)";
        };
        return databaseManager.executeUpdate(sql, ps -> {
            try {
                ps.setString(1, uuid.toString());
                ps.setString(2, namespace);
                ps.setLong(3, expiry);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        });
    }

    public CompletableFuture<Void> loadPersistent(UUID uuid) {
        String sql = "SELECT namespace, expiry FROM flarium_cooldowns WHERE uuid = ?";
        return databaseManager.executeQuery(sql, ps -> {
            try {
                ps.setString(1, uuid.toString());
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        }, rs -> {
            try {
                while (rs.next()) {
                    CooldownKey key = CooldownKey.of(uuid, rs.getString("namespace"));
                    persistentCache.put(key, rs.getLong("expiry"));
                    indexKey(uuid, key);
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return null;
        });
    }

    public void invalidatePersistent(UUID uuid) {
        Set<CooldownKey> keys = keysByUuid.get(uuid);
        if (keys == null) return;
        for (CooldownKey key : keys) {
            persistentCache.remove(key);
            if (ephemeralCache.getIfPresent(key) == null) {
                unindexKey(key);
            }
        }
    }

    public void remove(UUID uuid, String namespace) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        ephemeralCache.invalidate(key);
        persistentCache.remove(key);
        unindexKey(key);

        Task task = activeExpireTasks.remove(key);
        if (task != null) {
            task.cancel();
        }

        removePersistentFromDatabase(uuid, namespace);
    }

    public void clearOnQuit(UUID uuid) {
        Set<CooldownKey> keys = keysByUuid.remove(uuid);
        if (keys == null) return;
        for (CooldownKey key : keys) {
            persistentCache.remove(key);
            ephemeralCache.invalidate(key);
            Task task = activeExpireTasks.remove(key);
            if (task != null) {
                task.cancel();
            }
        }
    }

    public boolean isActive(UUID uuid, String namespace) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        long now = System.currentTimeMillis();

        Long ephemeralExpiry = ephemeralCache.getIfPresent(key);
        if (ephemeralExpiry != null && ephemeralExpiry > now) return true;

        Long persistentExpiry = persistentCache.get(key);
        if (persistentExpiry != null && persistentExpiry > now) return true;

        if (persistentExpiry != null) {
            persistentCache.remove(key);
            unindexKey(key);
            removePersistentFromDatabase(uuid, namespace);
        }

        return false;
    }

    public Duration getRemaining(UUID uuid, String namespace) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        long now = System.currentTimeMillis();

        Long ephemeralExpiry = ephemeralCache.getIfPresent(key);
        if (ephemeralExpiry != null && ephemeralExpiry > now) {
            return Duration.ofMillis(ephemeralExpiry - now);
        }

        Long persistentExpiry = persistentCache.get(key);
        if (persistentExpiry != null && persistentExpiry > now) {
            return Duration.ofMillis(persistentExpiry - now);
        }

        return Duration.ZERO;
    }

    public String getFormattedRemaining(UUID uuid, String namespace, TimeFormat format) {
        return TimeUtil.formatDuration(getRemaining(uuid, namespace).getSeconds(), format);
    }

    public void shutdown() {
        activeExpireTasks.values().forEach(Task::cancel);
        activeExpireTasks.clear();
    }

    private void indexKey(UUID uuid, CooldownKey key) {
        keysByUuid.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet()).add(key);
    }

    private void unindexKey(CooldownKey key) {
        if (key == null) return;
        Set<CooldownKey> keys = keysByUuid.get(key.uuid());
        if (keys != null) {
            keys.remove(key);
            if (keys.isEmpty()) {
                keysByUuid.remove(key.uuid(), keys);
            }
        }
    }

    private void removePersistentFromDatabase(UUID uuid, String namespace) {
        String sql = "DELETE FROM flarium_cooldowns WHERE uuid = ? AND namespace = ?";
        databaseManager.executeUpdate(sql, ps -> {
            try {
                ps.setString(1, uuid.toString());
                ps.setString(2, namespace);
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
        });
    }
}