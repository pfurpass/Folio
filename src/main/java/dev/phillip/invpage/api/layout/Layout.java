package dev.phillip.invpage.api.layout;

import java.util.List;

public sealed interface Layout permits LinearLayout, GridLayout, MaskLayout {

    /**
     * Returns the slot indices reserved for items, in fill order.
     * Reserved slots (border, navigation) are excluded.
     */
    List<Integer> itemSlots(int rows);

    /** Slots reserved by the layout (e.g. border) — not part of itemSlots. */
    List<Integer> reservedSlots(int rows);

    default int itemsPerPage(int rows) { return itemSlots(rows).size(); }
}
