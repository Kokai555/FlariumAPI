package com.flarium.api.data.sql;

import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * C40: SQLite single-connection pool + transaction re-entry semantics.
 *
 * <p>Negative control: {@link #transactionReentryFailsFastInsteadOfStarving} expects
 * fail-fast {@link IllegalStateException}; pre-fix the re-entrant call starves until
 * Hikari's connectionTimeout, so the bounded {@code get(8s)} observes a timeout.
 */
class DatabaseTransactionTest {

    @TempDir
    Path tempDir;

    private JavaPlugin plugin;
    private DatabaseManager manager;

    @BeforeEach
    void setUp() {
        plugin = mock(JavaPlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getLogger("DatabaseTransactionTest"));
        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        manager = new DatabaseManager(plugin, new DatabaseConfig(DatabaseType.SQLITE, null, 0, null, null, null));
        manager.executeUpdate("CREATE TABLE IF NOT EXISTS probe (id INTEGER PRIMARY KEY, v TEXT)", ps -> {
        }).join();
    }

    @AfterEach
    void tearDown() {
        manager.close();
    }

    @Test
    void transactionCommits() throws Exception {
        manager.executeTransaction(conn -> {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO probe (v) VALUES ('a')")) {
                ps.executeUpdate();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get(10, TimeUnit.SECONDS);

        List<String> rows = manager.executeQueryList("SELECT v FROM probe", ps -> {
        }, rs -> uncheckedGet(rs)).get(10, TimeUnit.SECONDS);
        assertEquals(List.of("a"), rows);
    }

    @Test
    void transactionFailureRollsBack() throws Exception {
        java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class, () ->
                manager.executeTransaction(conn -> {
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO probe (v) VALUES ('x')")) {
                        ps.executeUpdate();
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                    throw new RuntimeException("boom");
                }).get(10, TimeUnit.SECONDS));
        assertNotNull(failure.getCause());

        List<String> rows = manager.executeQueryList("SELECT v FROM probe", ps -> {
        }, rs -> uncheckedGet(rs)).get(10, TimeUnit.SECONDS);
        assertTrue(rows.isEmpty(), "rolled-back insert must not be visible");
    }

    @Test
    void transactionReentryFailsFastInsteadOfStarving() throws Exception {
        // Callback re-enters the manager while the single SQLite connection is held.
        // Fixed behavior: fail fast. Pre-fix: starves until Hikari timeout (~30s),
        // so the bounded wait below times out instead.
        CompletableFuture<Void> outer = manager.executeTransaction(conn ->
                manager.executeUpdate("INSERT INTO probe (v) VALUES ('reenter')", ps -> {
                }).join());

        java.util.concurrent.ExecutionException failure = assertThrows(java.util.concurrent.ExecutionException.class,
                () -> outer.get(8, TimeUnit.SECONDS),
                "re-entrant manager call inside a transaction must fail fast, not starve the pool");
        assertInstanceOf(IllegalStateException.class, rootCause(failure),
                "expected explicit non-reentrancy signal, got: " + rootCause(failure));
    }

    @Test
    void connectionBoundHelpersWorkInsideTransaction() throws Exception {
        manager.executeTransaction(conn -> {
            try {
                DatabaseManager.update(conn, "INSERT INTO probe (v) VALUES ('h1')", ps -> {
                });
                DatabaseManager.update(conn, "INSERT INTO probe (v) VALUES ('h2')", ps -> {
                });
                List<String> inside = DatabaseManager.queryList(conn, "SELECT v FROM probe ORDER BY id",
                        ps -> {
                        }, rs -> uncheckedGet(rs));
                assertEquals(List.of("h1", "h2"), inside);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }).get(10, TimeUnit.SECONDS);

        List<String> rows = manager.executeQueryList("SELECT v FROM probe ORDER BY id", ps -> {
        }, rs -> uncheckedGet(rs)).get(10, TimeUnit.SECONDS);
        assertEquals(List.of("h1", "h2"), rows);
    }

    @Test
    void concurrentTransactionsAllComplete() throws Exception {
        // SQLite pool size stays 1: concurrent top-level transactions must serialize
        // via the pool rather than deadlock.
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            final int n = i;
            futures.add(manager.executeTransaction(conn -> {
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO probe (v) VALUES (?)")) {
                    ps.setString(1, "c" + n);
                    ps.executeUpdate();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(25, TimeUnit.SECONDS);

        Integer count = manager.executeQuery("SELECT COUNT(*) FROM probe", ps -> {
        }, rs -> uncheckedCount(rs)).get(10, TimeUnit.SECONDS);
        assertEquals(6, count);
    }

    @Test
    void sqlitePoolSizeRemainsOne() {
        assertEquals(1, poolSizeOf(manager),
                "C40 fix must not blindly enlarge the SQLite pool to hide re-entry");
    }

    private static String uncheckedGet(java.sql.ResultSet rs) {
        try {
            return rs.getString(1);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Integer uncheckedCount(java.sql.ResultSet rs) {
        try {
            return rs.getInt(1);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private static Throwable rootCause(Throwable t) {
        Throwable current = t;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static int poolSizeOf(DatabaseManager manager) {
        try {
            var field = DatabaseManager.class.getDeclaredField("dataSource");
            field.setAccessible(true);
            com.zaxxer.hikari.HikariDataSource ds =
                    (com.zaxxer.hikari.HikariDataSource) field.get(manager);
            return ds.getMaximumPoolSize();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // Compile-time reference so the test fails clearly if the helper contract changes.
    @SuppressWarnings("unused")
    private static void helperSignatures(Connection conn) throws Exception {
        DatabaseManager.update(conn, "SELECT 1", ps -> {
        });
        DatabaseManager.query(conn, "SELECT 1", ps -> {
        }, rs -> null);
        DatabaseManager.queryList(conn, "SELECT 1", ps -> {
        }, rs -> null);
    }
}
