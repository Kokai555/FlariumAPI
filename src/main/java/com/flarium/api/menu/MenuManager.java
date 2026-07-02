package com.flarium.api.menu;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class MenuManager {

    private final JavaPlugin plugin;
    private final Map<String, MenuLayout> layouts = new HashMap<>();

    public MenuManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadMenus() {
        layouts.clear();
        File menusFolder = new File(plugin.getDataFolder(), "menus");
        if (!menusFolder.exists()) {
            menusFolder.mkdirs();
            plugin.saveResource("menus/main.yml", false);
        }

        File[] files = menusFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".yml", "").toLowerCase();
            try {
                YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
                MenuLayout layout = MenuLayout.fromConfig(config, plugin);
                layouts.put(name, layout);
                plugin.getLogger().info("Menu loaded: " + name);
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load menu '" + file.getName() + "': " + e.getMessage());
            }
        }
    }

    public void reloadMenus() {
        loadMenus();
    }

    public MenuLayout getLayout(String name) {
        return layouts.get(name.toLowerCase());
    }
}