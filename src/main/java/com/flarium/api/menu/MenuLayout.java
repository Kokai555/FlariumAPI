package com.flarium.api.menu;

import com.flarium.api.item.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record MenuLayout(
        int size,
        Component title,
        Map<Integer, LayoutItem> items,
        List<Integer> listSlots
) {
    public record LayoutItem(ItemStack item, String actionName) {}

    public static MenuLayout fromConfig(ConfigurationSection section, Plugin plugin) {
        if (section == null) throw new IllegalArgumentException("Menu configuration cannot be null!");

        int size = section.getInt("size", 27);
        Component title = MiniMessage.miniMessage().deserialize(section.getString("title", "<white>Menu</white>"));

        Map<Integer, LayoutItem> items = new HashMap<>();
        Map<Character, LayoutItem> itemDefinitions = new HashMap<>();

        List<Integer> parsedListSlots = new ArrayList<>();
        for (String s : section.getStringList("list-slots")) {
            try {
                parsedListSlots.add(Integer.parseInt(s));
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Invalid list-slot in menu config: '" + s + "' is not a valid number.");
            }
        }

        if (parsedListSlots.isEmpty()) {
            List<String> pattern = section.getStringList("pattern");
            for (int row = 0; row < pattern.size(); row++) {
                String line = pattern.get(row);
                for (int col = 0; col < line.length(); col++) {
                    if (line.charAt(col) == 'x' || line.charAt(col) == 'X') {
                        parsedListSlots.add(row * 9 + col);
                    }
                }
            }
        }

        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                char charKey = key.charAt(0);
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) continue;

                ItemStack item = ItemBuilder.fromConfig(itemSection);
                String actionName = itemSection.getString("action", null);
                itemDefinitions.put(charKey, new LayoutItem(item, actionName));
            }
        }

        List<String> pattern = section.getStringList("pattern");
        for (int row = 0; row < pattern.size(); row++) {
            String line = pattern.get(row);
            for (int col = 0; col < line.length(); col++) {
                char c = line.charAt(col);
                if (c == ' ' || c == '.' || c == 'x' || c == 'X') continue;

                LayoutItem def = itemDefinitions.get(c);
                if (def != null) {
                    int slot = row * 9 + col;
                    items.put(slot, def);
                }
            }
        }

        return new MenuLayout(size, title, items, parsedListSlots);
    }
}