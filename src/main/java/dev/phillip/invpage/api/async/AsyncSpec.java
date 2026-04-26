package dev.phillip.invpage.api.async;

import dev.phillip.invpage.api.InventoryItem;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;

/** Configuration for an async item source: placeholder, error fallback, cache TTL. */
public record AsyncSpec(
        AsyncItemSource source,
        @Nullable InventoryItem placeholder,
        @Nullable InventoryItem errorItem,
        @Nullable Duration cacheTtl,
        String cacheKey
) {
    public static Builder of(AsyncItemSource source) { return new Builder(source); }

    public static final class Builder {
        private final AsyncItemSource source;
        private InventoryItem placeholder;
        private InventoryItem errorItem;
        private Duration cacheTtl;
        private String cacheKey;
        Builder(AsyncItemSource source) { this.source = source; }

        public Builder placeholder(InventoryItem item) { this.placeholder = item; return this; }
        public Builder errorItem(InventoryItem item) { this.errorItem = item; return this; }
        public Builder cache(Duration ttl) { this.cacheTtl = ttl; return this; }
        public Builder cacheKey(String key) { this.cacheKey = key; return this; }

        public AsyncSpec build() {
            return new AsyncSpec(source, placeholder, errorItem, cacheTtl,
                    cacheKey != null ? cacheKey : ("anon-" + System.identityHashCode(source)));
        }
    }
}
