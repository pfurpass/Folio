package dev.phillip.invpage;

import dev.phillip.invpage.api.InventoryRegistry;
import dev.phillip.invpage.demo.DemoCommand;
import dev.phillip.invpage.i18n.Messages;
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
        saveDefaultConfig();
        loadLanguageFiles();

        Messages.setForcedLanguage(getConfig().getString("language", "auto"));

        this.registry = new InventoryRegistryImpl(this);
        var persistence = new SessionPersistence(this, registry);
        getServer().getPluginManager().registerEvents(new InventoryListener(registry, persistence), this);
        getServer().getPluginManager().registerEvents(persistence, this);

        boolean demoEnabled = getConfig().getBoolean("demo.enabled", true);
        var cmd = getCommand("foliodemo");
        if (cmd != null) {
            if (demoEnabled) {
                var disabled = new java.util.HashSet<>(getConfig().getStringList("demo.disabled-subs"));
                var demo = new DemoCommand(this, registry, persistence, disabled);
                cmd.setExecutor(demo);
                cmd.setTabCompleter(demo);
            } else {
                cmd.setExecutor((sender, c, label, args) -> {
                    sender.sendMessage("§cThe /foliodemo command is disabled in config.yml (demo.enabled: false).");
                    return true;
                });
            }
        }

        String langInfo = Messages.forcedLanguage() == null ? "auto" : Messages.forcedLanguage();
        getLogger().info("Folio ready (demo=" + (demoEnabled ? "on" : "off") + ", lang=" + langInfo + ").");
    }

    @Override
    public void onDisable() {
        if (registry != null) registry.shutdown();
        instance = null;
    }

    public static InventoryPagePlugin instance() { return instance; }
    public InventoryRegistry registry() { return registry; }

    private void loadLanguageFiles() {
        // Save bundled defaults so users have editable copies on first run.
        for (String f : new String[]{"lang/en.yml", "lang/de.yml"}) {
            if (!new java.io.File(getDataFolder(), f).exists()) saveResource(f, false);
        }

        java.io.File langDir = new java.io.File(getDataFolder(), "lang");
        if (!langDir.isDirectory()) return;

        java.io.File[] files = langDir.listFiles((d, n) -> {
            String l = n.toLowerCase();
            return l.endsWith(".yml") || l.endsWith(".yaml");
        });
        if (files == null) return;

        for (java.io.File f : files) {
            String langCode = f.getName().replaceFirst("\\.[^.]+$", "").toLowerCase();
            var cfg = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            var entries = new java.util.HashMap<String, String>();
            for (String key : cfg.getKeys(true)) {
                String val = cfg.getString(key);
                if (val != null) entries.put(key, val);   // null = section, not a leaf
            }
            Messages.load(langCode, entries);
            getLogger().info("Loaded language '" + langCode + "' (" + entries.size() + " keys) from " + f.getName());
        }
    }
}
