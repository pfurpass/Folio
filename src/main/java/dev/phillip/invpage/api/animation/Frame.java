package dev.phillip.invpage.api.animation;

import org.bukkit.inventory.ItemStack;

/** A single animation frame: a stack and its dwell time in ticks. */
public record Frame(ItemStack stack, int durationTicks) {
    public Frame {
        if (durationTicks < 1) throw new IllegalArgumentException("durationTicks must be >= 1");
    }
    public static Frame of(ItemStack stack, int ticks) { return new Frame(stack, ticks); }
}
