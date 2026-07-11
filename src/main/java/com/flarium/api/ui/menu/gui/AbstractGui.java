package com.flarium.api.ui.menu.gui;

import com.flarium.api.ui.menu.item.Item;
import com.flarium.api.ui.menu.structure.Ingredient;
import com.flarium.api.ui.menu.structure.Structure;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public abstract class AbstractGui implements Gui {

    private final int size;
    private final Item[] items;
    private final List<GuiModifier> modifiers = new ArrayList<>();

    public AbstractGui(int size) {
        this.size = size;
        this.items = new Item[size];
    }

    @Override
    public void setItem(int slot, Item item) {
        if (slot >= 0 && slot < size) {
            items[slot] = item;
        }
    }

    @Override
    public Item getItem(int slot) {
        if (slot >= 0 && slot < size) {
            return items[slot];
        }
        return null;
    }

    @Override
    public ItemStack[] getContent() {
        ItemStack[] content = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            if (items[i] != null) {
                content[i] = items[i].getItemProvider();
            }
        }
        return content;
    }

    @Override
    public int getSize() { return size; }

    @Override
    public void addModifier(GuiModifier modifier) { modifiers.add(modifier); }

    @Override
    public List<GuiModifier> getModifiers() { return modifiers; }

    @Override
    public void tick() {}

    public void applyStructure(Structure structure, Map<Character, Ingredient> ingredients) {
        for (Map.Entry<Character, Ingredient> entry : ingredients.entrySet()) {
            char c = entry.getKey();
            Ingredient ingredient = entry.getValue();

            for (int slot : structure.getSlots(c)) {
                if (slot < size) {
                    items[slot] = ingredient.getItem();
                }
            }
        }
    }
}