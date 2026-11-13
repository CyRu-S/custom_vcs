package com.vcs.core;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CommitService {
    private final Repository repository;
    private final ObjectStore objectStore;
    private final ReferenceStore references;

    public CommitService(Repository repository) {
        this.repository = repository;
        this.objectStore = new ObjectStore(repository);
        this.references = new ReferenceStore(repository);
    }

    public Commit readCommit(String commitId) throws IOException {
        return CommitCodec.decode(
            objectStore.readContent("commit", commitId)
        );
    }

    public Map<String, String> readSnapshot(String snapshotId) throws IOException {
        return SnapshotCodec.decode(objectStore.readContent("snapshot", snapshotId));
    }

    public String createCommit(String author, String message)
            throws IOException {

        if (author == null || author.isBlank()
                || message == null || message.isBlank()) {
            throw new IllegalArgumentException(
                    "Author and message are required.");
        }

        String parentId = references.currentCommitId();
        MergeStateStore mergeStore = new MergeStateStore(repository);
        MergeState merge = mergeStore.load();
        if (merge != null) {
            if (!merge.ours().equals(parentId)
                    || !merge.currentBranch().equals(references.currentBranchName())) {
                throw new RepositoryStateException("Branch changed during merge; cannot complete it.");
            }
            if (!merge.conflicts().isEmpty()) {
                throw new RepositoryStateException("Resolve and stage merge conflicts first: " + merge.conflicts());
            }
        }

        StagingArea stagingArea = new StagingArea(repository);
        // Commit the saved index, not the current working-file contents.
        stagingArea.load();
        Map<String, String> entries = stagingArea.entries();

        if (parentId == null && entries.isEmpty()) {
            throw new RepositoryStateException("No files staged for the first commit.");
        }

        // Refuse a snapshot that references missing or corrupted blobs.
        for (String blobId : entries.values()) {
            objectStore.readBlob(blobId);
        }

        String snapshotId = objectStore.writeObject(
                "snapshot", SnapshotCodec.encode(entries));

        if (parentId != null) {
            Commit parent = readCommit(parentId);

            // Working files may differ, but a commit needs changes in the index.
            if (merge == null && snapshotId.equals(parent.snapshotId())) {
                throw new RepositoryStateException("No staged changes to commit.");
            }
        }

        Commit commit = new Commit(
                snapshotId, parentId, author, Instant.now(), message,
                merge == null ? null : merge.theirs());

        String commitId = objectStore.writeObject(
                "commit", CommitCodec.encode(commit));

        // Update the branch only after both objects have been stored.
        references.updateCurrentBranch(commitId);
        if (merge != null) mergeStore.clear();
        return commitId;
    }

    public List<CommitEntry> history() throws IOException {
        List<CommitEntry> result = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        String tip = references.currentCommitId();
        if (tip == null) return List.of();
        // Reverse postorder visits every child before its parents, including shared ancestry.
        Set<String> active = new HashSet<>();
        record Visit(String id, boolean expanded) { }
        java.util.Deque<Visit> pending = new java.util.ArrayDeque<>();
        pending.push(new Visit(tip, false));
        while (!pending.isEmpty()) {
            Visit visit = pending.pop();
            if (visit.expanded()) {
                active.remove(visit.id());
                visited.add(visit.id());
                result.add(new CommitEntry(visit.id(), readCommit(visit.id())));
                continue;
            }
            if (active.contains(visit.id())) throw new IOException("Cycle detected in commit history.");
            if (visited.contains(visit.id())) continue;
            active.add(visit.id());
            Commit commit = readCommit(visit.id());
            pending.push(new Visit(visit.id(), true));
            if (commit.parentId() != null) pending.push(new Visit(commit.parentId(), false));
            if (commit.secondParentId() != null) pending.push(new Visit(commit.secondParentId(), false));
        }
        java.util.Collections.reverse(result);
        return List.copyOf(result);
    }

}
