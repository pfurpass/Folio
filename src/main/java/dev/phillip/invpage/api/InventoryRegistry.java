package dev.phillip.invpage.api;

import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

public interface InventoryRegistry {

    /** Open the paged inventory for the player. */
    InventorySession open(Player player, PagedInventory paged);

    /** Open and explicitly join an existing shared session. */
    InventorySession join(Player player, UUID sharedSessionId);

    /** Close the player's currently-tracked session. */
    void close(Player player);

    /** Currently active session for player, or null. */
    @Nullable InventorySession sessionOf(Player player);

    /** Lookup a known shared session. */
    @Nullable InventorySession sharedSession(UUID id);

    /** Register a named PagedInventory factory — required for persistence-restore on rejoin. */
    void registerNamed(String key, Supplier<PagedInventory> factory);

    /** Open a previously-registered named PagedInventory. Returns null if no such key. */
    @Nullable InventorySession openNamed(Player player, String key);
}
