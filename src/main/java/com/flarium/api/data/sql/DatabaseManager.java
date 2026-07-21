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
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final HikariDataSource dataSource;
    private final ExecutorService executor;

    public DatabaseManager(JavaPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
        this.dataSource = initHikari(config);
    }

    private HikariDataSource initHikari(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();
        config.type().configure(hikari, plugin, config);
        hikari.setPoolName("FlariumAPI-DB-Pool");

        plugin.getLogger().info("Database connection established: " + config.type().name());
        return new HikariDataSource(hikari);
    }

    public CompletableFuture<Void> executeUpdate(String sql, Consumer<PreparedStatement> setter) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                setter.accept(ps);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Error (Update): " + e.getMessage());
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
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Error (Query): " + e.getMessage());
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
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Error (QueryList): " + e.getMessage());
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
                } catch (SQLException e) {
                    conn.rollback();
                    plugin.getLogger().severe("SQL Transaction Error, rolled back: " + e.getMessage());
                    throw new CompletionException(e);
                } finally {
                    conn.setAutoCommit(true);
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
                } catch (SQLException e) {
                    conn.rollback();
                    plugin.getLogger().severe("SQL Batch Error, rolled back: " + e.getMessage());
                    throw new CompletionException(e);
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Connection Error (Batch): " + e.getMessage());
                throw new CompletionException(e);
            }
        }, executor);
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            executor.shutdown();
            plugin.getLogger().info("Database connection closed.");
        }
    }
}