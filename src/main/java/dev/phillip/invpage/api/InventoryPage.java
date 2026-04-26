package dev.phillip.invpage.api;

import dev.phillip.invpage.api.async.AsyncSpec;
import dev.phillip.invpage.api.click.ClickHandler;
import dev.phillip.invpage.api.gate.PermissionGate;
import dev.phillip.invpage.api.layout.Layout;
import dev.phillip.invpage.api.nav.NavigationButtons;
import dev.phillip.invpage.api.search.Filter;
import dev.phillip.invpage.api.theme.InventoryTheme;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface InventoryPage {

    Component title();
    int rows();
    Layout layout();
    InventoryTheme theme();
    NavigationButtons navigation();
    PermissionGate permissionGate();

    /** Items the layout will paginate through. */
    List<InventoryItem> items();

    /** Fixed slot overrides — e.g. nav buttons, header items. */
    Map<Integer, InventoryItem> fixedSlots();

    @Nullable ClickHandler globalClickHandler();
    @Nullable Duration refreshInterval();

    @Nullable AsyncSpec asyncSpec();

    @Nullable Filter filter();

    SessionMode sessionMode();

    /** Hotbar key (1..9) → handler. Fired when player presses N while hovering any slot in our top inventory. */
    Map<Integer, ClickHandler> hotbarBinds();

    /** Slots that accept item placement (form/input mode). Click events in these slots are NOT cancelled. */
    Set<Integer> inputSlots();

    /** If non-null, this page's state (page index) is persisted in the player's PDC and restored on rejoin. */
    @Nullable String persistentKey();

    static Builder builder() { return new dev.phillip.invpage.impl.InventoryPageImpl.BuilderImpl(); }

    interface Builder {
        Builder title(Component title);
        Builder rows(int rows);
        Builder layout(Layout layout);
        Builder theme(InventoryTheme theme);
        Builder navigation(NavigationButtons nav);
        Builder permissionGate(PermissionGate gate);
        Builder requirePermission(String node);

        Builder items(List<InventoryItem> items);
        Builder fixed(int slot, InventoryItem item);

        Builder onClick(ClickHandler handler);

        Builder refreshEvery(Duration duration);

        Builder asyncItems(AsyncSpec spec);

        Builder filter(Filter filter);

        Builder sessionMode(SessionMode mode);

        Builder hotbarBind(int key /* 1..9 */, ClickHandler handler);
        Builder inputSlot(int slot);
        Builder inputSlots(int... slots);
        Builder persistent(String key);

        InventoryPage build();
    }
}
