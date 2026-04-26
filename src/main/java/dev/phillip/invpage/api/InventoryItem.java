package dev.phillip.invpage.api;

import dev.phillip.invpage.api.click.ClickHandler;
import dev.phillip.invpage.api.click.ClickResult;
import dev.phillip.invpage.api.click.CooldownPolicy;
import dev.phillip.invpage.api.gate.PermissionGate;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public interface InventoryItem {

    UUID id();

    /** Render the visible ItemStack for the given player. May reflect dynamic bindings. */
    ItemStack render(Player viewer);

    boolean visibleFor(Player viewer);
    boolean clickableFor(Player viewer);

    @Nullable ClickHandler handlerFor(ClickType type);
    @Nullable ClickHandler globalHandler();
    @Nullable CooldownPolicy cooldown();
    boolean isAsync();

    static Builder builder() { return new dev.phillip.invpage.impl.InventoryItemImpl.BuilderImpl(); }

    /** Convenience: a static, non-clickable display item. */
    static InventoryItem of(ItemStack stack) {
        return builder().stack(stack).onClick(ctx -> ClickResult.cancel()).build();
    }

    interface Builder {
        Builder stack(ItemStack stack);
        Builder material(Material material);
        Builder amount(int amount);
        Builder name(Component name);
        Builder lore(Component... lines);
        Builder lore(List<Component> lore);
        Builder glow(boolean glow);
        Builder modelData(int data);

        /** Reactive binding — re-evaluated on every refresh(). */
        Builder binding(Supplier<ItemStack> supplier);

        Builder visibleIf(PermissionGate gate);
        Builder clickableIf(PermissionGate gate);
        Builder requirePermission(String node);

        Builder cooldown(Duration duration);
        Builder cooldown(CooldownPolicy policy);

        Builder onClick(ClickHandler handler);
        Builder onLeftClick(ClickHandler handler);
        Builder onRightClick(ClickHandler handler);
        Builder onShiftClick(ClickHandler handler);
        Builder onDrop(ClickHandler handler);
        Builder onNumberKey(ClickHandler handler);
        Builder onClickAsync(ClickHandler handler);

        InventoryItem build();
    }
}
