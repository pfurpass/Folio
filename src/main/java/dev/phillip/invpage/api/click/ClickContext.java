package dev.phillip.invpage.api.click;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.InventoryPage;
import dev.phillip.invpage.api.InventorySession;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

public record ClickContext(
        Player player,
        ClickType type,
        int slot,
        @Nullable ItemStack itemStack,
        @Nullable InventoryItem item,
        InventoryPage page,
        InventorySession session
) {
    public boolean isLeft() { return type.isLeftClick(); }
    public boolean isRight() { return type.isRightClick(); }
    public boolean isShift() { return type.isShiftClick(); }
    public boolean isDrop() {
        return type == ClickType.DROP || type == ClickType.CONTROL_DROP;
    }
    public boolean isNumberKey() { return type == ClickType.NUMBER_KEY; }
}
