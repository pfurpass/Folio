package dev.phillip.invpage.api.search;

import dev.phillip.invpage.api.InventoryItem;
import org.bukkit.entity.Player;

@FunctionalInterface
public interface Filter {
    boolean test(InventoryItem item, Player viewer);

    default Filter and(Filter other) { return (i, p) -> test(i, p) && other.test(i, p); }
    default Filter or(Filter other)  { return (i, p) -> test(i, p) || other.test(i, p); }
    default Filter negate()          { return (i, p) -> !test(i, p); }

    Filter PASS = (i, p) -> true;
    Filter NONE = (i, p) -> false;
}
