package dev.phillip.invpage.api.nav;

import dev.phillip.invpage.api.InventoryItem;
import org.jetbrains.annotations.Nullable;

import java.util.function.IntFunction;

/**
 * Slot positions for navigation. -1 disables a button.
 * pageIndicatorBuilder receives (currentPage, totalPages) — kept simple via two ints folded into a long? No, use record below.
 */
public record NavigationButtons(
        int firstSlot,
        int previousSlot,
        int nextSlot,
        int lastSlot,
        int pageIndicatorSlot,
        @Nullable IntFunction<InventoryItem> firstButton,
        @Nullable IntFunction<InventoryItem> previousButton,
        @Nullable IntFunction<InventoryItem> nextButton,
        @Nullable IntFunction<InventoryItem> lastButton
) {
    public static NavigationButtons disabled() {
        return new NavigationButtons(-1, -1, -1, -1, -1, null, null, null, null);
    }

    /** Standard nav for a 6-row chest: bottom row, slots 45..53. */
    public static NavigationButtons standard() {
        return new NavigationButtons(45, 47, 51, 53, 49, null, null, null, null);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int first = -1, prev = -1, next = -1, last = -1, indicator = -1;
        private IntFunction<InventoryItem> firstB, prevB, nextB, lastB;
        public Builder firstSlot(int s) { this.first = s; return this; }
        public Builder previousSlot(int s) { this.prev = s; return this; }
        public Builder nextSlot(int s) { this.next = s; return this; }
        public Builder lastSlot(int s) { this.last = s; return this; }
        public Builder pageIndicatorSlot(int s) { this.indicator = s; return this; }
        public Builder firstButton(IntFunction<InventoryItem> fn) { this.firstB = fn; return this; }
        public Builder previousButton(IntFunction<InventoryItem> fn) { this.prevB = fn; return this; }
        public Builder nextButton(IntFunction<InventoryItem> fn) { this.nextB = fn; return this; }
        public Builder lastButton(IntFunction<InventoryItem> fn) { this.lastB = fn; return this; }
        public NavigationButtons build() {
            return new NavigationButtons(first, prev, next, last, indicator, firstB, prevB, nextB, lastB);
        }
    }
}
