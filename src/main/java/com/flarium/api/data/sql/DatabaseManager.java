package com.flarium.api.data.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final DatabaseType databaseType;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    /**
     * C40: connection owned by an in-flight {@link #executeTransaction} on this thread.
     *
     * <p>The SQLite pool holds a single connection ({@code maximumPoolSize(1)} is
     * intentional: SQLite is single-writer and a larger pool only trades starvation
     * for {@code SQLITE_BUSY} lock failures). While a transaction owns that connection,
     * any manager call needing a pool connection from the same thread would wait until
     * Hikari's {@code connectionTimeout} and then fail. Track the owner thread-locally
     * so re-entry fails fast with an explicit error instead of starving.
     */
    private final ThreadLocal<Connection> transactionConnection = new ThreadLocal<>();

    public DatabaseManager(JavaPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.databaseType = config.type();
        // C31: initialize the pool before creating the executor so a failed
        // init cannot orphan an executor instance.
        HikariDataSource dataSource = initHikari(config);
        this.dataSource = dataSource;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public DatabaseType getDatabaseType() {
        return databaseType;
    }

    private HikariDataSource initHikari(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        config.type().configure(hikari, plugin, config);
        hikari.setPoolName("FlariumAPI-DB-Pool");

        // C31: only report success after construction actually succeeded.
        HikariDataSource dataSource = new HikariDataSource(hikari);
        plugin.getLogger().info("Database connection established: " + config.type().name());
        return dataSource;
    }

    public CompletableFuture<Void> executeUpdate(String sql, Consumer<PreparedStatement> setter) {
        // C40: fail fast instead of starving the single SQLite connection owned by an enclosing transaction.
        requireNoActiveTransaction("executeUpdate");
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setter.accept(ps);
                ps.executeUpdate();
            } catch (Exception e) {
                // C31: also cover RuntimeExceptions from the setter (e.g. wrapped
                // SQLExceptions), which previously bypassed error reporting.
                plugin.getLogger().severe("SQL Error (Update): " + e.getMessage());
                if (e instanceof CompletionException ce) throw ce;
                throw new CompletionException(e);
            }
        }, executor);
    }

    public <T> CompletableFuture<T> executeQuery(String sql, Consumer<PreparedStatement> setter, Function<ResultSet, T> mapper) {
        // C40: see executeUpdate.
        requireNoActiveTransaction("executeQuery");
        return CompletableFuture.supplyAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setter.accept(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    return mapper.apply(rs);
                }
            } catch (Exception e) {
                // C31: also cover RuntimeExceptions from setter/mapper, which
                // previously bypassed error reporting.
                plugin.getLogger().severe("SQL Error (Query): " + e.getMessage());
                if (e instanceof CompletionException ce) throw ce;
                throw new CompletionException(e);
            }
        }, executor);
    }

    public <T> CompletableFuture<List<T>> executeQueryList(String sql, Consumer<PreparedStatement> setter, Function<ResultSet, T> mapper) {
        // C40: see executeUpdate.
        requireNoActiveTransaction("executeQueryList");
        return CompletableFuture.supplyAsync(() -> {
            List<T> results = new ArrayList<>();
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setter.accept(ps);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        results.add(mapper.apply(rs));
                    }
                }
            } catch (Exception e) {
                // C31: same logging coverage as executeQuery above.
                plugin.getLogger().severe("SQL Error (QueryList): " + e.getMessage());
                if (e instanceof CompletionException ce) throw ce;
                throw new CompletionException(e);
            }
            return results;
        }, executor);
    }

    /**
     * Runs {@code consumer} with a single connection in a transaction.
     *
     * <p><b>C40 contract — not re-entrant:</b> the callback owns the transaction's
     * connection for its duration. Do not call {@link #executeUpdate},
     * {@link #executeQuery}, {@link #executeQueryList}, {@link #executeBatch} or
     * {@link #executeTransaction} from the callback (directly or via
     * {@code join()}/{@code get()}): on SQLite the pool holds exactly one connection,
     * so a re-entrant call would wait for it until Hikari's {@code connectionTimeout}
     * instead of completing. Re-entrant calls fail fast with
     * {@link IllegalStateException}. Use {@link #update}, {@link #query} and
     * {@link #queryList} with the provided {@link Connection} for statements that
     * must run inside the transaction.
     */
    public CompletableFuture<Void> executeTransaction(Consumer<Connection> consumer) {
        // C40: nested transactions on the owning thread fail fast (see contract above).
        if (transactionConnection.get() != null) {
            CompletableFuture<Void> rejected = new CompletableFuture<>();
            rejected.completeExceptionally(newReentryError("executeTransaction"));
            return rejected;
        }
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                transactionConnection.set(conn);
                try {
                    consumer.accept(conn);
                    conn.commit();
                } catch (Throwable t) {
                    // C31: roll back on any failure including Errors, which
                    // previously bypassed rollback via catch (Exception).
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        t.addSuppressed(rollbackEx);
                    }
                    plugin.getLogger().severe("SQL Transaction Error, rolled back: " + t.getMessage());
                    if (t instanceof CompletionException ce) throw ce;
                    if (t instanceof Exception e) throw new CompletionException(e);
                    throw new CompletionException(t);
                } finally {
                    transactionConnection.remove();
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Connection Error (Transaction): " + e.getMessage());
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CompletableFuture<Void> executeBatch(String sql, Consumer<PreparedStatement> setter) {
        // C40: see executeUpdate.
        requireNoActiveTransaction("executeBatch");
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                try {
                    setter.accept(ps);
                    ps.executeBatch();
                    conn.commit();
                } catch (Throwable t) {
                    // C31: roll back on any failure including Errors, which
                    // previously bypassed rollback via catch (Exception).
                    try {
                        conn.rollback();
                    } catch (SQLException rollbackEx) {
                        t.addSuppressed(rollbackEx);
                    }
                    plugin.getLogger().severe("SQL Batch Error, rolled back: " + t.getMessage());
                    if (t instanceof CompletionException ce) throw ce;
                    if (t instanceof Exception e) throw new CompletionException(e);
                    throw new CompletionException(t);
                } finally {
                    try {
                        conn.setAutoCommit(true);
                    } catch (SQLException ignored) {
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Connection Error (Batch): " + e.getMessage());
                throw new CompletionException(e);
            }
        }, executor);
    }

    /**
     * C40: rejects pool-backed calls made from the thread that owns an in-flight
     * transaction. Checked synchronously so misuse surfaces immediately instead of
     * starving the (single, on SQLite) pooled connection until timeout.
     */
    private void requireNoActiveTransaction(String operation) {
        if (transactionConnection.get() != null) {
            throw newReentryError(operation);
        }
    }

    private static IllegalStateException newReentryError(String operation) {
        return new IllegalStateException(
                "DatabaseManager." + operation + " must not be called from inside an executeTransaction callback: "
                        + "the callback owns the transaction connection (the SQLite pool holds exactly one), "
                        + "so a re-entrant call could never complete. Use update/query/queryList(Connection, ...) "
                        + "with the provided Connection instead.");
    }

    /**
     * C40: runs an update on a transaction-owned connection. Use inside
     * {@link #executeTransaction} callbacks instead of re-entering the manager.
     */
    public static void update(Connection conn, String sql, Consumer<PreparedStatement> setter) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.accept(ps);
            ps.executeUpdate();
        }
    }

    /**
     * C40: runs a query on a transaction-owned connection. Use inside
     * {@link #executeTransaction} callbacks instead of re-entering the manager.
     */
    public static <T> T query(Connection conn, String sql, Consumer<PreparedStatement> setter,
                              Function<ResultSet, T> mapper) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.accept(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return mapper.apply(rs);
            }
        }
    }

    /**
     * C40: runs a list query on a transaction-owned connection. Use inside
     * {@link #executeTransaction} callbacks instead of re-entering the manager.
     */
    public static <T> List<T> queryList(Connection conn, String sql, Consumer<PreparedStatement> setter,
                                        Function<ResultSet, T> mapper) throws SQLException {
        List<T> results = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.accept(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    results.add(mapper.apply(rs));
                }
            }
        }
        return results;
    }

    public void close() {
        // C31: shut down the executor first (in-flight work needs the pool),
        // await its termination, and always attempt the pool close even if an
        // earlier stage fails. Idempotent.
        try {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException interrupted) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        } finally {
            try {
                if (dataSource != null && !dataSource.isClosed()) {
                    dataSource.close();
                }
            } finally {
                plugin.getLogger().info("Database connection closed.");
            }
        }
    }
}
