package com.vcs.core;

import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.util.*;

// Merge base-relative edits; overlapping edits require a human choice.
final class TextMerge {
    record Result(byte[] bytes, boolean conflict) { }
    private record Edit(int start, int end, List<String> lines) { }

    static Result merge(byte[] base, byte[] ours, byte[] theirs, String branch) {
        String original = decode(base);
        String left = decode(ours);
        String right = decode(theirs);
        // Binary conflicts retain ours intact; status still requires explicit resolution.
        if (original == null || left == null || right == null) return new Result(ours, true);
        List<String> baseLines = LineDiff.lines(original);
        List<Edit> edits = new ArrayList<>(edits(baseLines, LineDiff.lines(left)));
        for (Edit incoming : edits(baseLines, LineDiff.lines(right))) {
            boolean duplicate = false;
            for (Edit existing : edits) {
                if (existing.equals(incoming)) { duplicate = true; break; }
                if (overlaps(existing, incoming)) {
                    String markers = "<<<<<<< HEAD\n" + terminated(left) + "=======\n"
                            + terminated(right) + ">>>>>>> " + branch + "\n";
                    return new Result(markers.getBytes(StandardCharsets.UTF_8), true);
                }
            }
            if (!duplicate) edits.add(incoming);
        }
        edits.sort(Comparator.comparingInt(Edit::start));
        StringBuilder merged = new StringBuilder();
        int cursor = 0;
        for (Edit edit : edits) {
            baseLines.subList(cursor, edit.start()).forEach(merged::append);
            edit.lines().forEach(merged::append);
            cursor = edit.end();
        }
        baseLines.subList(cursor, baseLines.size()).forEach(merged::append);
        return new Result(merged.toString().getBytes(StandardCharsets.UTF_8), false);
    }

    private static boolean overlaps(Edit a, Edit b) {
        // Insertions at a replacement boundary are deliberately treated conservatively.
        if (a.start() == a.end() || b.start() == b.end()) {
            return a.start() <= b.end() && b.start() <= a.end();
        }
        return a.start() < b.end() && b.start() < a.end();
    }

    private static List<Edit> edits(List<String> base, List<String> changed) {
        List<Edit> result = new ArrayList<>();
        int position = 0;
        int start = -1;
        List<String> replacement = new ArrayList<>();
        for (DiffLine line : LineDiff.compare(base, changed)) {
            if (line.kind() == DiffLine.Kind.CONTEXT) {
                if (start >= 0) {
                    result.add(new Edit(start, position, List.copyOf(replacement)));
                    replacement.clear();
                    start = -1;
                }
                position++;
            } else {
                if (start < 0) start = position;
                if (line.kind() == DiffLine.Kind.REMOVED) position++;
                else replacement.add(line.text());
            }
        }
        if (start >= 0) result.add(new Edit(start, position, List.copyOf(replacement)));
        return result;
    }

    private static String decode(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.indexOf('\0') < 0 ? text : null;
        } catch (CharacterCodingException exception) { return null; }
    }

    private static String terminated(String text) {
        return text.isEmpty() || text.endsWith("\n") ? text : text + "\n";
    }
}
