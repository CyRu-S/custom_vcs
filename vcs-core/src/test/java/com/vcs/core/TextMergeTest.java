package com.vcs.core;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class TextMergeTest {
    @Test void disjointReplacementsPreserveCrLfAndMissingFinalNewline() {
        TextMerge.Result result = merge("a\r\nb\r\nc", "A\r\nb\r\nc", "a\r\nb\r\nC");
        assertFalse(result.conflict());
        assertEquals("A\r\nb\r\nC", text(result));
    }
    @Test void disjointInsertionsAndDeletionsKeepTheirOriginalBasePositions() {
        TextMerge.Result result = merge("a\nb\nc\nd\n", "new\na\nb\nc\nd\n", "a\nb\nd\n");
        assertFalse(result.conflict());
        assertEquals("new\na\nb\nd\n", text(result));
    }
    @Test void identicalOverlappingEditsAreAppliedOnceAlongsideOtherChanges() {
        TextMerge.Result result = merge("a\nb\nc\nd\ne\n", "A\nb\nc\nd\ne\n", "A\nb\nc\nd\nE\n");
        assertFalse(result.conflict());
        assertEquals("A\nb\nc\nd\nE\n", text(result));
    }
    @Test void competingInsertionsAtSamePositionConflict() {
        TextMerge.Result result = merge("a\n", "ours\na\n", "theirs\na\n");
        assertTrue(result.conflict());
        assertTrue(text(result).contains("<<<<<<< HEAD"));
    }
    @Test void invalidUtf8IsNotSilentlyReplaced() {
        TextMerge.Result result = TextMerge.merge(new byte[]{1}, new byte[]{(byte) 0xFF}, new byte[]{2}, "feature");
        assertTrue(result.conflict());
        assertArrayEquals(new byte[]{(byte) 0xFF}, result.bytes());
    }
    private TextMerge.Result merge(String base, String ours, String theirs) {
        return TextMerge.merge(base.getBytes(StandardCharsets.UTF_8), ours.getBytes(StandardCharsets.UTF_8),
                theirs.getBytes(StandardCharsets.UTF_8), "feature");
    }
    private String text(TextMerge.Result result) { return new String(result.bytes(), StandardCharsets.UTF_8); }
}
