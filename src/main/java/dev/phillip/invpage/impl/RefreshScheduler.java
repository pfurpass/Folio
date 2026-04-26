package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventorySession;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Owns the BukkitScheduler tasks for session refresh + animation ticking.
 * One task per session at the smallest required tick rate.
 *
 * Performance: when no viewer can currently see the inventory (all viewers offline / not viewing),
 * the task no-ops without rendering, so background sessions don't burn CPU.
 */
public final class RefreshScheduler {

    private final Plugin plugin;
    private final Map<UUID, BukkitTask> tasks = new HashMap<>();
    private final long defaultTicks;

    public RefreshScheduler(Plugin plugin, long defaultTicks) {
        this.plugin = plugin;
        this.defaultTicks = Math.max(1L, defaultTicks);
    }

    public void register(InventorySessionImpl session, long tickInterval) {
        long t = Math.max(1L, tickInterval);
        cancel(session.id());
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (session.isClosed()) {
                cancel(session.id());
                return;
            }
            if (!session.hasActiveViewer()) return;
            session.tick();
        }, t, t);
        tasks.put(session.id(), task);
    }

    /** Register a refresh task at the default rate (typically 1 tick — animation rate). */
    public void registerDefault(InventorySessionImpl session) {
        register(session, defaultTicks);
    }

    public void cancel(UUID sessionId) {
        var t = tasks.remove(sessionId);
        if (t != null && !t.isCancelled()) t.cancel();
    }

    public void shutdown() {
        for (var t : tasks.values()) if (!t.isCancelled()) t.cancel();
        tasks.clear();
    }
}
