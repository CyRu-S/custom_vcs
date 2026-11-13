package com.vcs.core;

import java.util.List;

// Working-file changes since staging; these edits are not part of the next commit yet.
public record  WorkingTreeStatus (List<String> modified, List<String> deleted, List<String> untracked) {
    public WorkingTreeStatus {
        modified = List.copyOf(modified);
        deleted = List.copyOf(deleted);
        untracked = List.copyOf(untracked);
    }
}
