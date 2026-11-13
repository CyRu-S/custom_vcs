package com.vcs.customvcs.dto;

import com.vcs.core.StagedStatus;
import com.vcs.core.WorkingTreeStatus;

// Return branch metadata alongside staged and unstaged changes.
public record RepositoryStatusResponse (
    String branch,
    String commitId,
    // Reuse the engine's immutable data records instead of copying their fields.
    StagedStatus staged,
    WorkingTreeStatus working
) {
}
