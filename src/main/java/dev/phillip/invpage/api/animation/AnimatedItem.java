package dev.phillip.invpage.api.animation;

import dev.phillip.invpage.api.InventoryItem;
import dev.phillip.invpage.api.click.ClickHandler;

import java.util.List;

/** An InventoryItem whose underlying ItemStack cycles through frames. */
public interface AnimatedItem extends InventoryItem {

    List<Frame> frames();

    /** Total cycle length in ticks. */
    default int cycleTicks() {
        int sum = 0;
        for (var f : frames()) sum += f.durationTicks();
        return Math.max(1, sum);
    }

    static Builder builder() { return new dev.phillip.invpage.impl.AnimatedItemImpl.BuilderImpl(); }

    interface Builder {
        Builder frame(Frame frame);
        Builder frames(List<Frame> frames);
        Builder onClick(ClickHandler handler);
        AnimatedItem build();
    }
}
