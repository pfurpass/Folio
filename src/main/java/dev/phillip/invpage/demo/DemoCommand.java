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
import dev.phillip.invpage.i18n.Messages;
import dev.phillip.invpage.impl.InventoryRegistryImpl;
import dev.phillip.invpage.impl.SessionPersistence;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public final class DemoCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final InventoryRegistryImpl registry;
    private final SessionPersistence persistence;
    private final java.util.Set<String> disabledSubs;

    public DemoCommand(Plugin plugin, InventoryRegistryImpl registry, SessionPersistence persistence,
                       java.util.Set<String> disabledSubs) {
        this.plugin = plugin;
        this.registry = registry;
        this.persistence = persistence;
        this.disabledSubs = disabledSubs == null ? java.util.Set.of()
                : disabledSubs.stream().map(String::toLowerCase).collect(java.util.stream.Collectors.toSet());
        // Register the persistent shop factory so it can be restored on rejoin.
        registry.registerNamed("persist-shop", () -> PagedInventory.paginate(buildPersistShopPage()));
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Messages.text(null, "demo.players_only"));
            return true;
        }
        String mode = args.length > 0 ? args[0].toLowerCase() : "shop";
        if (disabledSubs.contains(mode)) {
            p.sendMessage(Component.text("This demo sub is disabled in config.yml.", NamedTextColor.RED));
            return true;
        }
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
            case "exit"    -> {
                persistence.clear(p);
                p.sendMessage(Messages.text(p, "demo.cleared", NamedTextColor.YELLOW));
            }
            default -> p.sendMessage(Messages.formatted(p, "demo.unknown", NamedTextColor.RED, mode));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            var all = List.of("shop", "anim", "async", "filter", "shared",
                    "search", "persist", "keys", "form", "exit");
            return all.stream().filter(s -> !disabledSubs.contains(s)).toList();
        }
        return List.of();
    }

    // -- Shop demo --
    private void openShop(Player p) {
        var items = sampleItems(200, p);
        var mask = MaskLayout.of("""
                BBBBBBBBB
                BXXXXXXXB
                BXXXXXXXB
                BXXXXXXXB
                BXXXXXXXB
                BBBBBBBBB
                """);
        var page = InventoryPage.builder()
                .title(Messages.text(p, "demo.shop.title", NamedTextColor.GOLD))
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

    // -- Anim demo --
    private void openAnim(Player p) {
        var colors = new Material[]{
                Material.RED_WOOL, Material.ORANGE_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL,
                Material.LIGHT_BLUE_WOOL, Material.BLUE_WOOL, Material.PURPLE_WOOL, Material.MAGENTA_WOOL
        };
        var frames = new ArrayList<Frame>();
        String label = Messages.get(p, "demo.anim.frame");
        for (var c : colors) frames.add(Frame.of(named(c, label), 6));

        var anim = AnimatedItem.builder().frames(frames).build();

        var page = InventoryPage.builder()
                .title(Messages.text(p, "demo.anim.title", NamedTextColor.AQUA))
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

    // -- Async demo --
    private void openAsync(Player p) {
        AsyncSpec spec = AsyncSpec.of(() -> CompletableFuture.supplyAsync(() -> {
                    try { Thread.sleep(1500); } catch (InterruptedException ignored) {}
                    return sampleItems(45, p);
                }))
                .placeholder(InventoryItem.of(named(Material.CLOCK, Messages.get(p, "demo.async.placeholder"))))
                .errorItem(InventoryItem.of(named(Material.BARRIER, Messages.get(p, "demo.async.error"))))
                .cache(Duration.ofMinutes(1))
                .cacheKey("demo-async")
                .build();

        var page = InventoryPage.builder()
                .title(Messages.text(p, "demo.async.title", NamedTextColor.LIGHT_PURPLE))
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

    // -- Filter demo --
    private void openFilter(Player p) {
        var items = sampleItems(200, p);
        Filter woolOnly = (i, viewer) -> i.render(viewer).getType().name().contains("WOOL");

        var toggleFilter = InventoryItem.builder()
                .material(Material.HOPPER)
                .name(Messages.text(p, "demo.filter.toggle", NamedTextColor.YELLOW))
                .onClick(ctx -> {
                    var session = (dev.phillip.invpage.impl.InventorySessionImpl) ctx.session();
                    session.setFilter(session.filter() == null ? woolOnly : null);
                    return ClickResult.refresh();
                })
                .build();

        var page = InventoryPage.builder()
                .title(Messages.text(p, "demo.filter.title", NamedTextColor.GREEN))
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

    // -- Shared demo --
    private static final AtomicInteger SHARED_CLICKS = new AtomicInteger(0);
    private static final java.util.Map<UUID, String> SHARED_LAST_CLICKER = new ConcurrentHashMap<>();

    private void openShared(Player p) {
        var counter = InventoryItem.builder()
                .binding(() -> {
                    int n = SHARED_CLICKS.get();
                    var stack = new ItemStack(Material.NETHER_STAR, Math.max(1, Math.min(64, n)));
                    var meta = stack.getItemMeta();
                    if (meta != null) {
                        meta.displayName(Messages.formatted(p, "demo.shared.counter", NamedTextColor.GOLD, n));
                        var lore = new ArrayList<Component>();
                        lore.add(Messages.text(p, "demo.shared.lore", NamedTextColor.GRAY));
                        SHARED_LAST_CLICKER.values().stream().findFirst().ifPresent(name ->
                                lore.add(Component.text(Messages.format(p, "demo.shared.last", name), NamedTextColor.YELLOW)
                                        .decoration(TextDecoration.ITALIC, false)));
                        meta.lore(lore);
                        stack.setItemMeta(meta);
                    }
                    return stack;
                })
                .onClick(ctx -> {
                    int n = SHARED_CLICKS.incrementAndGet();
                    SHARED_LAST_CLICKER.clear();
                    SHARED_LAST_CLICKER.put(ctx.player().getUniqueId(), ctx.player().getName());
                    ctx.player().sendMessage(Messages.formatted(ctx.player(), "demo.shared.youclicked", NamedTextColor.YELLOW, n));
                    return ClickResult.refresh();
                })
                .build();

        var reset = InventoryItem.builder()
                .material(Material.BARRIER)
                .name(Messages.text(p, "demo.shared.reset", NamedTextColor.RED))
                .onClick(ctx -> {
                    SHARED_CLICKS.set(0);
                    SHARED_LAST_CLICKER.clear();
                    return ClickResult.refresh();
                })
                .build();

        var page = InventoryPage.builder()
                .title(Messages.text(p, "demo.shared.title", NamedTextColor.GOLD))
                .rows(3)
                .layout(new dev.phillip.invpage.api.layout.GridLayout(true, 0))
                .navigation(NavigationButtons.disabled())
                .sessionMode(SessionMode.SHARED)
                .refreshEvery(Duration.ofMillis(100))
                .fixed(13, counter)
                .fixed(16, reset)
                .build();
        registry.open(p, PagedInventory.paginate(page));
    }

    // -- Search demo --
    private void openSearch(Player p) {
        var items = sampleItems(200, p);
        var searchButton = InventoryItem.builder()
                .material(Material.NAME_TAG)
                .name(Messages.text(p, "demo.search.button", NamedTextColor.YELLOW))
                .lore(Messages.text(p, "demo.search.lore", NamedTextColor.GRAY))
                .onClick(ctx -> {
                    SearchPrompt.builder(plugin, ctx.player())
                            .title(Messages.text(ctx.player(), "demo.search.prompt", NamedTextColor.GOLD))
                            .initial("")
                            .onConfirm(query -> {
                                String q = query == null ? "" : query.trim().toLowerCase();
                                Filter f = q.isEmpty() ? null
                                        : (i, viewer) -> i.render(viewer).getType().name().toLowerCase().contains(q);
                                var newSession = registry.open(ctx.player(), PagedInventory.paginate(buildSearchPage(items, ctx.player())));
                                if (newSession != null && f != null) ((dev.phillip.invpage.impl.InventorySessionImpl) newSession).setFilter(f);
                            })
                            .onCancel(() -> registry.open(ctx.player(), PagedInventory.paginate(buildSearchPage(items, ctx.player()))))
                            .open();
                    return ClickResult.cancel();
                })
                .build();

        var page = buildSearchPage(items, p, searchButton);
        registry.open(p, PagedInventory.paginate(page));
    }

    private InventoryPage buildSearchPage(List<InventoryItem> items, Player viewer, InventoryItem searchButton) {
        return InventoryPage.builder()
                .title(Messages.text(viewer, "demo.search.title", NamedTextColor.GOLD))
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

    private InventoryPage buildSearchPage(List<InventoryItem> items, Player viewer) {
        return buildSearchPage(items, viewer, InventoryItem.builder()
                .material(Material.NAME_TAG)
                .name(Messages.text(viewer, "demo.search.button", NamedTextColor.YELLOW))
                .build());
    }

    // -- Persist demo --
    private void openPersist(Player p) {
        registry.open(p, PagedInventory.paginate(buildPersistShopPage()));
    }

    private InventoryPage buildPersistShopPage() {
        // Note: persist factory has no Player context (rebuild on rejoin) — uses default English titles for safety.
        // Item-level texts go through Messages at render-time anyway (binding).
        var items = sampleItems(100, null);
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

    // -- Keys demo --
    private void openKeys(Player p) {
        var info = InventoryItem.builder()
                .material(Material.BOOK)
                .name(Messages.text(p, "demo.keys.info", NamedTextColor.YELLOW))
                .lore(
                        Messages.text(p, "demo.keys.line1", NamedTextColor.GRAY),
                        Messages.text(p, "demo.keys.line2", NamedTextColor.GRAY),
                        Messages.text(p, "demo.keys.line3", NamedTextColor.GRAY),
                        Messages.text(p, "demo.keys.line4", NamedTextColor.GRAY)
                )
                .build();

        var b = InventoryPage.builder()
                .title(Messages.text(p, "demo.keys.title", NamedTextColor.LIGHT_PURPLE))
                .rows(3)
                .layout(new dev.phillip.invpage.api.layout.GridLayout(true, 0))
                .navigation(NavigationButtons.disabled())
                .fixed(13, info)
                .hotbarBind(1, ctx -> {
                    ctx.player().sendMessage(Messages.text(ctx.player(), "demo.keys.hello", NamedTextColor.GREEN));
                    return ClickResult.cancel();
                })
                .hotbarBind(2, ctx -> ClickResult.refresh())
                .hotbarBind(3, ctx -> ClickResult.close());

        for (int k = 4; k <= 9; k++) {
            int key = k;
            b.hotbarBind(k, ctx -> {
                ctx.player().sendMessage(Messages.formatted(ctx.player(), "demo.keys.pressed", NamedTextColor.AQUA, key));
                return ClickResult.cancel();
            });
        }
        registry.open(p, PagedInventory.paginate(b.build()));
    }

    // -- Form demo --
    private void openForm(Player p) {
        var label = InventoryItem.of(namedRaw(p, Material.OAK_SIGN, "demo.form.label"));

        var confirm = InventoryItem.builder()
                .material(Material.EMERALD)
                .name(Messages.text(p, "demo.form.confirm", NamedTextColor.GREEN))
                .lore(Messages.text(p, "demo.form.confirm_lore", NamedTextColor.GRAY))
                .onClick(ctx -> {
                    var inv = ctx.session().inventoryFor(ctx.player());
                    int filled = 0;
                    var names = new ArrayList<String>();
                    for (int slot : new int[]{11, 13, 15}) {
                        var it = inv.getItem(slot);
                        if (it != null && !it.getType().isAir()) { filled++; names.add(it.getType().name()); }
                    }
                    if (filled < 3) {
                        ctx.player().sendMessage(Messages.formatted(ctx.player(), "demo.form.notfilled", NamedTextColor.RED, filled));
                        return ClickResult.cancel();
                    }
                    ctx.player().sendMessage(Messages.formatted(ctx.player(), "demo.form.ok", NamedTextColor.GREEN, String.join(", ", names)));
                    for (int slot : new int[]{11, 13, 15}) inv.setItem(slot, null);
                    return ClickResult.refresh();
                })
                .build();

        var page = InventoryPage.builder()
                .title(Messages.text(p, "demo.form.title", NamedTextColor.GOLD))
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
    private static List<InventoryItem> sampleItems(int n, Player p) {
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
            int total = n;
            return InventoryItem.builder()
                    .material(mat)
                    .amount(1 + (i % 16))
                    .name(Component.text(mat.name() + " #" + idx, NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false))
                    .lore(Component.text(p == null ? "Item " + idx + " / " + total
                                                  : Messages.format(p, "demo.shop.itemlbl", idx, total),
                            NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false))
                    .cooldown(Duration.ofMillis(250))
                    .onLeftClick(ctx -> {
                        ctx.player().sendMessage(Messages.formatted(ctx.player(), "demo.shop.click",
                                NamedTextColor.AQUA, mat.name(), idx));
                        return ClickResult.cancel();
                    })
                    .build();
        }).toList();
    }

    private static ItemStack named(Material m, String name) {
        var s = new ItemStack(m);
        var meta = s.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name).color(NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            s.setItemMeta(meta);
        }
        return s;
    }

    private static ItemStack namedRaw(Player p, Material m, String key) {
        var s = new ItemStack(m);
        var meta = s.getItemMeta();
        if (meta != null) {
            meta.displayName(Messages.text(p, key, NamedTextColor.YELLOW));
            s.setItemMeta(meta);
        }
        return s;
    }
}
