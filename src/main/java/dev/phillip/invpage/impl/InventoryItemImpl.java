package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.click.ClickHandler;
import dev.phillip.invpage.api.click.ClickResult;
import dev.phillip.invpage.api.click.CooldownPolicy;
import dev.phillip.invpage.api.gate.PermissionGate;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

public final class InventoryItemImpl implements InventoryItem {

    private final UUID id;
    private final Supplier<ItemStack> renderFn;
    private final PermissionGate visible;
    private final PermissionGate clickable;
    private final Map<ClickType, ClickHandler> typedHandlers;
    private final ClickHandler global;
    private final CooldownPolicy cooldown;
    private final boolean async;

    InventoryItemImpl(UUID id, Supplier<ItemStack> renderFn, PermissionGate visible, PermissionGate clickable,
                      Map<ClickType, ClickHandler> typedHandlers, ClickHandler global,
                      CooldownPolicy cooldown, boolean async) {
        this.id = id;
        this.renderFn = renderFn;
        this.visible = visible;
        this.clickable = clickable;
        this.typedHandlers = typedHandlers;
        this.global = global;
        this.cooldown = cooldown;
        this.async = async;
    }

    @Override public UUID id() { return id; }
    @Override public ItemStack render(Player viewer) { return renderFn.get(); }
    @Override public boolean visibleFor(Player viewer) { return visible.test(viewer); }
    @Override public boolean clickableFor(Player viewer) { return clickable.test(viewer); }

    @Override
    public @Nullable ClickHandler handlerFor(ClickType type) {
        // Map composite types to canonical buckets.
        if (typedHandlers.isEmpty()) return null;
        var direct = typedHandlers.get(type);
        if (direct != null) return direct;
        if (type.isShiftClick()) {
            var h = typedHandlers.get(ClickType.SHIFT_LEFT);
            if (h != null) return h;
        }
        if (type.isLeftClick())  return typedHandlers.get(ClickType.LEFT);
        if (type.isRightClick()) return typedHandlers.get(ClickType.RIGHT);
        return null;
    }

    @Override public @Nullable ClickHandler globalHandler() { return global; }
    @Override public @Nullable CooldownPolicy cooldown() { return cooldown; }
    @Override public boolean isAsync() { return async; }

    public static final class BuilderImpl implements Builder {
        private ItemStack baseStack;
        private Material material = Material.STONE;
        private int amount = 1;
        private Component name;
        private List<Component> lore = new ArrayList<>();
        private boolean glow;
        private Integer modelData;
        private Supplier<ItemStack> binding;

        private PermissionGate visible = PermissionGate.ALWAYS;
        private PermissionGate clickable = PermissionGate.ALWAYS;
        private final Map<ClickType, ClickHandler> handlers = new EnumMap<>(ClickType.class);
        private ClickHandler global;
        private CooldownPolicy cooldown;
        private boolean async;

        @Override public Builder stack(ItemStack stack) { this.baseStack = stack.clone(); return this; }
        @Override public Builder material(Material m) { this.material = m; return this; }
        @Override public Builder amount(int a) { this.amount = a; return this; }
        @Override public Builder name(Component n) { this.name = n; return this; }
        @Override public Builder lore(Component... lines) {
            this.lore = new ArrayList<>();
            for (var c : lines) this.lore.add(c);
            return this;
        }
        @Override public Builder lore(List<Component> lore) { this.lore = new ArrayList<>(lore); return this; }
        @Override public Builder glow(boolean g) { this.glow = g; return this; }
        @Override public Builder modelData(int d) { this.modelData = d; return this; }
        @Override public Builder binding(Supplier<ItemStack> supplier) { this.binding = supplier; return this; }

        @Override public Builder visibleIf(PermissionGate gate) { this.visible = gate; return this; }
        @Override public Builder clickableIf(PermissionGate gate) { this.clickable = gate; return this; }
        @Override public Builder requirePermission(String node) {
            this.visible = visible == PermissionGate.ALWAYS ? PermissionGate.hasPermission(node)
                                                            : PermissionGate.all(visible, PermissionGate.hasPermission(node));
            this.clickable = clickable == PermissionGate.ALWAYS ? PermissionGate.hasPermission(node)
                                                                : PermissionGate.all(clickable, PermissionGate.hasPermission(node));
            return this;
        }

        @Override public Builder cooldown(Duration duration) { this.cooldown = CooldownPolicy.perPlayer(duration); return this; }
        @Override public Builder cooldown(CooldownPolicy policy) { this.cooldown = policy; return this; }

        @Override public Builder onClick(ClickHandler h) { this.global = h; return this; }
        @Override public Builder onLeftClick(ClickHandler h) { handlers.put(ClickType.LEFT, h); return this; }
        @Override public Builder onRightClick(ClickHandler h) { handlers.put(ClickType.RIGHT, h); return this; }
        @Override public Builder onShiftClick(ClickHandler h) {
            handlers.put(ClickType.SHIFT_LEFT, h);
            handlers.put(ClickType.SHIFT_RIGHT, h);
            return this;
        }
        @Override public Builder onDrop(ClickHandler h) {
            handlers.put(ClickType.DROP, h);
            handlers.put(ClickType.CONTROL_DROP, h);
            return this;
        }
        @Override public Builder onNumberKey(ClickHandler h) { handlers.put(ClickType.NUMBER_KEY, h); return this; }
        @Override public Builder onClickAsync(ClickHandler h) {
            this.async = true;
            this.global = h;
            return this;
        }

        @Override public InventoryItem build() {
            Supplier<ItemStack> render;
            if (binding != null) {
                Supplier<ItemStack> b = binding;
                render = b;
            } else {
                final ItemStack baked = bake();
                render = () -> baked.clone();
            }
            return new InventoryItemImpl(UUID.randomUUID(), render, visible, clickable,
                    handlers.isEmpty() ? Map.of() : new EnumMap<>(handlers),
                    global == null ? (global = ctx -> ClickResult.cancel()) : global,
                    cooldown, async);
        }

        private ItemStack bake() {
            ItemStack s = baseStack != null ? baseStack.clone() : new ItemStack(material, Math.max(1, amount));
            if (amount > 0 && baseStack == null) s.setAmount(amount);
            ItemMeta m = s.getItemMeta();
            if (m != null) {
                if (name != null) m.displayName(name.decoration(TextDecoration.ITALIC, false));
                if (!lore.isEmpty()) {
                    var l = new ArrayList<Component>(lore.size());
                    for (var line : lore) l.add(line.decoration(TextDecoration.ITALIC, false));
                    m.lore(l);
                }
                if (glow) {
                    m.addEnchant(Enchantment.UNBREAKING, 1, true);
                    m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }
                if (modelData != null) m.setCustomModelData(modelData);
                s.setItemMeta(m);
            }
            return s;
        }
    }
}
