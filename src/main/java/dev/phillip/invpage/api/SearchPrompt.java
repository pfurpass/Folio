package dev.phillip.invpage.api;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Opens an Anvil-GUI as a one-shot text-input prompt.
 *  - Live keystrokes fire `onChange(text)` (every PrepareAnvilEvent).
 *  - Clicking the result slot fires `onConfirm(text)`.
 *  - Closing the anvil without confirming fires `onCancel()`.
 */
public final class SearchPrompt {

    public record Active(
            Plugin plugin,
            Player player,
            @Nullable Consumer<String> onChange,
            @Nullable Consumer<String> onConfirm,
            @Nullable Runnable onCancel,
            AnvilView view
    ) {}

    private static final java.util.Map<UUID, Active> active = new ConcurrentHashMap<>();

    public static @Nullable Active activeFor(Player p) { return active.get(p.getUniqueId()); }
    public static void clear(Player p) { active.remove(p.getUniqueId()); }

    public static Builder builder(Plugin plugin, Player player) { return new Builder(plugin, player); }

    public static final class Builder {
        private final Plugin plugin;
        private final Player player;
        private String initial = "";
        private Component title;
        private Consumer<String> onChange;
        private Consumer<String> onConfirm;
        private Runnable onCancel;

        Builder(Plugin plugin, Player player) {
            this.plugin = plugin;
            this.player = player;
            this.title = dev.phillip.invpage.i18n.Messages.text(player, "search.title", NamedTextColor.GOLD);
        }

        public Builder initial(String s) { this.initial = s == null ? "" : s; return this; }
        public Builder title(Component c) { this.title = c; return this; }
        public Builder onChange(Consumer<String> c) { this.onChange = c; return this; }
        public Builder onConfirm(Consumer<String> c) { this.onConfirm = c; return this; }
        public Builder onCancel(Runnable r) { this.onCancel = r; return this; }

        /** Open the anvil. Internally schedules to next tick — safe to call from a click handler. */
        public void open() {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                player.closeInventory();

                AnvilView view = MenuType.ANVIL.create(player, title);

                ItemStack paper = new ItemStack(Material.PAPER);
                ItemMeta m = paper.getItemMeta();
                if (m != null) {
                    m.displayName(Component.text(initial.isEmpty() ? " " : initial, NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false));
                    paper.setItemMeta(m);
                }
                AnvilInventory anvilInv = view.getTopInventory();
                anvilInv.setFirstItem(paper);

                view.open();
                view.setRepairCost(0);
                view.setMaximumRepairCost(0);

                active.put(player.getUniqueId(), new Active(plugin, player, onChange, onConfirm, onCancel, view));
            });
        }
    }
}
