package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.click.CooldownPolicy;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-item cooldown tracking. Keyed by (itemId, scope-key).
 * For PER_PLAYER scope, scope-key = playerUuid; for GLOBAL, scope-key = "GLOBAL".
 */
public final class CooldownTracker {

    private record Key(UUID itemId, String scopeKey) {}

    private final Map<Key, Long> readyAt = new HashMap<>();

    public boolean tryConsume(UUID itemId, UUID playerId, CooldownPolicy policy) {
        if (policy == null) return true;
        String scope = policy.scope() == CooldownPolicy.Scope.GLOBAL ? "GLOBAL" : playerId.toString();
        var key = new Key(itemId, scope);
        long now = System.currentTimeMillis();
        Long until = readyAt.get(key);
        if (until != null && now < until) return false;
        readyAt.put(key, now + policy.millis());
        return true;
    }

    public long remainingMillis(UUID itemId, UUID playerId, CooldownPolicy policy) {
        if (policy == null) return 0;
        String scope = policy.scope() == CooldownPolicy.Scope.GLOBAL ? "GLOBAL" : playerId.toString();
        Long until = readyAt.get(new Key(itemId, scope));
        if (until == null) return 0;
        return Math.max(0, until - System.currentTimeMillis());
    }
}
