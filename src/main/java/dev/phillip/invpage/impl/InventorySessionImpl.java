package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.InventoryPage;
import dev.phillip.invpage.api.InventorySession;
import dev.phillip.invpage.api.PagedInventory;
import dev.phillip.invpage.api.SessionMode;
import dev.phillip.invpage.api.search.Filter;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

public final class InventorySessionImpl implements InventorySession {

    private final UUID id;
    private final SessionMode mode;
    private final PagedInventory paged;
    private final Plugin plugin;
    private final Map<UUID, Inventory> inventories;
    private final Map<UUID, DiffRenderer> renderers;
    private final List<UUID> viewerOrder = new ArrayList<>();

    private int currentPage = 0;
    private Filter filter;
    private boolean closed;

    /** Async-loaded items (populated when an AsyncSpec resolves successfully). null = not loaded yet. */
    private List<InventoryItem> asyncItems;
    private boolean asyncLoading;
    private boolean asyncFailed;
    private CompletableFuture<List<InventoryItem>> asyncFuture;
    private final AtomicBoolean asyncCancelled = new AtomicBoolean(false);

    public InventorySessionImpl(UUID id, SessionMode mode, PagedInventory paged, Plugin plugin) {
        this.id = id;
        this.mode = mode;
        this.paged = paged;
        this.plugin = plugin;
        this.inventories = new HashMap<>();
        this.renderers = new HashMap<>();
    }

    @Override public UUID id() { return id; }
    @Override public SessionMode mode() { return mode; }
    @Override public PagedInventory paged() { return paged; }
    @Override public int currentPage() { return currentPage; }
    @Override public Collection<Player> viewers() {
        var out = new ArrayList<Player>();
        for (var u : viewerOrder) {
            var p = Bukkit.getPlayer(u);
            if (p != null) out.add(p);
        }
        return out;
    }

    @Override
    public Inventory inventoryFor(Player viewer) {
        if (mode == SessionMode.SHARED) {
            var any = inventories.values().stream().findFirst().orElse(null);
            if (any != null) return any;
        }
        return inventories.get(viewer.getUniqueId());
    }

    public void registerInventory(Player viewer, Inventory inv) {
        UUID key = mode == SessionMode.SHARED ? id : viewer.getUniqueId();
        inventories.put(key, inv);
        renderers.computeIfAbsent(key, k -> new DiffRenderer());
        if (!viewerOrder.contains(viewer.getUniqueId())) viewerOrder.add(viewer.getUniqueId());
    }

    public void removeViewer(UUID playerId) {
        viewerOrder.remove(playerId);
        if (mode == SessionMode.PER_PLAYER) {
            inventories.remove(playerId);
            renderers.remove(playerId);
        }
    }

    public boolean hasActiveViewer() {
        for (var u : viewerOrder) {
            var p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) return true;
        }
        return false;
    }

    public boolean isClosed() { return closed; }

    public void markClosed() {
        closed = true;
        cancelAsync();
    }

    public void renderInitial(Player viewer) {
        UUID key = mode == SessionMode.SHARED ? id : viewer.getUniqueId();
        var inv = inventories.get(key);
        if (inv == null) return;
        var r = renderers.get(key);
        var slotMap = SlotMap.compute(paged, currentPage, asyncItems, filter, viewer, asyncLoading, asyncFailed);
        r.render(inv, slotMap.asMap(), viewer);
    }

    public void tick() {
        // Re-render every viewer (cheap thanks to diffing).
        for (var p : viewers()) renderInitial(p);
    }

    @Override
    public void switchPage(int targetIndex) {
        int max = effectiveTotalPages();
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex >= max) targetIndex = max - 1;
        this.currentPage = targetIndex;
        for (var p : viewers()) renderInitial(p);
    }

    @Override
    public void refresh() {
        for (var p : viewers()) renderInitial(p);
    }

    @Override
    public void refreshSlot(int slot) {
        for (var p : viewers()) {
            UUID key = mode == SessionMode.SHARED ? id : p.getUniqueId();
            var inv = inventories.get(key);
            var r = renderers.get(key);
            if (inv == null || r == null) continue;
            var slotMap = SlotMap.compute(paged, currentPage, asyncItems, filter, p, asyncLoading, asyncFailed);
            var item = slotMap.get(slot);
            if (item != null) r.renderSlot(inv, slot, item, p);
        }
    }

    @Override public void close() { closed = true; }

    @Override public Filter filter() { return filter; }
    @Override public void setFilter(Filter f) {
        this.filter = f;
        this.currentPage = 0;
        refresh();
    }

    public int effectiveTotalPages() {
        if (!paged.dynamic()) return paged.pageCount();
        InventoryPage tpl = paged.template();
        var src = asyncItems != null ? asyncItems : tpl.items();
        Filter f = filter != null ? filter : (tpl.filter() != null ? tpl.filter() : Filter.PASS);
        // Use first viewer for filter context (filters may take player into account; default is permissive).
        Player anyViewer = viewers().stream().findFirst().orElse(null);
        long count = src.stream()
                .filter(i -> anyViewer == null || i.visibleFor(anyViewer))
                .filter(i -> anyViewer == null || f.test(i, anyViewer))
                .count();
        int perPage = tpl.layout().itemSlots(tpl.rows()).size();
        if (perPage <= 0) return 1;
        return Math.max(1, (int) ((count + perPage - 1) / perPage));
    }

    // -- Async support --

    public void startAsyncLoad(AsyncCache cache) {
        var spec = paged.template().asyncSpec();
        if (spec == null) return;
        // Cache hit?
        if (spec.cacheTtl() != null) {
            var cached = cache.get(spec.cacheKey());
            if (cached != null) { this.asyncItems = cached; return; }
        }
        asyncLoading = true;
        asyncCancelled.set(false);
        asyncFuture = spec.source().get();
        asyncFuture.whenComplete((items, err) -> {
            if (asyncCancelled.get() || closed) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (closed) return;
                if (err != null || items == null) {
                    asyncLoading = false;
                    asyncFailed = true;
                } else {
                    asyncItems = items;
                    asyncLoading = false;
                    asyncFailed = false;
                    if (spec.cacheTtl() != null) cache.put(spec.cacheKey(), items, spec.cacheTtl());
                }
                refresh();
            });
        });
    }

    public void cancelAsync() {
        asyncCancelled.set(true);
        if (asyncFuture != null && !asyncFuture.isDone()) {
            // soft discard — Future.cancel(true) interrupts threads which is unsafe for DB calls
            // Caller-owned future lifecycle.
        }
    }

    public boolean isAsyncLoading() { return asyncLoading; }
    public boolean isAsyncFailed() { return asyncFailed; }
    public List<InventoryItem> asyncItems() { return asyncItems; }
}
