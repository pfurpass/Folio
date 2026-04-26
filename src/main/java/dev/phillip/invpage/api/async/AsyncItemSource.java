package dev.phillip.invpage.api.async;

import dev.phillip.invpage.api.InventoryItem;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

@FunctionalInterface
public interface AsyncItemSource extends Supplier<CompletableFuture<List<InventoryItem>>> {
}
