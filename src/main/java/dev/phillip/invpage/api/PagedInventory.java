package dev.phillip.invpage.api;

import java.util.List;

/**
 * Container of one or more InventoryPages.
 *  - paginate(template) → auto-paginate the template's item list across N pages
 *  - of(pages...)        → manual list of pre-built pages
 */
public interface PagedInventory {

    List<InventoryPage> pages();
    int pageCount();
    InventoryPage page(int index);

    /** True when the page set is computed dynamically from one template. */
    boolean dynamic();

    /** For dynamic paging, the source template. */
    InventoryPage template();

    static PagedInventory paginate(InventoryPage template) {
        return new dev.phillip.invpage.impl.PagedInventoryImpl(template, true, List.of(template));
    }

    static PagedInventory of(InventoryPage... pages) {
        return new dev.phillip.invpage.impl.PagedInventoryImpl(pages[0], false, List.of(pages));
    }

    static PagedInventory of(List<InventoryPage> pages) {
        return new dev.phillip.invpage.impl.PagedInventoryImpl(pages.get(0), false, List.copyOf(pages));
    }
}
