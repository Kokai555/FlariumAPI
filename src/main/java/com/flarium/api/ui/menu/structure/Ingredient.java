package com.flarium.api.ui.menu.structure;

import com.flarium.api.ui.menu.item.Item;

@FunctionalInterface
public interface Ingredient {
    Item getItem();
}