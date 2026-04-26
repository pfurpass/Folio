package dev.phillip.invpage.api.layout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** Item area is a rectangle, optionally with a 1-slot border. */
public record GridLayout(boolean border, int reservedTailRows) implements Layout {

    public static GridLayout bordered() { return new GridLayout(true, 1); }
    public static GridLayout plain() { return new GridLayout(false, 0); }

    @Override
    public List<Integer> itemSlots(int rows) {
        int totalRows = rows;
        int usableRows = totalRows - reservedTailRows;
        var out = new ArrayList<Integer>();
        for (int r = 0; r < usableRows; r++) {
            for (int c = 0; c < 9; c++) {
                if (border && (r == 0 || r == usableRows - 1 || c == 0 || c == 8)) continue;
                out.add(r * 9 + c);
            }
        }
        return out;
    }

    @Override
    public List<Integer> reservedSlots(int rows) {
        var out = new ArrayList<Integer>();
        var set = new HashSet<>(itemSlots(rows));
        for (int i = 0; i < rows * 9; i++) if (!set.contains(i)) out.add(i);
        return out;
    }
}
