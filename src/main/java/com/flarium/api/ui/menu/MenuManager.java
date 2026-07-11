package com.flarium.api.ui.menu;

import com.flarium.api.core.scheduler.Scheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MenuManager {

    private final JavaPlugin plugin;
    private final Scheduler scheduler;
    private final Map<String, MenuLayout> layouts = new HashMap<>();
    private final Set<MenuView> activeMenus = ConcurrentHashMap.newKeySet();

    public MenuManager(JavaPlugin plugin, Scheduler scheduler) {
        this.plugin = plugin;
        this.scheduler = scheduler;

        // Közös tick task 1 másodpercenként (20 tick)
        scheduler.runGlobalTimer(this::tickMenus, Duration.ofSeconds(1), Duration.ofSeconds(1));
    }

    private void tickMenus() {
        for (MenuView view : activeMenus) {
            // Ellenőrizzük, hogy a játékos még online van-e és a menü még nyitva van-e
            if (!view.getPlayer().isOnline() || view.getPlayer().getOpenInventory().getTopInventory().getHolder() != view) {
                activeMenus.remove(view);
                continue;
            }
            view.tick();
        }
    }

    public void registerActiveMenu(MenuView view) {
        activeMenus.add(view);
    }

    public void unregisterActiveMenu(MenuView view) {
        activeMenus.remove(view);
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