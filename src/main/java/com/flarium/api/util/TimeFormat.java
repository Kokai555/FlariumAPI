package com.flarium.api.util;

import org.bukkit.configuration.ConfigurationSection;

public record TimeFormat(String second, String minute, String hour, String day) {

    public static TimeFormat load(ConfigurationSection section) {
        if (section == null) {
            return new TimeFormat("s", "m", "h", "d");
        }
        return new TimeFormat(
                section.getString("second", "s"),
                section.getString("minute", "m"),
                section.getString("hour", "h"),
                section.getString("day", "d")
        );
    }
}