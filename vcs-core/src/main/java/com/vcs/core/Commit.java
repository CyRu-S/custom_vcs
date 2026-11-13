package com.vcs.core;

import java.time.Instant;

public record Commit (
    // Identifies the saved snapshot of staged paths and blob IDs.
    String snapshotId,
    // Previous commit ID; null for the first commit.
    String parentId,
    String author,
    // The commit time as a moment on the UTC timeline.
    Instant timestamp,
    String message,
    // A merge connects the current tip to the branch being merged.
    String secondParentId) {
    public Commit(String snapshotId, String parentId, String author,
                  Instant timestamp, String message) {
        this(snapshotId, parentId, author, timestamp, message, null);
    }
}
