package com.vcs.core;

// Keep line endings in the data so a missing final newline remains a real change.
public record DiffLine(Kind kind, String text) {
    public enum Kind { CONTEXT, ADDED, REMOVED }
}
