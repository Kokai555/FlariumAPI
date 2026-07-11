package com.flarium.api.feature.cooldown;

import com.flarium.api.data.sql.DatabaseManager;
import com.flarium.api.core.scheduler.Scheduler;
import com.flarium.api.core.scheduler.Task;
import com.flarium.api.core.util.TimeFormat;
import com.flarium.api.core.util.TimeUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Duration;
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

    public CooldownManager(DatabaseManager databaseManager, Scheduler scheduler) {
        this.databaseManager = databaseManager;
        this.scheduler = scheduler;
        this.ephemeralCache = Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .build();
        this.persistentCache = new ConcurrentHashMap<>();
        this.activeExpireTasks = new ConcurrentHashMap<>();

        databaseManager.executeUpdate(
                "CREATE TABLE IF NOT EXISTS flarium_cooldowns (uuid VARCHAR(36), namespace VARCHAR(64), expiry BIGINT, PRIMARY KEY (uuid, namespace))",
                ps -> {}
        ).join();
    }

    public void set(UUID uuid, String namespace, Duration duration) {
        set(uuid, namespace, duration, null, null);
    }

    public void set(UUID uuid, String namespace, Duration duration, Runnable onExpire, Executor executor) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        long expiry = System.currentTimeMillis() + duration.toMillis();
        ephemeralCache.put(key, expiry);

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

        return databaseManager.executeTransaction(conn -> {
            try (PreparedStatement del = conn.prepareStatement("DELETE FROM flarium_cooldowns WHERE uuid = ? AND namespace = ?")) {
                del.setString(1, uuid.toString());
                del.setString(2, namespace);
                del.executeUpdate();
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            try (PreparedStatement ins = conn.prepareStatement("INSERT INTO flarium_cooldowns (uuid, namespace, expiry) VALUES (?, ?, ?)")) {
                ins.setString(1, uuid.toString());
                ins.setString(2, namespace);
                ins.setLong(3, expiry);
                ins.executeUpdate();
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
                    persistentCache.put(CooldownKey.of(uuid, rs.getString("namespace")), rs.getLong("expiry"));
                }
            } catch (SQLException e) {
                throw new CompletionException(e);
            }
            return null;
        });
    }

    public void invalidatePersistent(UUID uuid) {
        persistentCache.entrySet().removeIf(entry -> entry.getKey().uuid().equals(uuid));
    }

    public void remove(UUID uuid, String namespace) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        ephemeralCache.invalidate(key);
        persistentCache.remove(key);

        Task task = activeExpireTasks.remove(key);
        if (task != null) {
            task.cancel();
        }

        removePersistentFromDatabase(uuid, namespace);
    }

    public void clearOnQuit(UUID uuid) {
        persistentCache.entrySet().removeIf(entry -> entry.getKey().uuid().equals(uuid));
        ephemeralCache.asMap().keySet().removeIf(key -> key.uuid().equals(uuid));

        activeExpireTasks.entrySet().removeIf(entry -> {
            if (entry.getKey().uuid().equals(uuid)) {
                entry.getValue().cancel();
                return true;
            }
            return false;
        });
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