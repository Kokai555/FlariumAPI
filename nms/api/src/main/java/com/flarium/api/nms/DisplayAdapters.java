package com.flarium.api.nms;

import org.bukkit.Bukkit;
import org.bukkit.World;

import java.lang.reflect.Constructor;
import java.util.LinkedHashMap;
import java.util.Map;

public final class DisplayAdapters {

    private static final Map<String, String> IMPL_PACKAGES = new LinkedHashMap<>();

    static {
        IMPL_PACKAGES.put("1.21.4", "com.flarium.api.nms.v1_21_4");
        IMPL_PACKAGES.put("1.21.5", "com.flarium.api.nms.v1_21_5");
        IMPL_PACKAGES.put("1.21.6", "com.flarium.api.nms.v1_21_6");
        IMPL_PACKAGES.put("1.21.7", "com.flarium.api.nms.v1_21_7");
        IMPL_PACKAGES.put("1.21.8", "com.flarium.api.nms.v1_21_8");
        IMPL_PACKAGES.put("1.21.9", "com.flarium.api.nms.v1_21_9");
        IMPL_PACKAGES.put("1.21.10", "com.flarium.api.nms.v1_21_10");
        IMPL_PACKAGES.put("1.21.11", "com.flarium.api.nms.v1_21_11");
        IMPL_PACKAGES.put("26.1.1", "com.flarium.api.nms.v26_1_1");
        IMPL_PACKAGES.put("26.1.2", "com.flarium.api.nms.v26_1_2");
        IMPL_PACKAGES.put("26.2", "com.flarium.api.nms.v26_2");
    }

    private static String implPackage;

    private DisplayAdapters() {
    }

    public static DisplayAdapter createText(World world) {
        return create(world, true);
    }

    public static DisplayAdapter createItem(World world) {
        return create(world, false);
    }

    private static DisplayAdapter create(World world, boolean text) {
        String className = implPackage() + ".DisplayAdapterImpl";
        try {
            Class<?> implClass = Class.forName(className);
            Constructor<?> constructor = implClass.getDeclaredConstructor(World.class, boolean.class);
            return (DisplayAdapter) constructor.newInstance(world, text);
        } catch (ReflectiveOperationException e) {
            throw new UnsupportedOperationException("Failed to load NMS display adapter " + className, e);
        }
    }

    private static String implPackage() {
        if (implPackage == null) {
            String version = Bukkit.getMinecraftVersion();
            implPackage = IMPL_PACKAGES.get(version);
            if (implPackage == null) {
                String fallback = null;
                for (String supported : IMPL_PACKAGES.keySet()) {
                    if (compareVersions(supported, version) <= 0
                            && (fallback == null || compareVersions(supported, fallback) > 0)) {
                        fallback = supported;
                    }
                }
                if (fallback == null) {
                    throw new UnsupportedOperationException("FlariumAPI holograms are not supported on Minecraft " + version);
                }
                Bukkit.getLogger().warning("[FlariumAPI] No exact NMS adapter for Minecraft " + version
                        + ", falling back to the " + fallback + " adapter.");
                implPackage = IMPL_PACKAGES.get(fallback);
            }
        }
        return implPackage;
    }

    private static int compareVersions(String a, String b) {
        int[] pa = parseVersion(a);
        int[] pb = parseVersion(b);
        for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
            int va = i < pa.length ? pa[i] : 0;
            int vb = i < pb.length ? pb[i] : 0;
            if (va != vb) return Integer.compare(va, vb);
        }
        return 0;
    }

    private static int[] parseVersion(String version) {
        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            int end = 0;
            while (end < parts[i].length() && Character.isDigit(parts[i].charAt(end))) end++;
            if (end == 0) break;
            result[i] = Integer.parseInt(parts[i].substring(0, end));
        }
        return result;
    }
}
