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
import java.util.function.Consumer;
import java.util.function.Function;

public class DatabaseManager {

    private final JavaPlugin plugin;
    private final HikariDataSource dataSource;

    public DatabaseManager(JavaPlugin plugin, DatabaseConfig config) {
        this.plugin = plugin;
        this.dataSource = initHikari(config);
    }

    private HikariDataSource initHikari(DatabaseConfig config) {
        HikariConfig hikari = new HikariConfig();

        if (config.type().equalsIgnoreCase("MYSQL")) {
            hikari.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", config.address(), config.port(), config.databaseName()));
            hikari.setUsername(config.username());
            hikari.setPassword(config.password());
            hikari.setMaximumPoolSize(10);
            hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
            plugin.getLogger().info("Database connection established: MySQL (" + config.address() + ")");
        } else {
            hikari.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/database.db");
            hikari.setDriverClassName("org.sqlite.JDBC");
            hikari.setMaximumPoolSize(1);
            plugin.getLogger().info("Database connection established: SQLite (database.db)");
        }

        hikari.setPoolName("FlariumAPI-DB-Pool");
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
            }
        });
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
                return null;
            }
        });
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
            }
            return results;
        });
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
                }
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Connection Error (Transaction): " + e.getMessage());
            }
        });
    }

    public CompletableFuture<Void> executeBatch(String sql, Consumer<PreparedStatement> setter) {
        return CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                conn.setAutoCommit(false);
                setter.accept(ps);
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                plugin.getLogger().severe("SQL Batch Error: " + e.getMessage());
            }
        });
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            plugin.getLogger().info("Database connection closed.");
        }
    }
}