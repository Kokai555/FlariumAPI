package com.flarium.api.ui.menu.gui;

import com.flarium.api.ui.menu.item.Item;

import java.util.ArrayList;
import java.util.Collections;
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
        this.paginatedItems = items != null ? items : new ArrayList<>();
        if (this.currentPage >= this.getMaxPages()) {
            this.currentPage = Math.max(0, this.getMaxPages() - 1);
        }
        renderPage();
    }

    public int getMaxPages() {
        if (listSlots.isEmpty()) return 1;
        return (int) Math.max(1, Math.ceil((double) paginatedItems.size() / listSlots.size()));
    }

    public void nextPage() {
        if (currentPage < getMaxPages() - 1) {
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