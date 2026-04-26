# Folio

[English](README.md) · [Deutsch](README.de.md)

> Modulares, fluentes, builder-basiertes **Framework für paginierte Inventar-GUIs** für Paper 1.21+.
> Pure Adventure-API · Java 21 · keine Dependencies außer `paper-api`.

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
                                          (per-Player oder SHARED)
```

## Features

| Layer | Was es kann |
|---|---|
| **Core** | `InventoryPage` · `PagedInventory` · `InventoryItem` · `InventorySession` · `InventoryRegistry` |
| **Click** | Sealed `ClickResult` (Allow / Cancel / Close / Refresh / Navigate), getypte Handler (left/right/shift/drop/numberKey), Bubbling-Kette (Item → Page → Inventar), Per-Item-Cooldowns, Async-Handler |
| **Layout** | Sealed `Layout`: `LinearLayout` · `GridLayout` · `MaskLayout` (Text-Block-Pattern mit `B`/`X`/`.`) |
| **Pagination** | Auto + manuell, konfigurierbare Navigation-Slots, MiniMessage-Page-Anzeige |
| **Animation** | `AnimatedItem` mit Frames, `refreshEvery(Duration)`, Slot-Diffing-Renderer (kein Flackern), pausiert wenn niemand zuschaut |
| **Async** | `CompletableFuture<List<InventoryItem>>`-Quelle, animierter Placeholder, Error-Fallback, TTL-Cache, Soft-Cancel-on-Close |
| **Filter / Suche** | `Filter`-Predicates mit AND/OR/negate, **Anvil-GUI Live-Suche** via `SearchPrompt`, Filter-State pro Session |
| **Multi-Player** | `SessionMode.SHARED` — eine Inventory-Instanz, mehrere Viewer (Co-op-GUIs, Auktionen) |
| **Form / Input** | Slots als `inputSlot(...)` markieren, Click NICHT gecancelt, Leftover-Items werden bei Close zurückerstattet |
| **Hotbar-Tasten** | `hotbarBind(1..9, handler)` — Page-globale Number-Key-Shortcuts |
| **Persistenz** | `.persistent("key")` — Page-Index landet im `PersistentDataContainer`, beim Rejoin restored |
| **Permissions** | `PermissionGate` auf Page- **und** Item-Ebene, automatisches Hide |
| **Theme** | Zentrales `InventoryTheme` (Border-Material, Sounds, Indicator-Template) |
| **i18n** | Bilingual eingebaut (en + de), drop-in `lang/*.yml`-Files, Region-aware Lookup (`de_at` → `de` → `en`), `language: auto/en/de/...` in der Config |
| **Config** | `config.yml` für Sprache + Demo-Toggles (Master-Switch + per-Sub abschaltbar) |

---

## Installation

```xml
<dependency>
    <groupId>dev.phillip</groupId>
    <artifactId>folio</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

`folio-1.0.0.jar` in den `plugins/`-Ordner. In Deinem eigenen Plugin in `plugin.yml`:

```yaml
depend: [Folio]
```

Registry holen:

```java
InventoryRegistry registry = ((InventoryPagePlugin) Bukkit.getPluginManager()
        .getPlugin("Folio")).registry();
```

---

## Schnellstart — ein 28-Slot-Shop

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

### Mask-Layout — Text-Blöcke

```java
.layout(MaskLayout.of("""
        BBBBBBBBB
        B XXXXX B
        B XXXXX B
        B XXXXX B
        BBBBBBBBB
        """))
```

| Zeichen | Bedeutung |
|---|---|
| `X` | Item-Slot (von Auto-Pagination befüllt) |
| `B` | Border (Theme-Material, kein Click) |
| `.` | leerer Slot |
| `<` `>` `\|` `F` `L` `P` | reservierte Marker (über `MaskLayout.slotOf(...)` für Custom-Nav abrufbar) |

Whitespace wird ignoriert — schreib das Pattern natürlich.

### Grid-Layout

```java
.layout(GridLayout.bordered())                   // Border + letzte Reihe reserviert
.layout(new GridLayout(true, 0))                 // nur Border
.layout(GridLayout.plain())                      // 9×rows, kein Border
```

### Linear-Layout

```java
.layout(LinearLayout.full())                     // 0..size
.layout(LinearLayout.withNavRow())               // 0..size-9 (letzte Reihe reserviert)
```

---

## Click-Handler

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

`ClickResult` ist ein sealed interface — exhaustive switch:

```java
ClickResult result = handler.handle(ctx);
switch (result) {
    case ClickResult.Allow a    -> { /* Original-Click durchlassen */ }
    case ClickResult.Cancel c   -> { /* default — bereits gecancelt */ }
    case ClickResult.Close cl   -> ctx.player().closeInventory();
    case ClickResult.Refresh r  -> session.refresh();
    case ClickResult.Navigate n -> session.switchPage(n.targetPage());
}
```

Click-Events bubblen: **Item-Handler → Item-Global → Page-Global**, stoppt beim ersten non-`Cancel`-Result.

---

## Animationen

```java
List<Frame> frames = List.of(
        Frame.of(named(Material.RED_WOOL,    "Lädt"), 4),
        Frame.of(named(Material.YELLOW_WOOL, "Lädt"), 4),
        Frame.of(named(Material.LIME_WOOL,   "Lädt"), 4)
);

AnimatedItem loading = AnimatedItem.builder().frames(frames).build();

InventoryPage.builder()
        .refreshEvery(Duration.ofMillis(50))   // 1 Tick — clamped auf >= 1
        .fixed(13, loading)
        ...
```

Diff-Renderer schreibt nur Slots, deren Stack sich tatsächlich geändert hat → **kein Flackern**.

---

## Async-Loading

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

Inventar öffnet **sofort** mit Placeholdern. Wenn das Future resolvt, poppen die Items auf dem Main-Thread rein (gediffed). Schließt der Spieler mid-load, wird das pendiente Future soft-discarded — Dein Callback feuert nicht mehr.

---

## Filter & Live-Suche

```java
// Predicate-basierter Filter
Filter cheap = (item, viewer) -> price(item) < viewer.getLevel();
session.setFilter(cheap.and(byCategory("weapons")));

// Anvil-GUI Live-Suche
SearchPrompt.builder(plugin, player)
        .title(Component.text("Suche", NamedTextColor.GOLD))
        .initial("")
        .onChange(text -> /* Live-Preview falls gewünscht */ )
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

## Persistenz

```java
// 1) Named-Factory registrieren
registry.registerNamed("shop", () -> PagedInventory.paginate(buildShopPage()));

// 2) Page als persistent markieren
InventoryPage.builder()
        .persistent("shop")     // wird beim Close in PDC gesichert
        ...
```

Nach Re-Login liest Folio den PDC und öffnet wieder dieselbe Page auf demselben Index — automatisch.

Löschen via `SessionPersistence#clear(player)` (Getter über die Plugin-Instanz) oder im Demo: `/foliodemo exit`.

---

## Hotbar-Keybinds

```java
InventoryPage.builder()
        .hotbarBind(1, ctx -> { ctx.player().sendMessage("Quick Action 1"); return ClickResult.cancel(); })
        .hotbarBind(2, ctx -> ClickResult.refresh())
        .hotbarBind(3, ctx -> ClickResult.close())
        ...
```

Wenn der Spieler über irgendeinem Slot Deines Top-Inventars hovert und `1..9` drückt, feuert der jeweilige Handler.

---

## Form / Input-Slots

```java
InventoryPage.builder()
        .inputSlots(11, 13, 15)                                  // Spieler darf hier Items reinlegen
        .fixed(22, InventoryItem.builder()
                .material(Material.EMERALD).name(Component.text("Bestätigen"))
                .onClick(ctx -> {
                    var inv = ctx.session().inventoryFor(ctx.player());
                    var ingredients = List.of(inv.getItem(11), inv.getItem(13), inv.getItem(15));
                    if (ingredients.stream().anyMatch(i -> i == null || i.getType().isAir())) {
                        ctx.player().sendMessage("Alle 3 Slots befüllen!");
                        return ClickResult.cancel();
                    }
                    craft(ctx.player(), ingredients);
                    inv.setItem(11, null); inv.setItem(13, null); inv.setItem(15, null);
                    return ClickResult.refresh();
                })
                .build())
        .build();
```

Beim Close werden Leftover-Items aus den Input-Slots zurück ins Spielerinventar gelegt (oder gedroppt falls voll) — **Spieler verlieren nie was**.

---

## Multi-Player (Shared Sessions)

```java
InventoryPage.builder()
        .sessionMode(SessionMode.SHARED)                         // ← eine Inventory, viele Viewer
        .title(Component.text("Auktion"))
        ...
```

Alle Viewer sehen dieselbe `Inventory`-Instanz — Klicks von einem sind sofort für alle sichtbar. Page-Cursor ist auch shared (Konsens-Paging).

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

Indicator-Template ist **MiniMessage** mit `<current>`- und `<total>`-Tokens.

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
        .requirePermission("shop.use")           // Page-Level-Gate
        ...
```

Versteckte Items werden weder im Layout platziert noch in die Pagination eingerechnet.

---

## Komplettes Beispiel — alle Features in einer Page

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
        .inputSlots(52)                          // einzelner "drop here to sell"-Slot
        .sessionMode(SessionMode.PER_PLAYER)
        .requirePermission("shop.use")
        .onClick(ctx -> ClickResult.cancel())   // Fallback
        .build();

registry.open(player, PagedInventory.paginate(page));
```

---

## Eingebauter Demo

Das Plugin bringt `/foliodemo <sub>` mit (Alias `/invpagedemo`):

| Sub | Demonstriert |
|---|---|
| `shop` | Mask-Layout · 200 Items · Pagination · Navigation · Cooldowns |
| `anim` | 4 Slots zyklen 8 Wollfarben (6-Tick-Intervalle) |
| `async` | 1.5 s simulierte DB-Query mit Placeholder · TTL-Cache |
| `filter` | Toggle-Filter-Button · live re-paginiert |
| `shared` | Zwei Spieler, eine Inventar-Instanz, Shared-Click-Counter |
| `search` | Anvil-GUI Live-Suche → öffnet Shop gefiltert |
| `persist` | Öffnen, navigieren, Server verlassen, rejoinen → öffnet auf gleicher Seite |
| `keys` | Hotbar 1..9 Aktionen während Du über Items hoverst |
| `form`  | 3 Input-Slots + Smaragd-Confirm mit Validierung |
| `exit`  | Gespeicherten Persistence-State löschen |

---

## Konfiguration

Beim ersten Start schreibt Folio `plugins/Folio/config.yml`:

```yaml
language: auto

demo:
  enabled: true
  disabled-subs: []
```

| Key | Effekt |
|---|---|
| `language: auto` | Pro Spieler nach `player.locale()` (Default) |
| `language: en` / `de` / `<code>` | **Eine** Sprache für alle erzwingen (muss zu einer `lang/<code>.yml` passen) |
| `demo.enabled: false` | `/foliodemo` komplett aus — Folio bleibt als Library nutzbar |
| `demo.disabled-subs: [persist, shared]` | Einzelne Sub-Demos aus Tab-Completion ausblenden + Aufruf ablehnen |

Änderungen greifen nach `/reload confirm` oder Server-Restart.

---

## Internationalisierung (i18n)

Folio ist **out of the box bilingual** (Englisch + Deutsch) und **erweiterbar** über drop-in YAML-Dateien.

### Eingebaut

`lang/en.yml` und `lang/de.yml` werden beim ersten Start automatisch nach `plugins/Folio/lang/` extrahiert. Edits darin überschreiben die hardcoded Defaults — Texte ändern ohne Recompile.

### Neue Sprache hinzufügen

1. `lang/en.yml` → `lang/<code>.yml` kopieren (z.B. `lang/fr.yml` für Französisch)
2. Werte übersetzen (`%s` / `%d`-Platzhalter NICHT entfernen)
3. Server neu starten

Folio scannt `plugins/Folio/lang/*.yml` in `onEnable` und merged jede Datei in seine Übersetzungs-Tabelle. Server-Log bestätigt:

```
[Folio] Loaded language 'fr' (45 keys) from fr.yml
```

### Lookup-Reihenfolge

Pro Spieler löst Folio die Sprache so auf:

```
forced (Config)  →  <lang>_<country>  →  <lang>  →  en
```

| Client-Locale | Versucht | Gewinnt (mit vorhandenen Files) |
|---|---|---|
| `de_DE` | `de_de` → `de` → `en` | `de.yml` |
| `de_AT` „Deitsch (Österreich)" | `de_at` → `de` → `en` | `de_at.yml` falls vorhanden, sonst `de.yml` |
| `pt_BR` | `pt_br` → `pt` → `en` | `pt_br.yml` falls vorhanden, sonst `en.yml` |
| `fr_CA` | `fr_ca` → `fr` → `en` | `fr.yml` falls vorhanden |

**Naming-Regel**: Dateiname muss zur Locale passen, **nicht zum Land allein**. Eine `at.yml` würde nie auto-matchen, weil kein MC-Client `"at"` als Sprachkürzel zurückgibt. Korrekt ist `de_at.yml`.

### Datei-Format

Standard-YAML mit dotted-or-nested Keys:

```yaml
nav:
  first:    "« Première page"
  previous: "‹ Précédent"
demo:
  shop:
    title:   "Boutique"
    click:   "Cliqué : %s #%d"
```

Fehlende Keys fallen die Lookup-Chain runter — kein Crash, immer wird etwas gerendert.

### Programmatisches Override

```java
Messages.setForcedLanguage("de");     // Deutsch für alle erzwingen, egal welche Client-Sprache
Messages.setForcedLanguage("auto");   // Per-Player-Verhalten wiederherstellen
```

---

## Projekt-Layout

```
src/main/java/dev/phillip/invpage/
├── InventoryPagePlugin.java
├── api/                    ← öffentliche, stabile Oberfläche
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
├── i18n/
│   └── Messages            ← Per-Player-Locale-Resolver, Region-aware Lookup
├── impl/                   ← intern — nicht direkt drauf depen­den
│   ├── InventoryRegistryImpl · InventorySessionImpl · InventoryPageImpl · PagedInventoryImpl
│   ├── InventoryItemImpl · AnimatedItemImpl
│   ├── SlotMap · DiffRenderer · RefreshScheduler
│   ├── AsyncCache · CooldownTracker
│   ├── SessionPersistence
│   └── InventoryListener
└── demo/
    └── DemoCommand

src/main/resources/
├── plugin.yml
├── config.yml              ← Default-Config
└── lang/
    ├── en.yml              ← gebündeltes Englisch (auto-saved)
    └── de.yml              ← gebündeltes Deutsch (auto-saved)
```

---

## Build

```bash
mvn clean package
# → target/folio-1.0.0.jar
```

Braucht JDK 21 (`apt install openjdk-21-jdk-headless`) und Maven 3.8+.

---

## Design-Defaults (und das Warum)

| # | Default | Begründung |
|---|---|---|
| 1 | Indicator-Template lebt im `InventoryTheme` (MiniMessage, configurable) | Eine zentrale Stelle zum Re-Themen des ganzen Plugins |
| 2 | `SHARED`-Mode: shared Items **und** shared Cursor | Vorhersehbar für Co-op-GUIs (Auktionen, Parties) |
| 3 | Click + Drag im Top-Inventar immer gecancelt; `ClickResult.allow()` opt-in (nur sync) | Sicherer Default — Input-Mode ist explizit pro Slot |
| 4 | Async-Cancel = soft-discard (kein `Future.cancel(true)`) | Interrupting eines JDBC-Calls ist unsicher; Caller besitzt das Future-Lifecycle |
| 5 | Refresh-Intervall clamped auf ≥ 1 Tick (50 ms) | Sub-Tick-Scheduling gibt's auf Bukkit-Mainthread nicht |

---

## Lizenz

MIT — siehe `LICENSE`.
