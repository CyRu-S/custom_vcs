package com.vcs.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class StageFiveTest {
    @TempDir Path temporary;
    Repository repo;
    ReferenceStore refs;
    CommitService commits;
    CheckoutService checkout;
    MergeService merge;

    @BeforeEach void setup() throws IOException {
        repo = new Repository(temporary.resolve("repo"));
        repo.initialize();
        refs = new ReferenceStore(repo);
        commits = new CommitService(repo);
        checkout = new CheckoutService(repo);
        merge = new MergeService(repo);
    }

    @Test void oldAndMergeCommitFormatsRoundTrip() throws IOException {
        String id = "a".repeat(64), other = "b".repeat(64);
        Commit ordinary = new Commit(id, null, "Author", Instant.EPOCH, "Initial");
        byte[] bytes = CommitCodec.encode(ordinary);
        assertEquals(1, ByteBuffer.wrap(bytes).getInt());
        assertEquals(ordinary, CommitCodec.decode(bytes));
        Commit merged = new Commit(id, id, "Author", Instant.EPOCH, "Merge", other);
        assertEquals(2, ByteBuffer.wrap(CommitCodec.encode(merged)).getInt());
        assertEquals(merged, CommitCodec.decode(CommitCodec.encode(merged)));
        assertThrows(IOException.class, () -> CommitCodec.decode(Arrays.copyOf(bytes, bytes.length - 1)));
    }

    @Test void fastForwardAndRepeatedMergeKeepCurrentBranch() throws IOException {
        seed();
        checkout.checkout("feature");
        write("file", "feature\n"); stage("file");
        String target = commit();
        checkout.checkout("main");
        assertEquals(MergeResult.Kind.FAST_FORWARD, merge.merge("feature", null, null).kind());
        assertEquals(target, refs.currentCommitId());
        assertEquals("main", refs.currentBranchName());
        assertEquals("feature\n", read("file"));
        assertEquals(MergeResult.Kind.UP_TO_DATE, merge.merge("feature", null, null).kind());
        assertNull(state());
        assertClean();
    }

    @Test void independentLineChangesCreateTwoParentCommitAndFullHistory() throws IOException {
        String base = seed();
        write("file", "OURS\nb\nc\n"); stage("file"); String ours = commit();
        checkout.checkout("feature");
        write("file", "a\nb\nTHEIRS\n"); stage("file"); String theirs = commit();
        checkout.checkout("main");
        MergeResult result = merge.merge("feature", "Author", "Join work");
        assertEquals(MergeResult.Kind.MERGED, result.kind());
        assertEquals("OURS\nb\nTHEIRS\n", read("file"));
        Commit joined = commits.readCommit(result.commitId());
        assertEquals(ours, joined.parentId());
        assertEquals(theirs, joined.secondParentId());
        List<String> history = commits.history().stream().map(CommitEntry::id).toList();
        assertEquals(4, history.size());
        assertTrue(history.containsAll(List.of(base, ours, theirs)));
        assertEquals(result.commitId(), history.getFirst());
        assertEquals(base, history.getLast());
        assertEquals(MergeResult.Kind.UP_TO_DATE, merge.merge("feature", null, null).kind());
        assertNull(state());
        assertClean();
    }

    @Test void conflictsPersistBlockCommitAndCheckoutThenFinishAfterStaging() throws IOException {
        String[] tips = conflict();
        MergeResult result = merge.merge("feature", "Author", null);
        assertEquals(MergeResult.Kind.CONFLICTS, result.kind());
        assertEquals(List.of("file"), result.conflicts());
        assertTrue(read("file").contains("<<<<<<< HEAD\nours\n=======\ntheirs\n>>>>>>> feature"));
        assertEquals(tips[0], refs.currentCommitId());
        assertThrows(IOException.class, () -> commit());
        assertThrows(IOException.class, () -> checkout.checkout("feature"));
        assertThrows(IOException.class, () -> merge.merge("feature", "Author", null));
        write("file", "resolved\n");
        assertEquals(List.of("file"), state().conflicts());
        stage("file");
        assertTrue(state().conflicts().isEmpty());
        Commit resolved = commits.readCommit(commit());
        assertEquals(tips[0], resolved.parentId());
        assertEquals(tips[1], resolved.secondParentId());
        assertNull(state());
        assertClean();
    }

    @Test void abortRestoresFilesAndIndexWhilePreservingUnrelatedNewFile() throws IOException {
        conflict();
        checkout.checkout("feature");
        write("new/file", "incoming"); stage("new/file"); commit();
        checkout.checkout("main");
        String starting = refs.currentCommitId();
        Map<String, String> original = index().entries();
        merge.merge("feature", "Author", null);
        assertTrue(Files.exists(root().resolve("new/file")));
        write("draft", "keep me");
        write("file", "partial resolution"); stage("file");
        new MergeService(repo).abort();
        assertEquals("ours\n", read("file"));
        assertEquals("keep me", read("draft"));
        assertFalse(Files.exists(root().resolve("new")));
        assertEquals(original, index().entries());
        assertEquals(starting, refs.currentCommitId());
        assertNull(state());
        assertThrows(IOException.class, () -> merge.abort());
    }

    @Test void modifyDeleteConflictCanResolveAsDeletion() throws IOException {
        seed();
        Files.delete(root().resolve("file")); stage("file"); String ours = commit();
        checkout.checkout("feature");
        write("file", "changed\n"); stage("file"); commit();
        checkout.checkout("main");
        assertEquals(MergeResult.Kind.CONFLICTS, merge.merge("feature", "Author", null).kind());
        assertEquals("changed\n", read("file"));
        Files.delete(root().resolve("file")); stage("file");
        Commit resolution = commits.readCommit(commit());
        assertEquals(commits.readCommit(ours).snapshotId(), resolution.snapshotId());
        assertNotNull(resolution.secondParentId());
        assertFalse(Files.exists(root().resolve("file")));
        assertClean();
    }

    @Test void binaryConflictPreservesOursUntilExplicitChoice() throws IOException {
        seed();
        Files.write(root().resolve("file"), new byte[]{0, 1}); stage("file"); commit();
        checkout.checkout("feature");
        Files.write(root().resolve("file"), new byte[]{0, 2}); stage("file"); commit();
        checkout.checkout("main");
        assertEquals(List.of("file"), merge.merge("feature", "Author", null).conflicts());
        assertArrayEquals(new byte[]{0, 1}, Files.readAllBytes(root().resolve("file")));
        stage("file"); commit();
        assertNull(state());
    }

    @Test void independentAdditionsAndDeletionMergeWithoutConflict() throws IOException {
        seed();
        write("ours", "ours"); stage("ours"); commit();
        checkout.checkout("feature");
        Files.delete(root().resolve("file")); stage("file");
        write("theirs", "theirs"); stage("theirs"); commit();
        checkout.checkout("main");
        assertEquals(MergeResult.Kind.MERGED, merge.merge("feature", "Author", null).kind());
        assertEquals(Set.of("ours", "theirs"), index().entries().keySet());
        assertClean();
    }

    @Test void identicalEditsOnDivergedBranchesStillRecordBothParents() throws IOException {
        seed();
        write("file", "same"); stage("file");
        String ours = commits.createCommit("Author", "left");
        checkout.checkout("feature");
        write("file", "same"); stage("file");
        commits.createCommit("Author", "right");
        checkout.checkout("main");
        MergeResult result = merge.merge("feature", "Author", null);
        assertEquals(MergeResult.Kind.MERGED, result.kind());
        assertEquals(commits.readCommit(ours).snapshotId(), commits.readCommit(result.commitId()).snapshotId());
    }

    @Test void dirtyOrUntrackedFilesPreventMergeWithoutStateOrMutation() throws IOException {
        conflict();
        write("file", "dirty");
        assertThrows(IOException.class, () -> merge.merge("feature", "Author", null));
        assertEquals("dirty", read("file"));
        assertNull(state());
        write("file", "ours\n"); write("draft", "untracked");
        assertThrows(IOException.class, () -> merge.merge("feature", "Author", null));
        assertNull(state());
    }

    @Test void malformedIncomingSnapshotIsRejectedBeforeChangingFiles() throws IOException {
        seed();
        ObjectStore objects = new ObjectStore(repo);
        String blob = objects.writeBlob(new byte[]{1});
        String snapshot = objects.writeObject("snapshot", SnapshotCodec.encode(Map.of("../escape", blob)));
        String tip = objects.writeObject("commit", CommitCodec.encode(new Commit(snapshot,
                refs.currentCommitId(), "Author", Instant.EPOCH, "Invalid")));
        Files.writeString(repo.metadataDirectory().resolve("refs/heads/feature"), tip + "\n");
        assertThrows(IOException.class, () -> merge.merge("feature", "Author", null));
        assertEquals("a\nb\nc\n", read("file"));
        assertFalse(Files.exists(temporary.resolve("escape")));
        assertNull(state());
    }

    @Test void unrelatedHistoriesAreRejected() throws IOException {
        seed();
        ObjectStore objects = new ObjectStore(repo);
        String snapshot = commits.readCommit(refs.currentCommitId()).snapshotId();
        String unrelated = objects.writeObject("commit", CommitCodec.encode(new Commit(snapshot,
                null, "Other", Instant.EPOCH, "Unrelated root")));
        Files.writeString(repo.metadataDirectory().resolve("refs/heads/feature"), unrelated + "\n");
        assertThrows(IOException.class, () -> merge.merge("feature", "Author", null));
        assertNull(state());
    }

    @Test void multipleBestMergeBasesAreRejectedRatherThanPickingOneArbitrarily() throws IOException {
        String root = seed();
        String snapshot = commits.readCommit(root).snapshotId();
        String a = objectCommit(snapshot, root, null, "A");
        String b = objectCommit(snapshot, root, null, "B");
        String left = objectCommit(snapshot, a, b, "Left merge");
        String right = objectCommit(snapshot, b, a, "Right merge");
        refs.updateCurrentBranch(left);
        Files.writeString(repo.metadataDirectory().resolve("refs/heads/feature"), right + "\n");
        IOException error = assertThrows(IOException.class, () -> merge.merge("feature", "Author", null));
        assertTrue(error.getMessage().contains("Multiple merge bases"));
        assertEquals(left, refs.currentCommitId());
        assertNull(state());
    }

    @Test void abortHandlesIncomingFileToDirectoryTransitionAlongsideTextConflict() throws IOException {
        seed();
        write("node", "original node"); stage("node"); commit();
        // Start both branches at the same tracked file layout.
        Files.writeString(repo.metadataDirectory().resolve("refs/heads/feature"), refs.currentCommitId() + "\n");
        write("file", "ours\n"); stage("file"); commit();
        checkout.checkout("feature");
        Files.delete(root().resolve("node")); stage("node");
        write("node/child", "incoming child"); stage("node/child");
        write("file", "theirs\n"); stage("file"); commit();
        checkout.checkout("main");
        assertEquals(MergeResult.Kind.CONFLICTS, merge.merge("feature", "Author", null).kind());
        assertEquals("incoming child", read("node/child"));
        merge.abort();
        assertEquals("original node", read("node"));
        assertEquals("ours\n", read("file"));
        assertClean();
    }

    @Test void independentlyAddedFileAndDirectoryCollisionIsRejectedBeforeMutation() throws IOException {
        seed();
        write("node", "ours"); stage("node"); commit();
        checkout.checkout("feature");
        write("node/child", "theirs"); stage("node/child"); commit();
        checkout.checkout("main");
        assertThrows(IOException.class, () -> merge.merge("feature", "Author", null));
        assertEquals("ours", read("node"));
        assertNull(state());
        assertClean();
    }

    @Test void differingAddAddContentsRemainUnresolved() throws IOException {
        seed();
        write("added", "ours"); stage("added"); commit();
        checkout.checkout("feature");
        write("added", "theirs"); stage("added"); commit();
        checkout.checkout("main");
        assertEquals(List.of("added"), merge.merge("feature", "Author", null).conflicts());
        assertTrue(read("added").contains("<<<<<<< HEAD"));
        merge.abort();
        assertEquals("ours", read("added"));
    }

    private String objectCommit(String snapshot, String first, String second, String message) throws IOException {
        return new ObjectStore(repo).writeObject("commit", CommitCodec.encode(
                new Commit(snapshot, first, "Author", Instant.EPOCH, message, second)));
    }

    private String seed() throws IOException {
        write("file", "a\nb\nc\n"); stage("file");
        String id = commit(); refs.createBranch("feature"); return id;
    }
    private String[] conflict() throws IOException {
        seed();
        write("file", "ours\n"); stage("file"); String ours = commit();
        checkout.checkout("feature");
        write("file", "theirs\n"); stage("file"); String theirs = commit();
        checkout.checkout("main");
        return new String[]{ours, theirs};
    }
    private Path root() { return repo.workingDirectory(); }
    private void write(String path, String text) throws IOException {
        Path file = root().resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, text);
    }
    private String read(String path) throws IOException { return Files.readString(root().resolve(path)); }
    private StagingArea index() throws IOException { StagingArea s = new StagingArea(repo); s.load(); return s; }
    private void stage(String path) throws IOException { StagingArea s = index(); s.stagePath(Path.of(path)); s.save(); }
    private String commit() throws IOException { return commits.createCommit("Author", "Test commit"); }
    private MergeState state() throws IOException { return new MergeStateStore(repo).load(); }
    private void assertClean() throws IOException {
        StatusService s = new StatusService(repo);
        assertEquals(new WorkingTreeStatus(List.of(), List.of(), List.of()), s.inspect());
        assertEquals(new StagedStatus(List.of(), List.of(), List.of()), s.inspectStaged());
    }
}
