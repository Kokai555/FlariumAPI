package com.flarium.api.database;

import org.bukkit.configuration.ConfigurationSection;

public record DatabaseConfig(
        String type,
        String address,
        int port,
        String databaseName,
        String username,
        String password
) {
    public static DatabaseConfig load(ConfigurationSection section) {
        if (section == null) {
            return new DatabaseConfig("SQLITE", null, 0, null, null, null);
        }

        // Ha a type üres, alapértelmezetten SQLite-ot használunk
        String type = section.getString("type", "SQLITE");
        if (type == null || type.trim().isEmpty()) {
            type = "SQLITE";
        }
        type = type.toUpperCase();

        // Ha SQLite, a többi beállítás nem is kell
        if (type.equals("SQLITE")) {
            return new DatabaseConfig("SQLITE", null, 0, null, null, null);
        }

        // MySQL beállítások betöltése
        ConfigurationSection settings = section.getConfigurationSection("settings");
        if (settings == null) {
            return new DatabaseConfig("SQLITE", null, 0, null, null, null);
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