package com.flarium.api.data.sql;

import com.zaxxer.hikari.HikariConfig;
import org.bukkit.plugin.java.JavaPlugin;

public enum DatabaseType {
    SQLITE {
        @Override
        public void configure(HikariConfig config, JavaPlugin plugin, DatabaseConfig dbConfig) {
            config.setJdbcUrl("jdbc:sqlite:" + plugin.getDataFolder().getAbsolutePath() + "/database.db");
            config.setDriverClassName("org.sqlite.JDBC");
            config.setMaximumPoolSize(1);
        }
    },
    MYSQL {
        @Override
        public void configure(HikariConfig config, JavaPlugin plugin, DatabaseConfig dbConfig) {
            config.setJdbcUrl(String.format("jdbc:mysql://%s:%d/%s", dbConfig.address(), dbConfig.port(), dbConfig.databaseName()));
            config.setUsername(dbConfig.username());
            config.setPassword(dbConfig.password());
            config.setMaximumPoolSize(10);
            config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        }
    };

    public abstract void configure(HikariConfig config, JavaPlugin plugin, DatabaseConfig dbConfig);
}