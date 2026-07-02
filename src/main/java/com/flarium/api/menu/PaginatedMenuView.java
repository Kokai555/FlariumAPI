package com.flarium.api.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.function.Consumer;

public abstract class PaginatedMenuView<T> extends MenuView {

    private final List<Integer> listSlots;
    private List<T> paginatedItems;
    private int currentPage = 0;

    public PaginatedMenuView(Player player, MenuLayout layout) {
        super(player, layout);
        this.listSlots = layout.listSlots();
    }

    public void setItems(List<T> items) {
        this.paginatedItems = items;
        this.currentPage = 0;
        renderPage();
    }

    public void renderPage() {
        if (paginatedItems == null || listSlots.isEmpty()) return;

        for (Integer slot : listSlots) {
            inventory.setItem(slot, null);
            actions.remove(slot);
        }

        int itemsPerPage = listSlots.size();
        int startIndex = currentPage * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, paginatedItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int slotIndex = i - startIndex;
            int slot = listSlots.get(slotIndex);

            T itemData = paginatedItems.get(i);
            ClickableItem clickable = renderListItem(itemData);

            if (clickable != null) {
                inventory.setItem(slot, clickable.item());
                if (clickable.action() != null) {
                    actions.put(slot, clickable.action());
                }
            }
        }
    }

    protected abstract ClickableItem renderListItem(T itemData);

    protected void nextPage() {
        int maxPages = (int) Math.ceil((double) paginatedItems.size() / listSlots.size());
        if (currentPage < maxPages - 1) {
            currentPage++;
            renderPage();
        }
    }

    protected void previousPage() {
        if (currentPage > 0) {
            currentPage--;
            renderPage();
        }
    }

    public record ClickableItem(ItemStack item, Consumer<InventoryClickEvent> action) {}
}