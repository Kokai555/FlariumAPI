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

    // C27: ephemeral values are monotonic System.nanoTime() deadlines (immune to
    // wall-clock jumps). Persisted values in persistentCache/DB stay epoch-millis.
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
        ephemeralCache.put(key, monotonicDeadlineNanos(duration));
        indexKey(uuid, key);

        Task existingTask = activeExpireTasks.remove(key);
        if (existingTask != null) {
            existingTask.cancel();
        }

        if (onExpire != null) {
            final Task[] taskHolder = new Task[1];
            Task task = scheduler.runAsyncDelayed(() -> {
                try {
                    if (executor != null) {
                        executor.execute(onExpire);
                    } else {
                        onExpire.run();
                    }
                } finally {
                    Task self = taskHolder[0];
                    if (self != null) {
                        activeExpireTasks.remove(key, self);
                    } else {
                        activeExpireTasks.remove(key);
                    }
                }
            }, scheduleDelay(duration));
            taskHolder[0] = task;
            activeExpireTasks.put(key, task);
        }
    }

    public CompletableFuture<Void> setPersistent(UUID uuid, String namespace, Duration duration) {
        CooldownKey key = CooldownKey.of(uuid, namespace);
        long expiry = epochExpiryMillis(duration);
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
        long nowMono = System.nanoTime();

        Long ephemeralDeadline = ephemeralCache.getIfPresent(key);
        if (ephemeralDeadline != null && ephemeralDeadline > nowMono) return true;

        long now = System.currentTimeMillis();

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
        long nowMono = System.nanoTime();

        Long ephemeralDeadline = ephemeralCache.getIfPresent(key);
        if (ephemeralDeadline != null && ephemeralDeadline > nowMono) {
            return Duration.ofNanos(ephemeralDeadline - nowMono);
        }

        long now = System.currentTimeMillis();

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

    /**
     * C27: in-memory (ephemeral) deadlines use a monotonic clock so wall-clock
     * jumps (NTP/operator changes) cannot shorten or extend active cooldowns.
     * Persisted state intentionally stays on epoch millis
     * (see {@link #epochExpiryMillis}) because it must survive restarts and be
     * comparable across sessions.
     */
    private static long monotonicDeadlineNanos(Duration duration) {
        return saturatingAdd(System.nanoTime(), toNanosSaturated(duration));
    }

    /**
     * C27: persisted expiries remain epoch-millis based (DB + cross-restart
     * comparisons). Saturates instead of silently wrapping on overflow.
     */
    private static long epochExpiryMillis(Duration duration) {
        return saturatingAdd(System.currentTimeMillis(), toMillisSaturated(duration));
    }

    /**
     * C27: never hand the scheduler a negative delay. Negative/zero durations
     * keep the existing contract (no active cooldown) while a registered
     * callback still fires on the next tick instead of throwing inside the
     * scheduler.
     */
    private static Duration scheduleDelay(Duration duration) {
        if (duration.isNegative()) return Duration.ZERO;
        return duration;
    }

    private static long toNanosSaturated(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long toMillisSaturated(Duration duration) {
        try {
            return duration.toMillis();
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatingAdd(long a, long b) {
        long result = a + b;
        if (b > 0 && result < a) return Long.MAX_VALUE;
        if (b < 0 && result > a) return Long.MIN_VALUE;
        return result;
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