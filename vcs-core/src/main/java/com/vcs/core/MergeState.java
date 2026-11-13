package com.vcs.core;

import java.util.List;
import java.util.Map;

// Durable state keeps conflict resolution usable across separate CLI processes.
public record MergeState(String ours, String theirs, String currentBranch,
                         String sourceBranch, Map<String, String> managedPaths,
                         List<String> conflicts) {
    public MergeState {
        managedPaths = Map.copyOf(managedPaths);
        conflicts = List.copyOf(conflicts);
    }
}
