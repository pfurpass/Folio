package dev.phillip.invpage.impl;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Saves the (persistent-key, currentPage) pair to the player's PDC when a persistent page is closed,
 * and restores it on the next PlayerJoinEvent.
 */
public final class SessionPersistence implements Listener {

    private final Plugin plugin;
    private final InventoryRegistryImpl registry;
    private final NamespacedKey keyName;
    private final NamespacedKey pageIndex;

    public SessionPersistence(Plugin plugin, InventoryRegistryImpl registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.keyName = new NamespacedKey(plugin, "page_key");
        this.pageIndex = new NamespacedKey(plugin, "page_index");
    }

    /** Called by the registry when a persistent page is closed. */
    public void save(Player player, String key, int currentPage) {
        var pdc = player.getPersistentDataContainer();
        pdc.set(keyName, PersistentDataType.STRING, key);
        pdc.set(pageIndex, PersistentDataType.INTEGER, currentPage);
    }

    /** Clear stored persistence (e.g. after explicit user "exit"). */
    public void clear(Player player) {
        var pdc = player.getPersistentDataContainer();
        pdc.remove(keyName);
        pdc.remove(pageIndex);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        var pdc = p.getPersistentDataContainer();
        if (!pdc.has(keyName, PersistentDataType.STRING)) return;
        String key = pdc.get(keyName, PersistentDataType.STRING);
        Integer page = pdc.get(pageIndex, PersistentDataType.INTEGER);
        if (key == null) return;
        // Defer 1 tick so player is fully connected.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            var session = registry.openNamed(p, key);
            if (session != null && page != null && page > 0) {
                session.switchPage(page);
            }
        }, 20L);
    }
}
