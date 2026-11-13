package com.vcs.core;

import java.util.List;

public record FileDiff(String path, Change change, boolean binary,
                       int oldLineCount, int newLineCount, List<DiffLine> lines) {
    public enum Change { ADDED, MODIFIED, DELETED }

    public FileDiff {
        lines = List.copyOf(lines);
    }
}
