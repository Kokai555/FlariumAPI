package com.flarium.api.core.util;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SoundUtil {

    public static void playSoundFromString(Player player, String format) {
        if (format == null || !format.startsWith("[SOUND]")) return;

        String parsed = format.substring(8).trim();
        String[] parts = parsed.split("\\|");
        if (parts.length == 0 || parts[0].isEmpty()) return;

        String soundKey = parts[0].replace(".", "_").toLowerCase();
        Sound sound = Registry.SOUNDS.get(NamespacedKey.minecraft(soundKey));

        if (sound == null) {
            Bukkit.getLogger().warning("Unknown sound in config: " + soundKey);
            return;
        }

        float volume = parts.length > 1 ? parseFloatSafe(parts[1], 1.0f) : 1.0f;
        float pitch = parts.length > 2 ? parseFloatSafe(parts[2], 1.0f) : 1.0f;

        playSound(player, sound, volume, pitch);
    }

    public static void playSound(Player player, Sound sound, float volume, float pitch) {
        player.playSound(player.getLocation(), sound, volume, pitch);
    }

    public static void playSoundAtLocation(Location location, Sound sound, float volume, float pitch) {
        if (location.getWorld() == null) return;
        location.getWorld().playSound(location, sound, volume, pitch);
    }

    public static void stopSound(Player player, Sound sound) {
        player.stopSound(sound);
    }

    private static float parseFloatSafe(String str, float defaultValue) {
        try {
            return Float.parseFloat(str);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}