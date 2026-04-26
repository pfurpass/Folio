package dev.phillip.invpage.demo;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.InventoryPage;
import dev.phillip.invpage.api.PagedInventory;
import dev.phillip.invpage.api.SearchPrompt;
import dev.phillip.invpage.api.SessionMode;
import dev.phillip.invpage.api.animation.AnimatedItem;
import dev.phillip.invpage.api.animation.Frame;
import dev.phillip.invpage.api.async.AsyncSpec;
import dev.phillip.invpage.api.click.ClickResult;
import dev.phillip.invpage.api.layout.MaskLayout;
import dev.phillip.invpage.api.nav.NavigationButtons;
import dev.phillip.invpage.api.search.Filter;
import dev.phillip.invpage.api.theme.InventoryTheme;
import dev.phillip.invpage.impl.InventoryRegistryImpl;
import dev.phillip.invpage.impl.SessionPersistence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

public final class DemoCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final InventoryRegistryImpl registry;
    private final SessionPersistence persistence;

    public DemoCommand(Plugin plugin, InventoryRegistryImpl registry, SessionPersistence persistence) {
        this.plugin = plugin;
        this.registry = registry;
        this.persistence = persistence;
        // Register the persistent shop factory so it can be restored on rejoin.
        registry.registerNamed("persist-shop", () -> PagedInventory.paginate(buildPersistShopPage()));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }
        String mode = args.length > 0 ? args[0].toLowerCase() : "shop";
        switch (mode) {
            case "shop"    -> openShop(p);
            case "anim"    -> openAnim(p);
            case "async"   -> openAsync(p);
            case "filter"  -> openFilter(p);
            case "shared"  -> openShared(p);
            case "search"  -> openSearch(p);
            case "persist" -> openPersist(p);
            case "keys"    -> openKeys(p);
            case "form"    -> openForm(p);
            case "exit"    -> { persistence.clear(p); p.sendMessage(Component.text("Persistence cleared.", NamedTextColor.YELLOW)); }
            default -> p.sendMessage(Component.text("Unknown demo: " + mode, NamedTextColor.RED));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("shop", "anim", "async", "filter", "shared",
                "search", "persist", "keys", "form", "exit");
        return List.of();
    }

    // -- Shop demo: 200 items, mask layout, pagination + nav --
    private void openShop(Player p) {
        var items = sampleItems(200);
        var mask = MaskLayout.of("""
                BBBBBBBBB
                B.XXXXXXX
                BXXXXXXXX
                BXXXXXXXX
                BXXXXXXXX
                BFP<I>LBB
                """);
        // Note: F=first, P=prev, <=indicator? Actually mapping below uses standard slot positions.
        var page = InventoryPage.builder()
                .title(Component.text("Demo Shop", NamedTextColor.GOLD))
                .rows(6)
                .layout(mask)
                .navigation(NavigationButtons.builder()
                        .firstSlot(45).previousSlot(47).pageIndicatorSlot(49)
                        .nextSlot(51).lastSlot(53).build())
                .items(items)
                .onClick(ctx -> ClickResult.cancel())
                .build();
        registry.open(p, PagedInventory.paginate(page));
    }

    // -- Anim demo: rotating wool in 4 slots --
    private void openAnim(Player p) {
        var colors = new Material[]{
                Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
                Material.LIGHT_BLUE_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL
        };
        var frames = new ArrayList<Frame>();
        for (var c : colors) frames.add(Frame.of(named(c, "Cycling"), 6));

        var anim = AnimatedItem.builder().frames(frames).build();

        var page = InventoryPage.builder()
                .title(Component.text("Animation", NamedTextColor.AQUA))
                .rows(3)
                .layout(new dev.phillip.invpage.api.layout.GridLayout(true, 0))
                .navigation(NavigationButtons.disabled())
                .fixed(11, anim)
                .fixed(13, anim)
                .fixed(14, anim)
                .fixed(15, anim)
                .refreshEvery(Duration.ofMillis(50))
                .build();
        registry.open(p, PagedInventory.paginate(page));
    }

    // -- Async demo: simulated DB query --
    private void openAsync(Player p) {
        AsyncSpec spec = AsyncSpec.of(() -> CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    return sampleItems(45);
                }))
                .placeholder(InventoryItem.of(named(Material.CLOCK, "§7Lade …")))
                .errorItem(InventoryItem.of(named(Material.BARRIER, "§cFehler")))
                .cache(Duration.ofMinutes(1))
                .cacheKey("demo-async")
                .build();

        var page = InventoryPage.builder()
                .title(Component.text("Async Loading", NamedTextColor.LIGHT_PURPLE))
                .rows(6)
                .layout(MaskLayout.of("""
                        BBBBBBBBB
                        BXXXXXXXB
                        BXXXXXXXB
                        BXXXXXXXB
                        BXXXXXXXB
                        BBBBBBBBB
                        """))
                .navigation(NavigationButtons.standard())
                .asyncItems(spec)
                .build();

        registry.open(p, PagedInventory.paginate(page));
    }

    // -- Filter demo: 200 items, filter by material name containing "WOOL" --
    private void openFilter(Player p) {
        var items = sampleItems(200);
        Filter woolOnly = (i, viewer) -> i.render(viewer).getType().name().contains("WOOL");

        var toggleFilter = InventoryItem.builder()
                .material(Material.HOPPER)
                .name(Component.text("Toggle: nur Wolle", NamedTextColor.YELLOW))
                .onClick(ctx -> {
                    var session = (dev.phillip.invpage.impl.InventorySessionImpl) ctx.session();
                    session.setFilter(session.filter() == null ? woolOnly : null);
                    return ClickResult.refresh();
                })
                .build();

        var page = InventoryPage.builder()
                .title(Component.text("Filter Demo", NamedTextColor.GREEN))
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
                        .firstSlot(45).previousSlot(47).pageIndicatorSlot(49).nextSlot(51).lastSlot(53).build())
                .fixed(46, toggleFilter)
                .items(items)
                .build();

        registry.open(p, PagedInventory.paginate(page));
    }

    // -- Shared demo: all viewers share one underlying inventory --
    private void openShared(Player p) {
        var page = InventoryPage.builder()
                .title(Component.text("Co-Op GUI", NamedTextColor.GOLD))
                .rows(3)
                .layout(new dev.phillip.invpage.api.layout.GridLayout(true, 0))
                .navigation(NavigationButtons.disabled())
                .sessionMode(SessionMode.SHARED)
                .fixed(13, InventoryItem.builder()
                        .material(Material.NETHER_STAR)
                        .name(Component.text("Shared Click-Counter", NamedTextColor.GOLD))
                        .onClick(ctx -> {
                            ctx.player().sendMessage(Component.text("Shared click!", NamedTextColor.YELLOW));
                            return ClickResult.refresh();
                        })
                        .build())
                .build();
        registry.open(p, PagedInventory.paginate(page));
    }

    // -- Search demo: Anvil-Live-Suche --
    private void openSearch(Player p) {
        var items = sampleItems(200);
        var searchButton = InventoryItem.builder()
                .material(Material.NAME_TAG)
                .name(Component.text("Suche tippen…", NamedTextColor.YELLOW))
                .lore(Component.text("Klick öffnet Anvil-Eingabe", NamedTextColor.GRAY))
                .onClick(ctx -> {
                    var sess = ctx.session();
                    SearchPrompt.builder(plugin, ctx.player())
                            .title(Component.text("Suche", NamedTextColor.GOLD))
                            .initial("")
                            .onConfirm(query -> {
                                String q = query == null ? "" : query.trim().toLowerCase();
                                Filter f = q.isEmpty() ? null
                                        : (i, viewer) -> i.render(viewer).getType().name().toLowerCase().contains(q);
                                var newSession = registry.open(ctx.player(), PagedInventory.paginate(buildSearchPage(items)));
                                if (newSession != null && f != null) ((dev.phillip.invpage.impl.InventorySessionImpl) newSession).setFilter(f);
                            })
                            .onCancel(() -> registry.open(ctx.player(), PagedInventory.paginate(buildSearchPage(items))))
                            .open();
                    return ClickResult.cancel();
                })
                .build();

        var page = buildSearchPage(items, searchButton);
        registry.open(p, PagedInventory.paginate(page));
    }

    private InventoryPage buildSearchPage(List<InventoryItem> items, InventoryItem searchButton) {
        return InventoryPage.builder()
                .title(Component.text("Anvil-Search", NamedTextColor.GOLD))
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
                        .firstSlot(45).previousSlot(47).pageIndicatorSlot(49).nextSlot(51).lastSlot(53).build())
                .fixed(46, searchButton)
                .items(items)
                .build();
    }

    private InventoryPage buildSearchPage(List<InventoryItem> items) {
        return buildSearchPage(items, InventoryItem.builder()
                .material(Material.NAME_TAG)
                .name(Component.text("Suche tippen…", NamedTextColor.YELLOW))
                .build());
    }

    // -- Persist demo: page survives re-login --
    private void openPersist(Player p) {
        registry.open(p, PagedInventory.paginate(buildPersistShopPage()));
    }

    private InventoryPage buildPersistShopPage() {
        var items = sampleItems(100);
        return InventoryPage.builder()
                .title(Component.text("Persistent Shop", NamedTextColor.AQUA))
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
                        .firstSlot(45).previousSlot(47).pageIndicatorSlot(49).nextSlot(51).lastSlot(53).build())
                .items(items)
                .persistent("persist-shop")
                .build();
    }

    // -- Keys demo: hotbar 1..9 bound to chat actions --
    private void openKeys(Player p) {
        var info = InventoryItem.builder()
                .material(Material.BOOK)
                .name(Component.text("Drücke 1..9 während du hierüber hoverst", NamedTextColor.YELLOW))
                .lore(
                        Component.text("1 = Hallo", NamedTextColor.GRAY),
                        Component.text("2 = Refresh", NamedTextColor.GRAY),
                        Component.text("3 = Schließen", NamedTextColor.GRAY),
                        Component.text("4..9 = Zahl an Chat", NamedTextColor.GRAY)
                )
                .build();

        var b = InventoryPage.builder()
                .title(Component.text("Hotbar Keybinds", NamedTextColor.LIGHT_PURPLE))
                .rows(3)
                .layout(new dev.phillip.invpage.api.layout.GridLayout(true, 0))
                .navigation(NavigationButtons.disabled())
                .fixed(13, info)
                .hotbarBind(1, ctx -> { ctx.player().sendMessage(Component.text("Hallo!", NamedTextColor.GREEN)); return ClickResult.cancel(); })
                .hotbarBind(2, ctx -> ClickResult.refresh())
                .hotbarBind(3, ctx -> ClickResult.close());

        for (int k = 4; k <= 9; k++) {
            int key = k;
            b.hotbarBind(k, ctx -> {
                ctx.player().sendMessage(Component.text("Du hast " + key + " gedrückt", NamedTextColor.AQUA));
                return ClickResult.cancel();
            });
        }
        registry.open(p, PagedInventory.paginate(b.build()));
    }

    // -- Form demo: input slots + validate-on-confirm --
    private void openForm(Player p) {
        // Input slots: 11, 13, 15. Confirm: 22.
        var label = InventoryItem.of(named(Material.OAK_SIGN, "§eLeg 3 Items rein und klick Smaragd"));

        var confirm = InventoryItem.builder()
                .material(Material.EMERALD)
                .name(Component.text("Bestätigen", NamedTextColor.GREEN))
                .lore(Component.text("Validiert: alle 3 Slots gefüllt?", NamedTextColor.GRAY))
                .onClick(ctx -> {
                    var inv = ctx.session().inventoryFor(ctx.player());
                    int filled = 0;
                    var names = new java.util.ArrayList<String>();
                    for (int slot : new int[]{11, 13, 15}) {
                        var it = inv.getItem(slot);
                        if (it != null && !it.getType().isAir()) { filled++; names.add(it.getType().name()); }
                    }
                    if (filled < 3) {
                        ctx.player().sendMessage(Component.text("Nur " + filled + "/3 Slots gefüllt!", NamedTextColor.RED));
                        return ClickResult.cancel();
                    }
                    ctx.player().sendMessage(Component.text("OK: " + String.join(", ", names), NamedTextColor.GREEN));
                    // Consume the items (don't return them on close)
                    for (int slot : new int[]{11, 13, 15}) inv.setItem(slot, null);
                    return ClickResult.refresh();
                })
                .build();

        var page = InventoryPage.builder()
                .title(Component.text("Form-Mode", NamedTextColor.GOLD))
                .rows(3)
                .layout(new dev.phillip.invpage.api.layout.GridLayout(true, 0))
                .navigation(NavigationButtons.disabled())
                .fixed(4, label)
                .fixed(22, confirm)
                .inputSlots(11, 13, 15)
                .build();
        registry.open(p, PagedInventory.paginate(page));
    }

    // -- helpers --
    private static List<InventoryItem> sampleItems(int n) {
        var palette = new Material[]{
                Material.STONE, Material.DIRT, Material.OAK_PLANKS, Material.IRON_INGOT,
                Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD, Material.REDSTONE,
                Material.LAPIS_LAZULI, Material.RED_WOOL, Material.BLUE_WOOL, Material.GREEN_WOOL,
                Material.YELLOW_WOOL, Material.WHITE_WOOL, Material.BLACK_WOOL, Material.OAK_LOG,
                Material.SPRUCE_LOG, Material.COBBLESTONE, Material.SAND, Material.GRAVEL
        };
        return IntStream.range(0, n).mapToObj(i -> {
            Material mat = palette[i % palette.length];
            int idx = i;
            return InventoryItem.builder()
                    .material(mat)
                    .amount(1 + (i % 16))
                    .name(Component.text(mat.name() + " #" + idx, NamedTextColor.WHITE))
                    .lore(Component.text("Item " + idx + " / " + n, NamedTextColor.GRAY))
                    .cooldown(Duration.ofMillis(250))
                    .onLeftClick(ctx -> {
                        ctx.player().sendMessage(Component.text("Clicked " + mat.name() + " #" + idx,
                                NamedTextColor.AQUA));
                        return ClickResult.cancel();
                    })
                    .build();
        }).toList();
    }

    private static ItemStack named(Material m, String name) {
        var s = new ItemStack(m);
        var meta = s.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.WHITE));
            s.setItemMeta(meta);
        }
        return s;
    }
}
