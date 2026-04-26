package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryRegistry;
import dev.phillip.invpage.api.InventorySession;
import dev.phillip.invpage.api.PagedInventory;
import dev.phillip.invpage.api.SessionMode;
import dev.phillip.invpage.api.theme.InventoryTheme;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class InventoryRegistryImpl implements InventoryRegistry {

    private final Plugin plugin;
    private final RefreshScheduler scheduler;
    private final AsyncCache asyncCache = new AsyncCache();
    private final CooldownTracker cooldowns = new CooldownTracker();

    /** playerId → session */
    private final Map<UUID, InventorySessionImpl> activeByPlayer = new HashMap<>();
    /** sessionId → session (relevant for SHARED) */
    private final Map<UUID, InventorySessionImpl> sessionsById = new HashMap<>();
    /** Named registry: persistent-key → factory */
    private final Map<String, Supplier<PagedInventory>> namedRegistry = new ConcurrentHashMap<>();

    public InventoryRegistryImpl(Plugin plugin) {
        this.plugin = plugin;
        this.scheduler = new RefreshScheduler(plugin, 1L);
    }

    @Override
    public InventorySession open(Player player, PagedInventory paged) {
        var template = paged.template();
        if (!template.permissionGate().test(player)) {
            playSound(player, template.theme().deniedSound());
            return null;
        }
        close(player); // close any prior session

        InventorySessionImpl session;
        if (template.sessionMode() == SessionMode.SHARED) {
            // Find existing shared session for same paged template (by template identity).
            session = sessionsById.values().stream()
                    .filter(s -> s.mode() == SessionMode.SHARED && s.paged() == paged)
                    .findFirst().orElse(null);
            if (session == null) {
                session = createSession(paged);
                createInventoryFor(session, player);
            }
        } else {
            session = createSession(paged);
            createInventoryFor(session, player);
        }

        attachViewer(session, player);
        return session;
    }

    @Override
    public InventorySession join(Player player, UUID sharedSessionId) {
        var session = sessionsById.get(sharedSessionId);
        if (session == null || session.mode() != SessionMode.SHARED) return null;
        attachViewer(session, player);
        return session;
    }

    @Override
    public void close(Player player) {
        var session = activeByPlayer.remove(player.getUniqueId());
        if (session == null) return;
        session.removeViewer(player.getUniqueId());
        if (session.viewers().isEmpty()) {
            session.markClosed();
            scheduler.cancel(session.id());
            sessionsById.remove(session.id());
        }
        var inv = session.inventoryFor(player);
        if (inv != null && player.getOpenInventory().getTopInventory() == inv) {
            player.closeInventory();
        }
    }

    @Override
    public @Nullable InventorySession sessionOf(Player player) {
        return activeByPlayer.get(player.getUniqueId());
    }

    @Override
    public @Nullable InventorySession sharedSession(UUID id) {
        var s = sessionsById.get(id);
        return (s != null && s.mode() == SessionMode.SHARED) ? s : null;
    }

    /** Called by listener on InventoryCloseEvent. */
    public void handleClose(Player player) {
        close(player);
    }

    public InventorySessionImpl byPlayer(Player player) {
        return activeByPlayer.get(player.getUniqueId());
    }

    public CooldownTracker cooldowns() { return cooldowns; }
    public AsyncCache asyncCache() { return asyncCache; }
    public Plugin plugin() { return plugin; }

    private InventorySessionImpl createSession(PagedInventory paged) {
        var session = new InventorySessionImpl(UUID.randomUUID(), paged.template().sessionMode(), paged, plugin);
        sessionsById.put(session.id(), session);
        if (paged.template().asyncSpec() != null) session.startAsyncLoad(asyncCache);
        scheduler.registerDefault(session);
        return session;
    }

    private void createInventoryFor(InventorySessionImpl session, Player viewer) {
        var page = session.paged().template();
        Inventory inv = Bukkit.createInventory(null, page.rows() * 9, page.title());
        session.registerInventory(viewer, inv);
    }

    private void attachViewer(InventorySessionImpl session, Player viewer) {
        if (session.mode() == SessionMode.SHARED) {
            // Shared: ensure inventory exists
            if (session.inventoryFor(viewer) == null) {
                createInventoryFor(session, viewer);
            } else {
                session.registerInventory(viewer, session.inventoryFor(viewer));
            }
        }
        session.renderInitial(viewer);
        viewer.openInventory(session.inventoryFor(viewer));
        playSound(viewer, session.paged().template().theme().openSound());
        activeByPlayer.put(viewer.getUniqueId(), session);
    }

    private void playSound(Player p, org.bukkit.Sound s) {
        if (s == null) return;
        p.playSound(p.getLocation(), s, 0.6f, 1.0f);
    }

    @Override
    public void registerNamed(String key, Supplier<PagedInventory> factory) {
        namedRegistry.put(key, factory);
    }

    @Override
    public @Nullable InventorySession openNamed(Player player, String key) {
        var f = namedRegistry.get(key);
        if (f == null) return null;
        return open(player, f.get());
    }

    public Supplier<PagedInventory> namedFactory(String key) { return namedRegistry.get(key); }

    public void shutdown() {
        scheduler.shutdown();
        for (var s : sessionsById.values()) s.markClosed();
        sessionsById.clear();
        activeByPlayer.clear();
        asyncCache.clear();
    }
}
