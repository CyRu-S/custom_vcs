package com.vcs.core;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

public final class MergeService {
    private final Repository repository;
    private final ReferenceStore references;
    private final CommitService commits;
    private final ObjectStore objects;
    private final CheckoutService checkout;
    private final MergeStateStore states;

    public MergeService(Repository repository) {
        this.repository = repository;
        references = new ReferenceStore(repository);
        commits = new CommitService(repository);
        objects = new ObjectStore(repository);
        checkout = new CheckoutService(repository);
        states = new MergeStateStore(repository);
    }

    public MergeResult merge(String branch, String author, String message) throws IOException {
        if (states.load() != null) {
            throw new RepositoryStateException("Finish or abort the existing merge first.");
        }
        checkout.requireCleanWorkingTree();
        String ours = references.currentCommitId();
        String theirs = references.branchCommitId(branch);
        if (ours == null) {
            throw new RepositoryStateException("Create an initial commit before merging.");
        }
        CommitGraph graph = new CommitGraph(commits);
        // Case 1: the source branch is already part of our history.
        if (graph.ancestors(ours).contains(theirs)) {
            return new MergeResult(MergeResult.Kind.UP_TO_DATE, ours, List.of());
        }
        Map<String, String> left = snapshot(ours);
        Map<String, String> right = snapshot(theirs);
        // Case 2: our branch can simply move forward to the source commit.
        if (graph.ancestors(theirs).contains(ours)) {
            checkout.restore(left, right);
            // A fast-forward advances this branch; it does not switch to the source branch.
            references.updateCurrentBranch(theirs);
            return new MergeResult(MergeResult.Kind.FAST_FORWARD, theirs, List.of());
        }
        // Case 3: both branches changed, so combine them using their common ancestor.
        if (author == null || author.isBlank()) {
            throw new RepositoryStateException("Provide an author for a merge commit.");
        }
        if (message == null) {
            message = "Merge " + branch + " into " + references.currentBranchName();
        }
        if (message.isBlank()) {
            throw new RepositoryStateException("Merge message cannot be blank.");
        }
        Map<String, String> base = snapshot(graph.mergeBase(ours, theirs));
        checkout.validateSnapshot(base);
        checkout.validateSnapshot(left);
        checkout.validateSnapshot(right);
        List<String> conflicts = new ArrayList<>();
        Map<String, String> merged = mergeFiles(base, left, right, branch, conflicts);
        checkout.validateRestore(left, merged);
        Map<String, String> managed = new TreeMap<>(left);
        managed.putAll(merged);
        // Save recovery information before working files change; HEAD remains at ours.
        states.save(new MergeState(ours, theirs, references.currentBranchName(), branch, managed, conflicts));
        checkout.restore(left, merged);
        if (!conflicts.isEmpty()) {
            return new MergeResult(MergeResult.Kind.CONFLICTS, null, conflicts);
        }
        String id = commits.createCommit(author, message);
        return new MergeResult(MergeResult.Kind.MERGED, id, List.of());
    }

    // Decide the next snapshot one file at a time before changing the working tree.
    private Map<String, String> mergeFiles(Map<String, String> base,
            Map<String, String> ours, Map<String, String> theirs,
            String sourceBranch, List<String> conflicts) throws IOException {
        Set<String> paths = new TreeSet<>(base.keySet());
        paths.addAll(ours.keySet());
        paths.addAll(theirs.keySet());
        Map<String, String> merged = new TreeMap<>();
        for (String path : paths) {
            String chosen = mergeFile(path, base.get(path), ours.get(path),
                    theirs.get(path), sourceBranch, conflicts);
            // A null object ID means this path is deleted in the merged snapshot.
            if (chosen != null) {
                merged.put(path, chosen);
            }
        }
        return merged;
    }

    private String mergeFile(String path, String baseId, String oursId, String theirsId,
            String sourceBranch, List<String> conflicts) throws IOException {
        if (Objects.equals(oursId, theirsId)) {
            return oursId;
        }
        // If only one branch changed the file, take that branch's version.
        if (Objects.equals(oursId, baseId)) {
            return theirsId;
        }
        if (Objects.equals(theirsId, baseId)) {
            return oursId;
        }
        if (oursId == null || theirsId == null) {
            // One side edited and the other deleted: keep the surviving file for resolution.
            conflicts.add(path);
            return oursId == null ? theirsId : oursId;
        }

        // Both sides edited: the text helper combines independent edits or adds conflict markers.
        byte[] baseBytes = baseId == null ? new byte[0] : objects.readBlob(baseId);
        TextMerge.Result result = TextMerge.merge(baseBytes,
                objects.readBlob(oursId), objects.readBlob(theirsId), sourceBranch);
        if (result.conflict()) {
            conflicts.add(path);
        }
        return objects.writeBlob(result.bytes());
    }

    public void abort() throws IOException {
        MergeState state = states.load();
        if (state == null) {
            throw new RepositoryStateException("No merge is in progress.");
        }
        if (!state.ours().equals(references.currentCommitId())
                || !state.currentBranch().equals(references.currentBranchName())) {
            throw new RepositoryStateException("Branch changed during merge; cannot safely abort.");
        }
        // Only paths owned by this merge may be removed; unrelated new files stay untouched.
        Map<String, String> existing = new TreeMap<>();
        Path root = repository.workingDirectory().toRealPath();
        for (var entry : state.managedPaths().entrySet()) {
            Path file = WorkingPaths.resolve(root, entry.getKey());
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)
                        && state.managedPaths().keySet().stream()
                                .anyMatch(name -> name.startsWith(entry.getKey() + "/"))) {
                    continue;
                }
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Merge path is blocked: " + entry.getKey());
                }
                existing.put(entry.getKey(), entry.getValue());
            }
        }
        checkout.restore(existing, snapshot(state.ours()));
        states.clear();
    }

    private Map<String, String> snapshot(String id) throws IOException {
        return commits.readSnapshot(commits.readCommit(id).snapshotId());
    }
}
