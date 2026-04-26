package dev.phillip.invpage;

import dev.phillip.invpage.api.InventoryRegistry;
import dev.phillip.invpage.demo.DemoCommand;
import dev.phillip.invpage.impl.InventoryListener;
import dev.phillip.invpage.impl.InventoryRegistryImpl;
import dev.phillip.invpage.impl.SessionPersistence;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventoryPagePlugin extends JavaPlugin {

    private static InventoryPagePlugin instance;
    private InventoryRegistryImpl registry;

    @Override
    public void onEnable() {
        instance = this;
        this.registry = new InventoryRegistryImpl(this);
        var persistence = new SessionPersistence(this, registry);
        getServer().getPluginManager().registerEvents(new InventoryListener(registry, persistence), this);
        getServer().getPluginManager().registerEvents(persistence, this);

        var cmd = getCommand("foliodemo");
        if (cmd != null) {
            var demo = new DemoCommand(this, registry, persistence);
            cmd.setExecutor(demo);
            cmd.setTabCompleter(demo);
        }

        getLogger().info("Folio ready.");
    }

    @Override
    public void onDisable() {
        if (registry != null) registry.shutdown();
        instance = null;
    }

    public static InventoryPagePlugin instance() { return instance; }
    public InventoryRegistry registry() { return registry; }
}
