package dev.phillip.invpage.impl;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.animation.AnimatedItem;
import dev.phillip.invpage.api.animation.Frame;
import dev.phillip.invpage.api.click.ClickHandler;
import dev.phillip.invpage.api.click.ClickResult;
import dev.phillip.invpage.api.click.CooldownPolicy;
import dev.phillip.invpage.api.gate.PermissionGate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AnimatedItemImpl implements AnimatedItem {

    private final UUID id;
    private final List<Frame> frames;
    private final ClickHandler global;
    private final int cycleTicks;

    AnimatedItemImpl(UUID id, List<Frame> frames, ClickHandler global) {
        this.id = id;
        this.frames = List.copyOf(frames);
        this.global = global;
        int sum = 0;
        for (var f : frames) sum += f.durationTicks();
        this.cycleTicks = Math.max(1, sum);
    }

    @Override public UUID id() { return id; }
    @Override public List<Frame> frames() { return frames; }

    @Override
    public ItemStack render(Player viewer) {
        long tick = currentTick();
        int phase = (int) Math.floorMod(tick, cycleTicks);
        int acc = 0;
        for (var f : frames) {
            acc += f.durationTicks();
            if (phase < acc) return f.stack().clone();
        }
        return frames.get(0).stack().clone();
    }

    private long currentTick() {
        return Bukkit.getCurrentTick();
    }

    @Override public boolean visibleFor(Player viewer) { return true; }
    @Override public boolean clickableFor(Player viewer) { return global != null; }
    @Override public @Nullable ClickHandler handlerFor(ClickType type) { return null; }
    @Override public @Nullable ClickHandler globalHandler() { return global; }
    @Override public @Nullable CooldownPolicy cooldown() { return null; }
    @Override public boolean isAsync() { return false; }

    public static final class BuilderImpl implements AnimatedItem.Builder {
        private final List<Frame> frames = new ArrayList<>();
        private ClickHandler handler;

        @Override public AnimatedItem.Builder frame(Frame f) { frames.add(f); return this; }
        @Override public AnimatedItem.Builder frames(List<Frame> f) { frames.addAll(f); return this; }
        @Override public AnimatedItem.Builder onClick(ClickHandler h) { this.handler = h; return this; }

        @Override public AnimatedItem build() {
            if (frames.isEmpty()) throw new IllegalStateException("AnimatedItem requires at least one frame");
            return new AnimatedItemImpl(UUID.randomUUID(), frames,
                    handler != null ? handler : ctx -> ClickResult.cancel());
        }
    }
}
