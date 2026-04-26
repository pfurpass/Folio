package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.InventoryPage;
import dev.phillip.invpage.api.PagedInventory;
import dev.phillip.invpage.api.layout.Layout;
import dev.phillip.invpage.api.nav.NavigationButtons;
import dev.phillip.invpage.api.search.Filter;
import dev.phillip.invpage.api.theme.InventoryTheme;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Computes the (slot → InventoryItem) projection for one viewer of one page.
 * Encapsulates: layout slots, fixed slots, navigation, page indicator, async placeholders,
 * filter pipeline, permission visibility filter.
 */
public final class SlotMap {

    private final Map<Integer, InventoryItem> slots;

    private SlotMap(Map<Integer, InventoryItem> slots) { this.slots = slots; }

    public Map<Integer, InventoryItem> asMap() { return slots; }
    public @Nullable InventoryItem get(int slot) { return slots.get(slot); }

    public static SlotMap compute(
            PagedInventory paged,
            int currentPage,
            @Nullable List<InventoryItem> overrideItems,
            Filter sessionFilter,
            Player viewer,
            boolean asyncLoading,
            boolean asyncFailed
    ) {
        InventoryPage tpl = paged.template();
        InventoryPage page = paged.page(Math.min(currentPage, paged.pageCount() - 1));
        Layout layout = page.layout();
        int rows = page.rows();
        var theme = page.theme();

        Map<Integer, InventoryItem> map = new HashMap<>();

        // 1. Border / reserved → theme border
        for (int s : layout.reservedSlots(rows)) {
            map.put(s, themeBorder(theme));
        }

        // 2. Item slots — auto-paginate the source items list
        var sourceItems = overrideItems != null ? overrideItems : page.items();
        Filter f = sessionFilter != null ? sessionFilter : (page.filter() != null ? page.filter() : Filter.PASS);
        var visible = sourceItems.stream()
                .filter(i -> i.visibleFor(viewer))
                .filter(i -> f.test(i, viewer))
                .toList();

        var itemSlots = layout.itemSlots(rows);
        int perPage = itemSlots.size();
        int totalPages;
        if (paged.dynamic()) {
            totalPages = perPage <= 0 ? 1 : Math.max(1, (visible.size() + perPage - 1) / perPage);
        } else {
            totalPages = paged.pageCount();
        }
        int idx = paged.dynamic() ? currentPage * perPage : 0;

        if (asyncLoading && page.asyncSpec() != null) {
            var ph = page.asyncSpec().placeholder();
            if (ph != null) {
                for (int s : itemSlots) map.put(s, ph);
            }
        } else if (asyncFailed && page.asyncSpec() != null && page.asyncSpec().errorItem() != null) {
            var err = page.asyncSpec().errorItem();
            for (int s : itemSlots) map.put(s, err);
        } else if (paged.dynamic()) {
            for (int slotI = 0; slotI < itemSlots.size() && idx < visible.size(); slotI++, idx++) {
                map.put(itemSlots.get(slotI), visible.get(idx));
            }
            if (visible.isEmpty()) {
                int center = itemSlots.isEmpty() ? rows * 9 / 2 : itemSlots.get(itemSlots.size() / 2);
                map.put(center, emptyResultItem(viewer));
            }
        } else {
            // Manual page mode → use the page's own fixed mapping for the item area.
            int slotI = 0;
            for (var item : sourceItems) {
                if (slotI >= itemSlots.size()) break;
                map.put(itemSlots.get(slotI++), item);
            }
        }

        // 3. Fixed slots (page-defined) override
        map.putAll(page.fixedSlots());

        // 4. Navigation
        var nav = page.navigation();
        applyNavigation(map, nav, currentPage, totalPages, theme, viewer);

        return new SlotMap(map);
    }

    private static InventoryItem themeBorder(InventoryTheme theme) {
        var stack = new ItemStack(theme.borderMaterial());
        var meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(theme.borderName().decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return dev.phillip.invpage.api.InventoryItem.of(stack);
    }

    private static InventoryItem emptyResultItem(Player viewer) {
        var stack = new ItemStack(Material.BARRIER);
        var meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(dev.phillip.invpage.i18n.Messages.text(viewer, "result.empty", NamedTextColor.RED));
            stack.setItemMeta(meta);
        }
        return dev.phillip.invpage.api.InventoryItem.of(stack);
    }

    private static void applyNavigation(Map<Integer, InventoryItem> map, NavigationButtons nav,
                                        int current, int total, InventoryTheme theme, Player viewer) {
        if (nav == null) return;
        if (nav.firstSlot() >= 0 && current > 0) {
            map.put(nav.firstSlot(), navItem(nav.firstButton(), 0,
                    dev.phillip.invpage.i18n.Messages.get(viewer, "nav.first"), Material.SPECTRAL_ARROW));
        }
        if (nav.previousSlot() >= 0 && current > 0) {
            map.put(nav.previousSlot(), navItem(nav.previousButton(), current - 1,
                    dev.phillip.invpage.i18n.Messages.get(viewer, "nav.previous"), Material.ARROW));
        }
        if (nav.nextSlot() >= 0 && current < total - 1) {
            map.put(nav.nextSlot(), navItem(nav.nextButton(), current + 1,
                    dev.phillip.invpage.i18n.Messages.get(viewer, "nav.next"), Material.ARROW));
        }
        if (nav.lastSlot() >= 0 && current < total - 1) {
            map.put(nav.lastSlot(), navItem(nav.lastButton(), total - 1,
                    dev.phillip.invpage.i18n.Messages.get(viewer, "nav.last"), Material.SPECTRAL_ARROW));
        }
        if (nav.pageIndicatorSlot() >= 0) {
            map.put(nav.pageIndicatorSlot(), pageIndicator(theme, current + 1, total));
        }
    }

    private static InventoryItem navItem(@Nullable java.util.function.IntFunction<InventoryItem> custom,
                                         int target, String defaultLabel, Material defaultMat) {
        if (custom != null) {
            var candidate = custom.apply(target);
            if (candidate != null) return candidate;
        }
        var stack = new ItemStack(defaultMat);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(defaultLabel, NamedTextColor.YELLOW)
                    .decoration(TextDecoration.ITALIC, false));
            stack.setItemMeta(meta);
        }
        return dev.phillip.invpage.api.InventoryItem.builder()
                .stack(stack)
                .onClick(ctx -> dev.phillip.invpage.api.click.ClickResult.navigate(target))
                .build();
    }

    private static InventoryItem pageIndicator(InventoryTheme theme, int current, int total) {
        var stack = new ItemStack(Material.PAPER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(theme.renderPageIndicator(current, total));
            stack.setItemMeta(meta);
        }
        return dev.phillip.invpage.api.InventoryItem.of(stack);
    }
}
