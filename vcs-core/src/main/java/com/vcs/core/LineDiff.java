package com.vcs.core;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// The LCS identifies shared lines; the gaps become deletions and additions.
final class LineDiff {
    private static final long MAX_MATRIX_CELLS = 4_000_000;

    private LineDiff() {
    }

    static List<String> lines(String text) {
        return text.isEmpty() ? List.of() : Arrays.asList(text.split("(?<=\n)"));
    }

    static List<DiffLine> compare(List<String> before, List<String> after) {
        List<DiffLine> result = new ArrayList<>();
        int prefix = 0;
        while (prefix < before.size() && prefix < after.size()
                && before.get(prefix).equals(after.get(prefix))) {
            result.add(new DiffLine(DiffLine.Kind.CONTEXT, before.get(prefix++)));
        }
        int suffix = 0;
        while (suffix < before.size() - prefix && suffix < after.size() - prefix
                && before.get(before.size() - suffix - 1)
                        .equals(after.get(after.size() - suffix - 1))) {
            suffix++;
        }

        List<String> left = before.subList(prefix, before.size() - suffix);
        List<String> right = after.subList(prefix, after.size() - suffix);
        appendMiddle(left, right, result);
        for (int i = before.size() - suffix; i < before.size(); i++) {
            result.add(new DiffLine(DiffLine.Kind.CONTEXT, before.get(i)));
        }
        return List.copyOf(result);
    }

    private static void appendMiddle(List<String> left, List<String> right,
                                     List<DiffLine> result) {
        int n = left.size();
        int m = right.size();
        // Bound memory. Large changed regions use a valid replacement rather than a minimal edit.
        if ((n + 1L) * (m + 1L) > MAX_MATRIX_CELLS) {
            left.forEach(line -> result.add(new DiffLine(DiffLine.Kind.REMOVED, line)));
            right.forEach(line -> result.add(new DiffLine(DiffLine.Kind.ADDED, line)));
            return;
        }

        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = left.get(i).equals(right.get(j))
                        ? 1 + lcs[i + 1][j + 1]
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }

        int i = 0;
        int j = 0;
        while (i < n || j < m) {
            if (i < n && j < m && left.get(i).equals(right.get(j))) {
                result.add(new DiffLine(DiffLine.Kind.CONTEXT, left.get(i++)));
                j++;
            } else if (i < n && (j == m || lcs[i + 1][j] >= lcs[i][j + 1])) {
                result.add(new DiffLine(DiffLine.Kind.REMOVED, left.get(i++)));
            } else {
                result.add(new DiffLine(DiffLine.Kind.ADDED, right.get(j++)));
            }
        }
    }
}
