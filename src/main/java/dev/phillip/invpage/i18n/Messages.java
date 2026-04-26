package dev.phillip.invpage.i18n;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Tiny per-language message table. Picks language from {@code player.locale().getLanguage()}.
 * Fallback chain: requested lang → English → key (so missing keys still produce something visible).
 *
 *  Supported: "en" (default), "de".
 *
 *  Add a new language by inserting another inner map below.
 */
public final class Messages {

    private static final String DEFAULT_LANG = "en";
    private static final Map<String, Map<String, String>> TABLE = new HashMap<>();

    /** When non-null, every lookup uses this language regardless of player locale. */
    private static volatile String forced = null;

    /**
     * Override per-player locale detection. Pass {@code null} or "auto" to restore per-player behaviour.
     * Unknown languages fall back to English.
     */
    public static void setForcedLanguage(String lang) {
        if (lang == null || lang.isBlank() || lang.equalsIgnoreCase("auto")) {
            forced = null;
        } else {
            forced = lang.toLowerCase();
        }
    }

    public static String forcedLanguage() { return forced; }

    /**
     * Merge entries into a language table (creates it if missing). Existing keys are overridden.
     * Pass values from a YamlConfiguration via {@code cfg.getKeys(true)} + {@code cfg.getString(k)}.
     */
    public static void load(String lang, java.util.Map<String, String> entries) {
        if (lang == null || entries == null) return;
        String l = lang.toLowerCase();
        TABLE.computeIfAbsent(l, k -> new HashMap<>()).putAll(entries);
    }

    public static java.util.Set<String> availableLanguages() {
        return java.util.Set.copyOf(TABLE.keySet());
    }

    static {
        var en = new HashMap<String, String>();
        var de = new HashMap<String, String>();

        // -- Framework defaults (nav buttons, empty result, search title) --
        put(en, de, "nav.first",      "« First page",     "« Erste Seite");
        put(en, de, "nav.previous",   "‹ Back",           "‹ Zurück");
        put(en, de, "nav.next",       "Next ›",           "Weiter ›");
        put(en, de, "nav.last",       "Last page »",      "Letzte Seite »");
        put(en, de, "result.empty",   "No results",       "Keine Ergebnisse");
        put(en, de, "search.title",   "Search",           "Suche");

        // -- Demo command messages --
        put(en, de, "demo.unknown",   "Unknown demo: %s", "Unbekannte Demo: %s");
        put(en, de, "demo.cleared",   "Persistence cleared.", "Persistenz gelöscht.");
        put(en, de, "demo.players_only", "Players only.", "Nur für Spieler.");

        // shop demo
        put(en, de, "demo.shop.title",  "Demo Shop",     "Demo-Shop");
        put(en, de, "demo.shop.click",  "Clicked %s #%d", "Geklickt: %s #%d");
        put(en, de, "demo.shop.itemlbl","Item %d / %d",  "Item %d / %d");

        // anim demo
        put(en, de, "demo.anim.title", "Animation",      "Animation");
        put(en, de, "demo.anim.frame", "Cycling",        "Wechselnd");

        // async demo
        put(en, de, "demo.async.title",       "Async Loading",  "Async Laden");
        put(en, de, "demo.async.placeholder", "§7Loading…",     "§7Lade …");
        put(en, de, "demo.async.error",       "§cError",        "§cFehler");

        // filter demo
        put(en, de, "demo.filter.title",  "Filter Demo",       "Filter-Demo");
        put(en, de, "demo.filter.toggle", "Toggle: wool only", "Umschalten: nur Wolle");

        // shared demo
        put(en, de, "demo.shared.title",   "Co-Op GUI",                       "Co-Op GUI");
        put(en, de, "demo.shared.counter", "Shared click counter: %d",        "Geteilter Klick-Zähler: %d");
        put(en, de, "demo.shared.lore",    "Click = +1 for all viewers",      "Klick = +1 für ALLE Viewer");
        put(en, de, "demo.shared.last",    "Last click: %s",                  "Letzter Klick: %s");
        put(en, de, "demo.shared.youclicked", "You clicked → %d",             "Du hast geklickt → %d");
        put(en, de, "demo.shared.reset",   "Reset counter",                   "Zähler zurücksetzen");

        // search demo
        put(en, de, "demo.search.title",      "Anvil Search",                "Anvil-Suche");
        put(en, de, "demo.search.button",     "Type to search…",             "Suche tippen…");
        put(en, de, "demo.search.lore",       "Click opens anvil input",     "Klick öffnet Anvil-Eingabe");
        put(en, de, "demo.search.prompt",     "Search",                      "Suche");

        // persist demo
        put(en, de, "demo.persist.title",  "Persistent Shop",                "Persistenter Shop");

        // keys demo
        put(en, de, "demo.keys.title",     "Hotbar Keybinds",                "Hotbar-Keybinds");
        put(en, de, "demo.keys.info",      "Press 1..9 while hovering here", "Drücke 1..9 während du hier hoverst");
        put(en, de, "demo.keys.line1",     "1 = Hello",                      "1 = Hallo");
        put(en, de, "demo.keys.line2",     "2 = Refresh",                    "2 = Refresh");
        put(en, de, "demo.keys.line3",     "3 = Close",                      "3 = Schließen");
        put(en, de, "demo.keys.line4",     "4..9 = Number to chat",          "4..9 = Zahl an Chat");
        put(en, de, "demo.keys.hello",     "Hello!",                         "Hallo!");
        put(en, de, "demo.keys.pressed",   "You pressed %d",                 "Du hast %d gedrückt");

        // form demo
        put(en, de, "demo.form.title",     "Form Mode",                                "Form-Modus");
        put(en, de, "demo.form.label",     "§ePlace 3 items, then click the emerald", "§eLeg 3 Items rein und klick Smaragd");
        put(en, de, "demo.form.confirm",   "Confirm",                                  "Bestätigen");
        put(en, de, "demo.form.confirm_lore","Validates: all 3 slots filled?",         "Validiert: alle 3 Slots gefüllt?");
        put(en, de, "demo.form.notfilled", "Only %d/3 slots filled!",                  "Nur %d/3 Slots gefüllt!");
        put(en, de, "demo.form.ok",        "OK: %s",                                   "OK: %s");

        TABLE.put("en", en);
        TABLE.put("de", de);
    }

    private static void put(Map<String, String> en, Map<String, String> de, String key, String enVal, String deVal) {
        en.put(key, enVal);
        de.put(key, deVal);
    }

    private Messages() {}

    public static String lang(Player p) {
        if (forced != null) return TABLE.containsKey(forced) ? forced : DEFAULT_LANG;
        if (p == null) return DEFAULT_LANG;
        try {
            var locale = p.locale();
            String langCode = locale.getLanguage().toLowerCase();
            String country  = locale.getCountry().toLowerCase();

            if (!country.isEmpty()) {
                // 1) lang_country — e.g. "de_at"
                String full = langCode + "_" + country;
                if (TABLE.containsKey(full)) return full;
                // 2) lang-country — e.g. "de-at"
                String hyphen = langCode + "-" + country;
                if (TABLE.containsKey(hyphen)) return hyphen;
            }
            // 3) lang only — e.g. "de"
            if (TABLE.containsKey(langCode)) return langCode;
            // 4) fallback
            return DEFAULT_LANG;
        } catch (Exception e) {
            return DEFAULT_LANG;
        }
    }

    public static String get(Player p, String key) {
        var map = TABLE.get(lang(p));
        String s = map == null ? null : map.get(key);
        if (s != null) return s;
        var def = TABLE.get(DEFAULT_LANG);
        s = def == null ? null : def.get(key);
        return s != null ? s : key;
    }

    public static String format(Player p, String key, Object... args) {
        try { return String.format(get(p, key), args); }
        catch (Exception e) { return get(p, key); }
    }

    public static Component text(Player p, String key) {
        return Component.text(get(p, key)).decoration(TextDecoration.ITALIC, false);
    }

    public static Component text(Player p, String key, NamedTextColor color) {
        return Component.text(get(p, key), color).decoration(TextDecoration.ITALIC, false);
    }

    public static Component formatted(Player p, String key, NamedTextColor color, Object... args) {
        return Component.text(format(p, key, args), color).decoration(TextDecoration.ITALIC, false);
    }
}
