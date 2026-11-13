package com.vcs.core;

import java.util.List;

public record MergeResult(Kind kind, String commitId, List<String> conflicts) {
    public enum Kind { UP_TO_DATE, FAST_FORWARD, MERGED, CONFLICTS }
    public MergeResult { conflicts = List.copyOf(conflicts); }
}
