package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryPage;
import dev.phillip.invpage.api.PagedInventory;

import java.util.List;

public final class PagedInventoryImpl implements PagedInventory {

    private final InventoryPage template;
    private final boolean dynamic;
    private final List<InventoryPage> pages;

    public PagedInventoryImpl(InventoryPage template, boolean dynamic, List<InventoryPage> pages) {
        this.template = template;
        this.dynamic = dynamic;
        this.pages = pages;
    }

    @Override public List<InventoryPage> pages() { return pages; }
    @Override public int pageCount() { return pages.size(); }
    @Override public InventoryPage page(int index) {
        if (index < 0 || index >= pages.size()) throw new IndexOutOfBoundsException("page " + index);
        return pages.get(index);
    }
    @Override public boolean dynamic() { return dynamic; }
    @Override public InventoryPage template() { return template; }
}
