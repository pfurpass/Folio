package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * Slot-diffing renderer.
 * Compares the previously rendered ItemStacks with the new ones; only writes changed slots.
 * Avoids the visual flicker of doing inventory.clear() + repopulate.
 */
public final class DiffRenderer {

    private final Map<Integer, ItemStack> lastRendered = new HashMap<>();

    public void render(Inventory inv, Map<Integer, InventoryItem> slotItems, Player viewer) {
        int size = inv.getSize();
        Map<Integer, ItemStack> nextRendered = new HashMap<>();

        for (var entry : slotItems.entrySet()) {
            int slot = entry.getKey();
            if (slot < 0 || slot >= size) continue;
            InventoryItem item = entry.getValue();
            if (!item.visibleFor(viewer)) continue;
            ItemStack rendered = item.render(viewer);
            nextRendered.put(slot, rendered);
            ItemStack prev = lastRendered.get(slot);
            if (prev == null || !prev.isSimilar(rendered) || prev.getAmount() != rendered.getAmount()) {
                inv.setItem(slot, rendered);
            }
        }

        // Clear slots that previously had something and no longer do.
        for (Integer prevSlot : lastRendered.keySet()) {
            if (!nextRendered.containsKey(prevSlot) && prevSlot < size) {
                inv.setItem(prevSlot, null);
            }
        }

        lastRendered.clear();
        lastRendered.putAll(nextRendered);
    }

    public void renderSlot(Inventory inv, int slot, InventoryItem item, Player viewer) {
        if (slot < 0 || slot >= inv.getSize()) return;
        if (!item.visibleFor(viewer)) {
            inv.setItem(slot, null);
            lastRendered.remove(slot);
            return;
        }
        ItemStack rendered = item.render(viewer);
        ItemStack prev = lastRendered.get(slot);
        if (prev == null || !prev.isSimilar(rendered) || prev.getAmount() != rendered.getAmount()) {
            inv.setItem(slot, rendered);
            lastRendered.put(slot, rendered);
        }
    }

    public void invalidate() { lastRendered.clear(); }
}
