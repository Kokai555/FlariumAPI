package com.flarium.api.data.cache;

import com.destroystokyo.paper.profile.PlayerProfile;
import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * C28: profile loading cannot block login forever; async DB I/O preserved;
 * concurrent loads coalesce; shutdown bounded and complete; eviction saves.
 *
 * <p>Negative control: {@link #loginLoadHangingKicksWithinTimeout} expects the
 * pre-login handler to give up within the login timeout and kick. Pre-fix the
 * unbounded {@code join()} blocks forever, so the bounded wait observes a
 * timeout instead.
 */
@SuppressWarnings({"deprecation", "removal"})
class ProfileManagerLifecycleTest {

    private JavaPlugin plugin;
    private MockedStatic<Bukkit> bukkit;
    private ExecutorService daemon;

    /** Controllable manager: scripted loads, tracked saves, short test timeouts. */
    static final class TestProfiles extends AbstractProfileManager<String> {
        final ConcurrentHashMap<UUID, CompletableFuture<String>> scripts = new ConcurrentHashMap<>();
        final AtomicInteger dbLoads = new AtomicInteger();
        final CopyOnWriteArrayList<String> saved = new CopyOnWriteArrayList<>();
        final ConcurrentLinkedQueue<CompletableFuture<Void>> hangingSaves = new ConcurrentLinkedQueue<>();
        volatile boolean hangSaves;

        TestProfiles(JavaPlugin plugin, long expireSeconds, long maxSize) {
            super(plugin, expireSeconds, maxSize);
        }

        @Override
        protected long loginTimeoutSeconds() {
            return 2;
        }

        @Override
        protected long shutdownTimeoutSeconds() {
            return 2;
        }

        @Override
        protected CompletableFuture<String> loadFromDatabase(UUID uuid) {
            dbLoads.incrementAndGet();
            return scripts.getOrDefault(uuid, CompletableFuture.completedFuture("profile:" + uuid));
        }

        @Override
        protected CompletableFuture<Void> saveToDatabase(String profile) {
            saved.add(profile);
            if (hangSaves) {
                CompletableFuture<Void> hanging = new CompletableFuture<>();
                hangingSaves.add(hanging);
                return hanging;
            }
            return CompletableFuture.completedFuture(null);
        }
    }

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("ProfileManagerLifecycleTest"));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenReturn(null);
        // AsyncPlayerPreLoginEvent builds its profile via Bukkit.createProfile (no server here).
        bukkit.when(() -> Bukkit.createProfile(any(UUID.class), any(String.class)))
                .thenAnswer(invocation -> {
                    PlayerProfile profile = mock(PlayerProfile.class);
                    UUID id = invocation.getArgument(0);
                    when(profile.getId()).thenReturn(id);
                    return profile;
                });
        daemon = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "c28-probe");
            t.setDaemon(true);
            return t;
        });
    }

    @AfterEach
    void tearDown() {
        daemon.shutdownNow();
        bukkit.close();
    }

    @Test
    void concurrentLoadsCoalesceToSingleDatabaseHit() throws Exception {
        TestProfiles manager = new TestProfiles(plugin, 60, 100);
        UUID uuid = UUID.randomUUID();
        CompletableFuture<String> dbFuture = new CompletableFuture<>();
        manager.scripts.put(uuid, dbFuture);

        int callers = 8;
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch go = new CountDownLatch(1);
        List<CompletableFuture<Void>> calls = new ArrayList<>();
        for (int i = 0; i < callers; i++) {
            calls.add(CompletableFuture.runAsync(() -> {
                ready.countDown();
                try {
                    assertTrue(go.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                manager.loadProfile(uuid).orTimeout(5, TimeUnit.SECONDS).join();
            }, daemon));
        }
        assertTrue(ready.await(5, TimeUnit.SECONDS));
        go.countDown();
        // Let every caller enter loadProfile while the DB future is still pending.
        Thread.sleep(300);
        assertEquals(1, manager.dbLoads.get(), "concurrent loads must coalesce to one DB hit");

        dbFuture.complete("profile:" + uuid);
        CompletableFuture.allOf(calls.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
        assertEquals(1, manager.dbLoads.get());
        assertEquals("profile:" + uuid, manager.getProfile(uuid));
    }

    @Test
    void loginLoadHangingKicksWithinTimeout() throws Exception {
        TestProfiles manager = new TestProfiles(plugin, 60, 100);
        UUID uuid = UUID.randomUUID();
        CompletableFuture<String> hanging = new CompletableFuture<>();
        manager.scripts.put(uuid, hanging);
        AsyncPlayerPreLoginEvent event =
                new AsyncPlayerPreLoginEvent("probe", InetAddress.getLoopbackAddress(), uuid);

        CompletableFuture<Void> handler = CompletableFuture.runAsync(() -> manager.onPreLogin(event), daemon);
        try {
            // Fixed behavior: bounded wait then kick. Pre-fix join() never returns here.
            handler.get(5, TimeUnit.SECONDS);
        } finally {
            hanging.completeExceptionally(new RuntimeException("release probe thread"));
        }
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult(),
                "hung profile load must kick within the login timeout, not block login forever");
    }

    @Test
    void failedLoadKicksAndRetrySucceeds() throws Exception {
        TestProfiles manager = new TestProfiles(plugin, 60, 100);
        UUID uuid = UUID.randomUUID();
        manager.scripts.put(uuid, CompletableFuture.failedFuture(new RuntimeException("db down")));
        AsyncPlayerPreLoginEvent event =
                new AsyncPlayerPreLoginEvent("probe", InetAddress.getLoopbackAddress(), uuid);

        manager.onPreLogin(event);
        assertEquals(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, event.getLoginResult());

        manager.scripts.put(uuid, CompletableFuture.completedFuture("profile:" + uuid));
        AsyncPlayerPreLoginEvent retry =
                new AsyncPlayerPreLoginEvent("probe", InetAddress.getLoopbackAddress(), uuid);
        manager.onPreLogin(retry);
        assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, retry.getLoginResult());
        assertEquals("profile:" + uuid, manager.getProfile(uuid));
    }

    @Test
    void shutdownCompletesCleanly() throws Exception {
        TestProfiles manager = new TestProfiles(plugin, 60, 100);
        UUID uuid = UUID.randomUUID();
        manager.loadProfile(uuid).get(5, TimeUnit.SECONDS);

        long start = System.nanoTime();
        manager.shutdown();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMs < 10_000, "shutdown must be bounded, took " + elapsedMs + "ms");
        assertTrue(manager.saved.contains("profile:" + uuid), "shutdown must persist cached profiles");
        assertNull(manager.getProfile(uuid), "shutdown must invalidate profiles");
    }

    @Test
    void shutdownBoundedWithHangingSave() {
        TestProfiles manager = new TestProfiles(plugin, 60, 100);
        manager.hangSaves = true;
        UUID uuid = UUID.randomUUID();
        manager.loadProfile(uuid).join();
        manager.saveAndInvalidate(uuid);

        long start = System.nanoTime();
        manager.shutdown();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        assertTrue(elapsedMs < 10_000,
                "shutdown must stay bounded even with a hanging save, took " + elapsedMs + "ms");
        for (CompletableFuture<Void> hanging : manager.hangingSaves) {
            hanging.completeExceptionally(new TimeoutException("probe cleanup"));
        }
    }

    @Test
    void shutdownBoundedWithHangingLoad() {
        TestProfiles manager = new TestProfiles(plugin, 60, 100);
        UUID uuid = UUID.randomUUID();
        CompletableFuture<String> hanging = new CompletableFuture<>();
        manager.scripts.put(uuid, hanging);
        CompletableFuture<Void> load = manager.loadProfile(uuid);

        long start = System.nanoTime();
        manager.shutdown();
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        hanging.complete("profile:" + uuid);
        load.orTimeout(5, TimeUnit.SECONDS).join();
        assertTrue(elapsedMs < 10_000,
                "shutdown must stay bounded even with an in-flight load, took " + elapsedMs + "ms");
    }

    @Test
    void evictionTriggersSave() throws Exception {
        TestProfiles manager = new TestProfiles(plugin, 60, 1);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        manager.loadProfile(first).get(5, TimeUnit.SECONDS);
        manager.loadProfile(second).get(5, TimeUnit.SECONDS);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!manager.saved.contains("profile:" + first) && System.nanoTime() < deadline) {
            Thread.sleep(50);
        }
        assertTrue(manager.saved.contains("profile:" + first),
                "evicted entries must be persisted, saved=" + manager.saved);
    }
}
