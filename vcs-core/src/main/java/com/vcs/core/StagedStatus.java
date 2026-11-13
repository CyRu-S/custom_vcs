package com.vcs.core;

import java.util.List;

// Changes the next commit would make relative to the current commit's snapshot.
public record StagedStatus(
        List<String> added,
        List<String> modified,
        List<String> deleted) {
    public StagedStatus {
        added = List.copyOf(added);
        modified = List.copyOf(modified);
        deleted = List.copyOf(deleted);
    }
}
