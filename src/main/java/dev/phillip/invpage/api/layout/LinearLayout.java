package dev.phillip.invpage.api.layout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Fills slots linearly 0..n-1, optionally skipping a reserved tail (e.g. last row for nav). */
public record LinearLayout(int reservedTailRows) implements Layout {

    public static LinearLayout full() { return new LinearLayout(0); }
    public static LinearLayout withNavRow() { return new LinearLayout(1); }

    @Override
    public List<Integer> itemSlots(int rows) {
        int total = rows * 9;
        int end = total - reservedTailRows * 9;
        var out = new ArrayList<Integer>(end);
        for (int i = 0; i < end; i++) out.add(i);
        return out;
    }

    @Override
    public List<Integer> reservedSlots(int rows) {
        if (reservedTailRows <= 0) return Collections.emptyList();
        int total = rows * 9;
        int start = total - reservedTailRows * 9;
        var out = new ArrayList<Integer>(reservedTailRows * 9);
        for (int i = start; i < total; i++) out.add(i);
        return out;
    }
}
