package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.SearchPrompt;
import dev.phillip.invpage.api.click.ClickContext;
import dev.phillip.invpage.api.click.ClickHandler;
import dev.phillip.invpage.api.click.ClickResult;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.view.AnvilView;

public final class InventoryListener implements Listener {

    private final InventoryRegistryImpl registry;
    private final SessionPersistence persistence;

    public InventoryListener(InventoryRegistryImpl registry, SessionPersistence persistence) {
        this.registry = registry;
        this.persistence = persistence;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // -- Anvil-Search routing --
        var prompt = SearchPrompt.activeFor(player);
        if (prompt != null && event.getView() == prompt.view()) {
            handleAnvilSearchClick(event, prompt, player);
            return;
        }

        var session = registry.byPlayer(player);
        if (session == null) return;
        var inv = session.inventoryFor(player);
        if (inv == null) return;

        boolean inTop = event.getClickedInventory() != null && event.getClickedInventory() == inv;
        var page = session.paged().template();

        // -- Hotbar binds: player presses 1..9 anywhere over the GUI --
        if (event.getClick() == ClickType.NUMBER_KEY && inTop && !page.hotbarBinds().isEmpty()) {
            int key = event.getHotbarButton() + 1;
            ClickHandler hb = page.hotbarBinds().get(key);
            if (hb != null) {
                event.setCancelled(true);
                var ctx = new ClickContext(player, event.getClick(), event.getSlot(),
                        event.getCurrentItem(), null, page, session);
                applyResult(hb.handle(ctx), ctx, event);
                return;
            }
        }

        // -- Input slots: don't cancel; let the player place / take items --
        if (inTop && page.inputSlots().contains(event.getSlot())) {
            return; // event NOT cancelled
        }

        event.setCancelled(true);
        if (!inTop) return;

        int slot = event.getSlot();
        var slotMap = SlotMap.compute(session.paged(), session.currentPage(), session.asyncItems(),
                session.filter(), player, session.isAsyncLoading(), session.isAsyncFailed());
        InventoryItem item = slotMap.get(slot);
        if (item == null) return;

        if (!item.clickableFor(player)) {
            playSound(player, page.theme().deniedSound());
            return;
        }

        if (item.cooldown() != null
                && !registry.cooldowns().tryConsume(item.id(), player.getUniqueId(), item.cooldown())) {
            playSound(player, page.theme().deniedSound());
            return;
        }

        var ctx = new ClickContext(player, event.getClick(), slot,
                event.getCurrentItem(), item, page, session);

        ClickHandler[] chain = new ClickHandler[]{
                item.handlerFor(event.getClick()),
                item.globalHandler(),
                page.globalClickHandler()
        };

        Runnable dispatch = () -> {
            ClickResult result = ClickResult.cancel();
            for (ClickHandler h : chain) {
                if (h == null) continue;
                result = h.handle(ctx);
                if (result != null && !(result instanceof ClickResult.Cancel)) break;
            }
            applyResult(result, ctx, event);
        };

        if (item.isAsync()) {
            Bukkit.getScheduler().runTaskAsynchronously(registry.plugin(), dispatch);
        } else {
            dispatch.run();
            playSound(player, page.theme().clickSound());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        var session = registry.byPlayer(player);
        if (session == null) return;
        var inv = session.inventoryFor(player);
        if (inv == null) return;

        var inputSlots = session.paged().template().inputSlots();
        // Cancel any drag that touches a top-inv slot that is NOT an input slot.
        for (int slot : event.getRawSlots()) {
            if (slot >= inv.getSize()) continue;
            if (!inputSlots.contains(slot)) { event.setCancelled(true); return; }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        // -- Anvil-Search close --
        var prompt = SearchPrompt.activeFor(player);
        if (prompt != null && event.getView() == prompt.view()) {
            SearchPrompt.clear(player);
            if (prompt.onCancel() != null) prompt.onCancel().run();
            return;
        }

        var session = registry.byPlayer(player);
        if (session == null) return;
        if (event.getInventory() == session.inventoryFor(player)) {
            // Persistence: save (key, currentPage) before close cleans up.
            var key = session.paged().template().persistentKey();
            if (key != null) persistence.save(player, key, session.currentPage());

            // Form/Input mode: return any items left in input slots to the player so they don't lose them.
            var inputSlots = session.paged().template().inputSlots();
            if (!inputSlots.isEmpty()) {
                for (int s : inputSlots) {
                    ItemStack item = event.getInventory().getItem(s);
                    if (item != null && !item.getType().isAir()) {
                        var leftover = player.getInventory().addItem(item);
                        leftover.values().forEach(it -> player.getWorld().dropItem(player.getLocation(), it));
                    }
                }
            }

            registry.handleClose(player);
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        var p = event.getView().getPlayer();
        if (!(p instanceof Player player)) return;
        var prompt = SearchPrompt.activeFor(player);
        if (prompt == null || event.getView() != prompt.view()) return;

        String text = event.getInventory().getRenameText();
        if (text == null) text = "";

        ItemStack base = event.getInventory().getItem(0);
        if (base == null) return;
        ItemStack result = base.clone();
        var meta = result.getItemMeta();
        if (meta != null) {
            meta.displayName(net.kyori.adventure.text.Component.text(text.isEmpty() ? " " : text)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            result.setItemMeta(meta);
        }
        event.setResult(result);

        if (event.getView() instanceof AnvilView av) {
            av.setRepairCost(0);
            av.setMaximumRepairCost(0);
        }

        if (prompt.onChange() != null) {
            String captured = text;
            Bukkit.getScheduler().runTask(registry.plugin(), () -> prompt.onChange().accept(captured));
        }
    }

    private void handleAnvilSearchClick(InventoryClickEvent event, SearchPrompt.Active prompt, Player player) {
        event.setCancelled(true);
        if (event.getRawSlot() != 2) return; // Only result-slot confirms.
        ItemStack result = event.getCurrentItem();
        String text = "";
        if (result != null && result.getItemMeta() != null && result.getItemMeta().hasDisplayName()) {
            text = PlainTextComponentSerializer.plainText().serialize(result.getItemMeta().displayName());
        } else {
            text = (event.getInventory() instanceof AnvilInventory av) ? av.getRenameText() : "";
            if (text == null) text = "";
        }
        SearchPrompt.clear(player);
        String captured = text;
        Bukkit.getScheduler().runTask(registry.plugin(), () -> {
            player.closeInventory();
            if (prompt.onConfirm() != null) prompt.onConfirm().accept(captured);
        });
    }

    private void applyResult(ClickResult result, ClickContext ctx, InventoryClickEvent event) {
        if (result == null) return;
        var session = (InventorySessionImpl) ctx.session();
        switch (result) {
            case ClickResult.Allow ignored -> {
                if (Bukkit.isPrimaryThread()) event.setCancelled(false);
            }
            case ClickResult.Cancel ignored -> { /* already cancelled */ }
            case ClickResult.Close ignored -> {
                if (Bukkit.isPrimaryThread()) ctx.player().closeInventory();
                else Bukkit.getScheduler().runTask(registry.plugin(), () -> ctx.player().closeInventory());
            }
            case ClickResult.Refresh ignored -> {
                if (Bukkit.isPrimaryThread()) session.refresh();
                else Bukkit.getScheduler().runTask(registry.plugin(), session::refresh);
            }
            case ClickResult.Navigate nav -> {
                if (Bukkit.isPrimaryThread()) session.switchPage(nav.targetPage());
                else Bukkit.getScheduler().runTask(registry.plugin(), () -> session.switchPage(nav.targetPage()));
                playSound(ctx.player(), session.paged().template().theme().pageSwitchSound());
            }
        }
    }

    private void playSound(Player p, org.bukkit.Sound s) {
        if (s == null) return;
        p.playSound(p.getLocation(), s, 0.6f, 1.0f);
    }
}
