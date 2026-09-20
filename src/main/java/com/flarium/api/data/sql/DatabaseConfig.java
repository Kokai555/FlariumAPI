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
    public DatabaseConfig {
        // C39: fail fast at the configuration boundary. databaseName is interpolated
        // into the MySQL JDBC URL path (DatabaseType), so URL delimiters must be
        // rejected rather than stripped or encoded.
        if (type == DatabaseType.MYSQL) {
            validateDatabaseName(databaseName);
        }
    }

    private static void validateDatabaseName(String databaseName) {
        if (databaseName == null || databaseName.isBlank()) {
            throw new IllegalArgumentException("Invalid MySQL database name: must not be blank");
        }
        if (databaseName.length() > 64) {
            throw new IllegalArgumentException("Invalid MySQL database name: exceeds 64 characters");
        }
        for (int i = 0; i < databaseName.length(); i++) {
            char c = databaseName.charAt(i);
            if (c == '?' || c == '&' || c == '=' || c == '#' || c == ';' || c == '/' || c == '\\' || c == '%'
                    || Character.isWhitespace(c) || Character.isISOControl(c)) {
                throw new IllegalArgumentException(
                        "Invalid MySQL database name '" + databaseName + "': URL delimiters and whitespace are not allowed");
            }
        }
    }

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