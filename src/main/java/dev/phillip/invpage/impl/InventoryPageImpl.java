package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.InventoryPage;
import dev.phillip.invpage.api.SessionMode;
import dev.phillip.invpage.api.async.AsyncSpec;
import dev.phillip.invpage.api.click.ClickHandler;
import dev.phillip.invpage.api.gate.PermissionGate;
import dev.phillip.invpage.api.layout.Layout;
import dev.phillip.invpage.api.layout.LinearLayout;
import dev.phillip.invpage.api.nav.NavigationButtons;
import dev.phillip.invpage.api.search.Filter;
import dev.phillip.invpage.api.theme.InventoryTheme;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class InventoryPageImpl implements InventoryPage {

    private final Component title;
    private final int rows;
    private final Layout layout;
    private final InventoryTheme theme;
    private final NavigationButtons navigation;
    private final PermissionGate gate;
    private final List<InventoryItem> items;
    private final Map<Integer, InventoryItem> fixed;
    private final ClickHandler global;
    private final Duration refresh;
    private final AsyncSpec asyncSpec;
    private final Filter filter;
    private final SessionMode mode;
    private final Map<Integer, ClickHandler> hotbarBinds;
    private final Set<Integer> inputSlots;
    private final String persistentKey;

    InventoryPageImpl(Component title, int rows, Layout layout, InventoryTheme theme,
                      NavigationButtons navigation, PermissionGate gate, List<InventoryItem> items,
                      Map<Integer, InventoryItem> fixed, ClickHandler global, Duration refresh,
                      AsyncSpec asyncSpec, Filter filter, SessionMode mode,
                      Map<Integer, ClickHandler> hotbarBinds, Set<Integer> inputSlots, String persistentKey) {
        this.title = title;
        this.rows = rows;
        this.layout = layout;
        this.theme = theme;
        this.navigation = navigation;
        this.gate = gate;
        this.items = items;
        this.fixed = fixed;
        this.global = global;
        this.refresh = refresh;
        this.asyncSpec = asyncSpec;
        this.filter = filter;
        this.mode = mode;
        this.hotbarBinds = hotbarBinds;
        this.inputSlots = inputSlots;
        this.persistentKey = persistentKey;
    }

    @Override public Component title() { return title; }
    @Override public int rows() { return rows; }
    @Override public Layout layout() { return layout; }
    @Override public InventoryTheme theme() { return theme; }
    @Override public NavigationButtons navigation() { return navigation; }
    @Override public PermissionGate permissionGate() { return gate; }
    @Override public List<InventoryItem> items() { return items; }
    @Override public Map<Integer, InventoryItem> fixedSlots() { return fixed; }
    @Override public @Nullable ClickHandler globalClickHandler() { return global; }
    @Override public @Nullable Duration refreshInterval() { return refresh; }
    @Override public @Nullable AsyncSpec asyncSpec() { return asyncSpec; }
    @Override public @Nullable Filter filter() { return filter; }
    @Override public SessionMode sessionMode() { return mode; }
    @Override public Map<Integer, ClickHandler> hotbarBinds() { return hotbarBinds; }
    @Override public Set<Integer> inputSlots() { return inputSlots; }
    @Override public @Nullable String persistentKey() { return persistentKey; }

    public static final class BuilderImpl implements Builder {
        private Component title = Component.text("Inventory");
        private int rows = 6;
        private Layout layout = LinearLayout.withNavRow();
        private InventoryTheme theme = InventoryTheme.DEFAULT;
        private NavigationButtons nav = NavigationButtons.standard();
        private PermissionGate gate = PermissionGate.ALWAYS;
        private List<InventoryItem> items = List.of();
        private final Map<Integer, InventoryItem> fixed = new LinkedHashMap<>();
        private ClickHandler global;
        private Duration refresh;
        private AsyncSpec asyncSpec;
        private Filter filter;
        private SessionMode mode = SessionMode.PER_PLAYER;
        private final Map<Integer, ClickHandler> hotbarBinds = new TreeMap<>();
        private final Set<Integer> inputSlots = new HashSet<>();
        private String persistentKey;

        @Override public Builder title(Component t) { this.title = t; return this; }
        @Override public Builder rows(int r) {
            if (r < 1 || r > 6) throw new IllegalArgumentException("rows must be 1..6");
            this.rows = r; return this;
        }
        @Override public Builder layout(Layout l) { this.layout = l; return this; }
        @Override public Builder theme(InventoryTheme t) { this.theme = t; return this; }
        @Override public Builder navigation(NavigationButtons n) { this.nav = n; return this; }
        @Override public Builder permissionGate(PermissionGate g) { this.gate = g; return this; }
        @Override public Builder requirePermission(String node) {
            this.gate = PermissionGate.all(this.gate, PermissionGate.hasPermission(node)); return this;
        }
        @Override public Builder items(List<InventoryItem> items) { this.items = List.copyOf(items); return this; }
        @Override public Builder fixed(int slot, InventoryItem item) { fixed.put(slot, item); return this; }
        @Override public Builder onClick(ClickHandler h) { this.global = h; return this; }
        @Override public Builder refreshEvery(Duration d) { this.refresh = d; return this; }
        @Override public Builder asyncItems(AsyncSpec s) { this.asyncSpec = s; return this; }
        @Override public Builder filter(Filter f) { this.filter = f; return this; }
        @Override public Builder sessionMode(SessionMode m) { this.mode = m; return this; }
        @Override public Builder hotbarBind(int key, ClickHandler handler) {
            if (key < 1 || key > 9) throw new IllegalArgumentException("hotbar key must be 1..9");
            hotbarBinds.put(key, handler);
            return this;
        }
        @Override public Builder inputSlot(int slot) { inputSlots.add(slot); return this; }
        @Override public Builder inputSlots(int... slots) { for (int s : slots) inputSlots.add(s); return this; }
        @Override public Builder persistent(String key) { this.persistentKey = key; return this; }

        @Override public InventoryPage build() {
            return new InventoryPageImpl(title, rows, layout, theme, nav, gate,
                    items, new HashMap<>(fixed), global, refresh, asyncSpec, filter, mode,
                    Map.copyOf(hotbarBinds), Set.copyOf(inputSlots), persistentKey);
        }
    }
}
