package com.flarium.api.core.util;

public class TimeUtil {

    public static String formatDuration(long seconds, TimeFormat format) {
        if (seconds < 0) return "0" + format.second();

        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder builder = new StringBuilder();

        if (days > 0) builder.append(days).append(format.day()).append(" ");
        if (hours > 0) builder.append(hours).append(format.hour()).append(" ");
        if (minutes > 0) builder.append(minutes).append(format.minute()).append(" ");
        if (secs > 0 || builder.length() == 0) builder.append(secs).append(format.second());

        return builder.toString().trim();
    }
}