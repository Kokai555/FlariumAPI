package com.flarium.api.ui.menu;

import com.flarium.api.ui.item.ItemBuilder;
import com.flarium.api.ui.menu.event.ItemClickEvent;
import com.flarium.api.ui.menu.gui.NormalGui;
import com.flarium.api.ui.menu.item.Item;
import com.flarium.api.ui.menu.structure.Ingredient;
import com.flarium.api.ui.menu.structure.Structure;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class ConfigMenuFactory {

    public static NormalGui createGui(ConfigurationSection section, Map<String, Consumer<ItemClickEvent>> actions) {
        if (section == null) throw new IllegalArgumentException("Menu configuration cannot be null!");

        int size = section.getInt("size", 27);
        NormalGui gui = new NormalGui(size / 9);

        Structure structure = new Structure(section.getStringList("pattern").toArray(new String[0]));
        Map<Character, Ingredient> ingredients = new HashMap<>();

        ConfigurationSection itemsSection = section.getConfigurationSection("items");
        if (itemsSection != null) {
            for (String key : itemsSection.getKeys(false)) {
                char charKey = key.charAt(0);
                ConfigurationSection itemSection = itemsSection.getConfigurationSection(key);
                if (itemSection == null) continue;

                ItemStack itemStack = ItemBuilder.fromConfig(itemSection);
                String actionName = itemSection.getString("action", null);

                Consumer<ItemClickEvent> action = null;
                if (actionName != null) {
                    if (actions != null && actions.containsKey(actionName.toUpperCase())) {
                        action = actions.get(actionName.toUpperCase());
                    } else {
                        Bukkit.getLogger().warning("[FlariumAPI] Warning: Unknown action key in menu config: " + actionName);
                    }
                }

                Item menuItem = new ConfigurableItem(itemStack, action);
                ingredients.put(charKey, () -> menuItem);
            }
        }

        gui.applyStructure(structure, ingredients);
        return gui;
    }
}