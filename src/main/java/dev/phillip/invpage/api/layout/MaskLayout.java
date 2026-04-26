package dev.phillip.invpage.api.layout;

import java.util.ArrayList;
import java.util.List;

/**
 * Mask layout — string pattern, one row per line.
 *  X = item slot (filled by paginated content)
 *  . = empty
 *  B = border / reserved
 *  < > | F L  = nav slot hints (treated as reserved here; resolved by NavigationButtons)
 * Whitespace (spaces, tabs) is ignored — multiline text-blocks read naturally.
 */
public record MaskLayout(char[][] grid) implements Layout {

    public static MaskLayout of(String pattern) {
        var rows = new ArrayList<char[]>();
        for (String raw : pattern.split("\\R")) {
            var sb = new StringBuilder(9);
            for (int i = 0; i < raw.length() && sb.length() < 9; i++) {
                char c = raw.charAt(i);
                if (c == ' ' || c == '\t') continue;
                sb.append(c);
            }
            if (sb.length() == 0) continue;
            while (sb.length() < 9) sb.append('.');
            rows.add(sb.toString().toCharArray());
        }
        return new MaskLayout(rows.toArray(new char[0][]));
    }

    public int rows() { return grid.length; }

    @Override
    public List<Integer> itemSlots(int rows) {
        var out = new ArrayList<Integer>();
        int r = Math.min(rows, grid.length);
        for (int row = 0; row < r; row++) {
            for (int col = 0; col < 9; col++) {
                if (grid[row][col] == 'X') out.add(row * 9 + col);
            }
        }
        return out;
    }

    @Override
    public List<Integer> reservedSlots(int rows) {
        var out = new ArrayList<Integer>();
        int r = Math.min(rows, grid.length);
        for (int row = 0; row < r; row++) {
            for (int col = 0; col < 9; col++) {
                char c = grid[row][col];
                if (c == 'B' || c == '<' || c == '>' || c == '|' || c == 'F' || c == 'L' || c == 'P') {
                    out.add(row * 9 + col);
                }
            }
        }
        return out;
    }

    /** First slot matching the given mask character, or -1. */
    public int slotOf(char marker) {
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < 9; col++) {
                if (grid[row][col] == marker) return row * 9 + col;
            }
        }
        return -1;
    }

    /** All slots matching marker. */
    public List<Integer> slotsOf(char marker) {
        var out = new ArrayList<Integer>();
        for (int row = 0; row < grid.length; row++) {
            for (int col = 0; col < 9; col++) {
                if (grid[row][col] == marker) out.add(row * 9 + col);
            }
        }
        return out;
    }
}
