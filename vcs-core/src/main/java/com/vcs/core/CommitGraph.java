package com.vcs.core;

import java.io.IOException;
import java.util.*;

// Ancestry follows both parents; first-parent log order alone cannot decide merges.
final class CommitGraph {
    private final CommitService commits;

    CommitGraph(CommitService commits) { this.commits = commits; }

    Set<String> ancestors(String tip) throws IOException {
        Set<String> seen = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        pending.add(tip);
        while (!pending.isEmpty()) {
            String id = pending.removeFirst();
            if (!seen.add(id)) continue;
            Commit commit = commits.readCommit(id);
            if (commit.parentId() != null) pending.addLast(commit.parentId());
            if (commit.secondParentId() != null) pending.addLast(commit.secondParentId());
        }
        return seen;
    }

    String mergeBase(String ours, String theirs) throws IOException {
        Set<String> common = new HashSet<>(ancestors(ours));
        common.retainAll(ancestors(theirs));
        if (common.isEmpty()) throw new RepositoryStateException("Branches have unrelated histories.");
        Set<String> best = new HashSet<>(common);
        for (String candidate : common) {
            Set<String> older = ancestors(candidate);
            older.remove(candidate);
            best.removeAll(older);
        }
        // Criss-cross histories need recursive virtual-base merging, a later extension.
        if (best.size() != 1) throw new RepositoryStateException("Multiple merge bases; automatic merge is ambiguous.");
        return best.iterator().next();
    }
}
