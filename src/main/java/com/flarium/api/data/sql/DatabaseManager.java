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

    public CompletableFuture<Void> executeTransaction(Consumer<Connection> consumer) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
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
