package dev.phillip.invpage.api;

import dev.phillip.invpage.api.search.Filter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;

/**
 * Per-viewer (or shared) session state for an open paged inventory.
 * - Shared sessions hold one underlying Inventory; multiple players view it.
 * - Per-player sessions hold one Inventory per player.
 */
public interface InventorySession {

    UUID id();
    SessionMode mode();
    PagedInventory paged();
    int currentPage();
    Collection<Player> viewers();

    /** The Bukkit Inventory shown to this viewer (in shared mode this is the same instance for all). */
    Inventory inventoryFor(Player viewer);

    void switchPage(int targetIndex);
    void refresh();
    void refreshSlot(int slot);
    void close();

    /** Filter currently active on this session. */
    Filter filter();
    void setFilter(Filter filter);
}
