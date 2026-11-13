package com.vcs.customvcs.dto;

import java.util.List;

// Expose resolution progress without leaking the engine's internal recovery snapshot.
public record MergeStateResponse(boolean inProgress, String currentBranch,
        String sourceBranch, String ours, String theirs, List<String> conflicts) {
    public MergeStateResponse {
        conflicts = List.copyOf(conflicts);
    }
}
