package dev.phillip.invpage.api.gate;

import org.bukkit.entity.Player;
import java.util.function.Predicate;

@FunctionalInterface
public interface PermissionGate extends Predicate<Player> {

    PermissionGate ALWAYS = p -> true;
    PermissionGate NEVER = p -> false;

    static PermissionGate hasPermission(String node) {
        return p -> p.hasPermission(node);
    }
    static PermissionGate any(PermissionGate... gates) {
        return p -> { for (var g : gates) if (g.test(p)) return true; return false; };
    }
    static PermissionGate all(PermissionGate... gates) {
        return p -> { for (var g : gates) if (!g.test(p)) return false; return true; };
    }
}
