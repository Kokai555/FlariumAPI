package com.flarium.api.data.sql;

import org.bukkit.configuration.ConfigurationSection;

public record DatabaseConfig(
        DatabaseType type,
        String address,
        int port,
        String databaseName,
        String username,
        String password
) {
    public static DatabaseConfig load(ConfigurationSection section) {
        if (section == null) {
            return new DatabaseConfig(DatabaseType.SQLITE, null, 0, null, null, null);
        }

        String typeStr = section.getString("type", "SQLITE");
        if (typeStr == null || typeStr.trim().isEmpty()) {
            typeStr = "SQLITE";
        }

        DatabaseType type;
        try {
            type = DatabaseType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = DatabaseType.SQLITE;
        }

        if (type == DatabaseType.SQLITE) {
            return new DatabaseConfig(DatabaseType.SQLITE, null, 0, null, null, null);
        }

        ConfigurationSection settings = section.getConfigurationSection("settings");
        if (settings == null) {
            return new DatabaseConfig(DatabaseType.SQLITE, null, 0, null, null, null);
        }

        return new DatabaseConfig(
                type,
                settings.getString("address", "localhost"),
                settings.getInt("port", 3306),
                settings.getString("database", "flarium"),
                settings.getString("username", "root"),
                settings.getString("password", "")
        );
    }
}