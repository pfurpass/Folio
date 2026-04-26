package dev.phillip.invpage.api.theme;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.jetbrains.annotations.Nullable;

public record InventoryTheme(
        Material borderMaterial,
        Component borderName,
        @Nullable Sound openSound,
        @Nullable Sound clickSound,
        @Nullable Sound pageSwitchSound,
        @Nullable Sound deniedSound,
        String pageIndicatorTemplate
) {
    public static final InventoryTheme DEFAULT = new InventoryTheme(
            Material.GRAY_STAINED_GLASS_PANE,
            Component.text(" "),
            Sound.UI_BUTTON_CLICK,
            Sound.UI_BUTTON_CLICK,
            Sound.ITEM_BOOK_PAGE_TURN,
            Sound.BLOCK_NOTE_BLOCK_BASS,
            "<gray>Page <gold><current></gold> / <gold><total></gold></gray>"
    );

    public Component renderPageIndicator(int current, int total) {
        String s = pageIndicatorTemplate
                .replace("<current>", String.valueOf(current))
                .replace("<total>", String.valueOf(total));
        return MiniMessage.miniMessage().deserialize(s)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private Material borderMat = Material.GRAY_STAINED_GLASS_PANE;
        private Component borderName = Component.text(" ");
        private Sound open, click, page, denied;
        private String tpl = DEFAULT.pageIndicatorTemplate;
        public Builder borderMaterial(Material m) { this.borderMat = m; return this; }
        public Builder borderName(Component n) { this.borderName = n; return this; }
        public Builder openSound(Sound s) { this.open = s; return this; }
        public Builder clickSound(Sound s) { this.click = s; return this; }
        public Builder pageSwitchSound(Sound s) { this.page = s; return this; }
        public Builder deniedSound(Sound s) { this.denied = s; return this; }
        public Builder pageIndicatorTemplate(String t) { this.tpl = t; return this; }
        public InventoryTheme build() {
            return new InventoryTheme(borderMat, borderName, open, click, page, denied, tpl);
        }
    }

    public static InventoryTheme dark() {
        return builder()
                .borderMaterial(Material.BLACK_STAINED_GLASS_PANE)
                .pageIndicatorTemplate("<dark_gray>« <white><current><dark_gray>/<white><total> <dark_gray>»")
                .build();
    }
}
