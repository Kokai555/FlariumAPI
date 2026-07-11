package com.flarium.api.ui.menu.gui;

import com.flarium.api.ui.menu.item.Item;

import java.util.ArrayList;
import java.util.List;

public class PaginatedGui extends AbstractGui {

    private final List<Integer> listSlots;
    private List<Item> paginatedItems = new ArrayList<>();
    private int currentPage = 0;

    public PaginatedGui(int rows, List<Integer> listSlots) {
        super(rows * 9);
        this.listSlots = listSlots;
    }

    public void setItems(List<Item> items) {
        this.paginatedItems = items;
        this.currentPage = 0;
        renderPage();
    }

    public void nextPage() {
        int maxPages = (int) Math.ceil((double) paginatedItems.size() / listSlots.size());
        if (currentPage < maxPages - 1) {
            currentPage++;
            renderPage();
        }
    }

    public void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            renderPage();
        }
    }

    private void renderPage() {
        for (int slot : listSlots) {
            setItem(slot, null);
        }

        int itemsPerPage = listSlots.size();
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, paginatedItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            int slot = listSlots.get(slotIndex);
            setItem(slot, paginatedItems.get(i));
        }
    }
}