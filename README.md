# Folio

> Modular, fluent, builder-based **paginated inventory GUI framework** for Paper 1.21+.
> Pure Adventure API · Java 21 · zero dependencies beyond `paper-api`.

```
                                              ┌──────────────┐
  PagedInventory ──┬──> InventoryPage(0) ─────│  Layout      │
                   ├──> InventoryPage(1) ─────│  Items       │
                   └──> InventoryPage(N) ─────│  Navigation  │
                                              │  Theme       │
                                              └──────────────┘
                                                    │
                                                    ▼
                                          InventorySession
                                          (per-player or SHARED)
```

## Features

| Layer | Capability |
|---|---|
| **Core** | `InventoryPage` · `PagedInventory` · `InventoryItem` · `InventorySession` · `InventoryRegistry` |
| **Click** | sealed `ClickResult` (Allow / Cancel / Close / Refresh / Navigate), per-type handlers (left/right/shift/drop/numberKey), bubbling chain (item → page → inventory), per-item cooldowns, async handlers |
| **Layout** | sealed `Layout`: `LinearLayout` · `GridLayout` · `MaskLayout` (text-block patterns with `B`/`X`/`.`) |
| **Pagination** | auto + manual, configurable navigation slots, MiniMessage page-indicator |
| **Animation** | `AnimatedItem` with frames, `refreshEvery(Duration)`, slot-diffing renderer (no flicker), pauses when nobody's watching |
| **Async** | `CompletableFuture<List<InventoryItem>>` source, animated placeholder, error fallback, TTL cache, soft-cancel-on-close |
| **Filter / Search** | `Filter` predicates with AND/OR/negate, **anvil-GUI live search** via `SearchPrompt`, per-session filter state |
| **Multi-player** | `SessionMode.SHARED` — one Inventory instance, multiple viewers (co-op GUIs, auctions) |
| **Form / Input** | mark slots as `inputSlot(...)`, click NOT cancelled, leftover items returned on close |
| **Hotbar keys** | `hotbarBind(1..9, handler)` — page-global number-key shortcuts |
| **Persistence** | `.persistent("key")` — page index saved in player's `PersistentDataContainer`, restored on rejoin |
| **Permissions** | `PermissionGate` on page **and** item level, auto-hide |
| **Theme** | central `InventoryTheme` (border material, sounds, indicator template) |

---

## Install

```xml
<dependency>
    <groupId>dev.phillip</groupId>
    <artifactId>folio</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

Drop `folio-1.0.0.jar` into `plugins/`. Add to your own plugin's `plugin.yml`:

```yaml
depend: [Folio]
```

Get the registry:

```java
InventoryRegistry registry = ((InventoryPagePlugin) Bukkit.getPluginManager()
        .getPlugin("Folio")).registry();
```

---

## Quick start — a 28-slot shop

```java
List<InventoryItem> offers = catalog.stream()
        .map(o -> InventoryItem.builder()
                .material(o.material())
                .name(Component.text(o.name(), NamedTextColor.YELLOW))
                .lore(Component.text("Preis: " + o.price() + "g", NamedTextColor.GRAY))
                .cooldown(Duration.ofMillis(500))
                .clickableIf(p -> economy.balance(p) >= o.price())
                .onLeftClick(ctx -> {
                    economy.charge(ctx.player(), o.price());
                    ctx.player().getInventory().addItem(o.product());
                    return ClickResult.refresh();
                })
                .build())
        .toList();

InventoryPage page = InventoryPage.builder()
        .title(Component.text("Shop", NamedTextColor.GOLD))
        .rows(6)
        .layout(MaskLayout.of("""
                BBBBBBBBB
                BXXXXXXXB
                BXXXXXXXB
                BXXXXXXXB
                BXXXXXXXB
                BBBBBBBBB
                """))
        .navigation(NavigationButtons.builder()
                .firstSlot(45).previousSlot(47).pageIndicatorSlot(49)
                .nextSlot(51).lastSlot(53).build())
        .items(offers)
        .build();

registry.open(player, PagedInventory.paginate(page));
```

---

## Layouts

### Mask layout — text blocks

```java
.layout(MaskLayout.of("""
        BBBBBBBBB
        B XXXXX B
        B XXXXX B
        B XXXXX B
        BBBBBBBBB
        """))
```

| Char | Meaning |
|---|---|
| `X` | item slot (consumed by auto-pagination) |
| `B` | border (theme material, no click) |
| `.` | empty slot |
| `<` `>` `\|` `F` `L` `P` | reserved markers (used by `MaskLayout.slotOf(...)` for custom nav placement) |

Whitespace is ignored — write the pattern naturally.

### Grid layout

```java
.layout(GridLayout.bordered())                   // border + bottom row reserved
.layout(new GridLayout(true, 0))                 // border only
.layout(GridLayout.plain())                      // 9×rows, no border
```

### Linear layout

```java
.layout(LinearLayout.full())                     // 0..size
.layout(LinearLayout.withNavRow())               // 0..size-9 (last row reserved)
```

---

## Click handlers

```java
InventoryItem.builder()
        .material(Material.DIAMOND_SWORD)
        .name(Component.text("Excalibur"))
        .onLeftClick(ctx  -> { upgrade(ctx.player()); return ClickResult.refresh(); })
        .onRightClick(ctx -> { describe(ctx.player()); return ClickResult.cancel(); })
        .onShiftClick(ctx -> { sell(ctx.player());     return ClickResult.refresh(); })
        .onClickAsync(ctx -> { db.recordClick(...);    return ClickResult.cancel(); })
        .build();
```

`ClickResult` is a sealed interface — exhaustive switch:

```java
ClickResult result = handler.handle(ctx);
switch (result) {
    case ClickResult.Allow a    -> { /* let the underlying click through */ }
    case ClickResult.Cancel c   -> { /* default — already cancelled */ }
    case ClickResult.Close cl   -> ctx.player().closeInventory();
    case ClickResult.Refresh r  -> session.refresh();
    case ClickResult.Navigate n -> session.switchPage(n.targetPage());
}
```

Click events bubble: **item handler → item global → page global**, stopping at the first non-`Cancel` result.

---

## Animations

```java
List<Frame> frames = List.of(
        Frame.of(named(Material.RED_WOOL,    "Lädt"), 4),
        Frame.of(named(Material.YELLOW_WOOL, "Lädt"), 4),
        Frame.of(named(Material.LIME_WOOL,   "Lädt"), 4)
);

AnimatedItem loading = AnimatedItem.builder().frames(frames).build();

InventoryPage.builder()
        .refreshEvery(Duration.ofMillis(50))   // 1 tick — clamped to >= 1
        .fixed(13, loading)
        ...
```

Diff-renderer only writes slots whose stack actually changed → **no flicker**.

---

## Async loading

```java
AsyncSpec spec = AsyncSpec.of(() -> CompletableFuture.supplyAsync(() ->
                database.loadShopOffers(playerId)
                        .stream()
                        .map(this::toItem)
                        .toList()))
        .placeholder(InventoryItem.of(named(Material.CLOCK, "§7Lade …")))
        .errorItem(InventoryItem.of(named(Material.BARRIER, "§cFehler")))
        .cache(Duration.ofMinutes(5))
        .cacheKey("shop:" + playerId)
        .build();

InventoryPage.builder()
        .asyncItems(spec)
        ...
```

Inventory opens **instantly** with placeholders. When the future resolves, items pop in on the main thread (diffed). If the player closes mid-load, the pending future is soft-discarded — your callback won't run.

---

## Filter & live search

```java
// Predicate-based filter
Filter cheap = (item, viewer) -> price(item) < viewer.getLevel();
session.setFilter(cheap.and(byCategory("weapons")));

// Anvil-GUI live search
SearchPrompt.builder(plugin, player)
        .title(Component.text("Search", NamedTextColor.GOLD))
        .initial("")
        .onChange(text -> /* live preview if you want */ )
        .onConfirm(query -> {
            String q = query.toLowerCase();
            Filter f = (i, v) -> i.render(v).getType().name().toLowerCase().contains(q);
            var s = registry.open(player, paged);
            ((InventorySessionImpl) s).setFilter(f);
        })
        .onCancel(() -> registry.open(player, paged))
        .open();
```

---

## Persistence

```java
// 1) Register a named factory
registry.registerNamed("shop", () -> PagedInventory.paginate(buildShopPage()));

// 2) Mark the page persistent
InventoryPage.builder()
        .persistent("shop")     // saved to PDC on close
        ...
```

After a re-login, Folio reads the player's PDC and reopens the same page on the same index — automatic.

Clear with: `registry` getter for `SessionPersistence#clear(player)` or, in the demo, `/foliodemo exit`.

---

## Hotbar keybinds

```java
InventoryPage.builder()
        .hotbarBind(1, ctx -> { ctx.player().sendMessage("Quick action 1"); return ClickResult.cancel(); })
        .hotbarBind(2, ctx -> ClickResult.refresh())
        .hotbarBind(3, ctx -> ClickResult.close())
        ...
```

While the player hovers any slot in your top inventory, pressing `1..9` fires the matching handler.

---

## Form / input slots

```java
InventoryPage.builder()
        .inputSlots(11, 13, 15)                                  // player can place items here
        .fixed(22, InventoryItem.builder()
                .material(Material.EMERALD).name(Component.text("Confirm"))
                .onClick(ctx -> {
                    var inv = ctx.session().inventoryFor(ctx.player());
                    var ingredients = List.of(inv.getItem(11), inv.getItem(13), inv.getItem(15));
                    if (ingredients.stream().anyMatch(i -> i == null || i.getType().isAir())) {
                        ctx.player().sendMessage("Fill all 3 slots!");
                        return ClickResult.cancel();
                    }
                    craft(ctx.player(), ingredients);
                    inv.setItem(11, null); inv.setItem(13, null); inv.setItem(15, null);
                    return ClickResult.refresh();
                })
                .build())
        .build();
```

On close, leftover items in input slots are returned to the player's inventory (or dropped if full) — players can't lose stuff.

---

## Multi-player (shared) sessions

```java
InventoryPage.builder()
        .sessionMode(SessionMode.SHARED)                         // ← one Inventory, many viewers
        .title(Component.text("Auction"))
        ...
```

All viewers see the same `Inventory` instance — clicks by one are visible to all. Page cursor is also shared (consensus paging).

---

## Theme

```java
InventoryTheme theme = InventoryTheme.builder()
        .borderMaterial(Material.BLACK_STAINED_GLASS_PANE)
        .openSound(Sound.UI_TOAST_IN)
        .clickSound(Sound.UI_BUTTON_CLICK)
        .pageSwitchSound(Sound.ITEM_BOOK_PAGE_TURN)
        .deniedSound(Sound.BLOCK_NOTE_BLOCK_BASS)
        .pageIndicatorTemplate("<dark_gray>« <white><current><dark_gray>/<white><total> <dark_gray>»")
        .build();

InventoryPage.builder().theme(theme)...
```

Indicator template is **MiniMessage** with `<current>` and `<total>` tokens.

---

## Permissions

```java
InventoryItem.builder()
        .visibleIf(PermissionGate.hasPermission("shop.see-rare"))
        .clickableIf(PermissionGate.all(
                PermissionGate.hasPermission("shop.buy"),
                p -> economy.balance(p) > 0))
        ...

InventoryPage.builder()
        .requirePermission("shop.use")           // page-level gate
        ...
```

Hidden items are excluded from layout placement and don't count toward pagination.

---

## Complete example — all features in one page

```java
InventoryPage page = InventoryPage.builder()
        .title(Component.text("Big Demo", NamedTextColor.GOLD))
        .rows(6)
        .layout(MaskLayout.of("""
                BBBBBBBBB
                BXXXXXXXB
                BXXXXXXXB
                BXXXXXXXB
                BXXXXXXXB
                FP I NL S
                """))
        .theme(InventoryTheme.dark())
        .navigation(NavigationButtons.builder()
                .firstSlot(45).previousSlot(46).pageIndicatorSlot(48)
                .nextSlot(50).lastSlot(51).build())
        .items(offers)
        .filter((i, v) -> v.hasPermission("shop.see-all") || !rare(i))
        .asyncItems(databaseSpec)
        .refreshEvery(Duration.ofSeconds(1))
        .persistent("big-demo")
        .hotbarBind(1, ctx -> ClickResult.refresh())
        .hotbarBind(9, ctx -> ClickResult.close())
        .inputSlots(52)                          // single "drop here to sell" slot
        .sessionMode(SessionMode.PER_PLAYER)
        .requirePermission("shop.use")
        .onClick(ctx -> ClickResult.cancel())   // fallback
        .build();

registry.open(player, PagedInventory.paginate(page));
```

---

## Built-in demo

The plugin ships with `/foliodemo <sub>` (alias `/invpagedemo`):

| Sub | Demonstrates |
|---|---|
| `shop` | Mask layout · 200 items · pagination · navigation · cooldowns |
| `anim` | 4 slots cycling 8 wool colors at 6-tick intervals |
| `async` | 1.5 s simulated DB load with placeholder · TTL cache |
| `filter` | Toggle filter button · re-paginates live |
| `shared` | Two players, one inventory instance |
| `search` | Anvil-GUI live search → re-opens shop with filter |
| `persist` | Open, navigate, leave, rejoin → opens at the same page |
| `keys` | Hotbar 1..9 actions while hovering an item |
| `form`  | 3 input slots + emerald-confirm with validation |
| `exit`  | Clear stored persistence state |

---

## Project layout

```
src/main/java/dev/phillip/invpage/
├── InventoryPagePlugin.java
├── api/                    ← public, stable surface
│   ├── InventoryPage / PagedInventory / InventoryItem / InventorySession / InventoryRegistry
│   ├── SearchPrompt
│   ├── click/              ClickContext · ClickResult (sealed) · ClickHandler · CooldownPolicy
│   ├── layout/             Layout (sealed) · LinearLayout · GridLayout · MaskLayout
│   ├── nav/                NavigationButtons
│   ├── animation/          AnimatedItem · Frame
│   ├── async/              AsyncItemSource · AsyncSpec
│   ├── search/             Filter
│   ├── theme/              InventoryTheme
│   └── gate/               PermissionGate
├── impl/                   ← internal — don't depend on these directly
│   ├── InventoryRegistryImpl · InventorySessionImpl · InventoryPageImpl · PagedInventoryImpl
│   ├── InventoryItemImpl · AnimatedItemImpl
│   ├── SlotMap · DiffRenderer · RefreshScheduler
│   ├── AsyncCache · CooldownTracker
│   ├── SessionPersistence
│   └── InventoryListener
└── demo/
    └── DemoCommand
```

---

## Build

```bash
mvn clean package
# → target/folio-1.0.0.jar
```

Requires JDK 21 (`apt install openjdk-21-jdk-headless`) and Maven 3.8+.

---

## Design defaults (and the why)

| # | Default | Reason |
|---|---|---|
| 1 | Indicator template lives in `InventoryTheme` (MiniMessage, configurable) | One place to retheme entire plugin |
| 2 | `SHARED` mode: shared items **and** shared cursor | Predictable for co-op GUIs (auctions, parties) |
| 3 | Click + drag in top inventory always cancelled; `ClickResult.allow()` is opt-in (sync only) | Safer default — input mode is explicit per-slot |
| 4 | Async cancel = soft-discard (no `Future.cancel(true)`) | Interrupting a JDBC call is unsafe; caller owns the future's lifecycle |
| 5 | Refresh interval clamped to ≥ 1 tick (50 ms) | Sub-tick scheduling isn't a thing on Bukkit's main thread |

---

## License

MIT — see `LICENSE`.
