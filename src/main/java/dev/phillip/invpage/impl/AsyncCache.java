package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class AsyncCache {

    private record Entry(List<InventoryItem> items, long expiresAt) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public List<InventoryItem> get(String key) {
        var e = store.get(key);
        if (e == null) return null;
        if (System.currentTimeMillis() > e.expiresAt) {
            store.remove(key);
            return null;
        }
        return e.items;
    }

    public void put(String key, List<InventoryItem> items, Duration ttl) {
        if (ttl == null) return;
        store.put(key, new Entry(items, System.currentTimeMillis() + ttl.toMillis()));
    }

    public void invalidate(String key) { store.remove(key); }
    public void clear() { store.clear(); }
}
