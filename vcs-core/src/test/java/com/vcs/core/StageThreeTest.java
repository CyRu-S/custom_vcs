package com.vcs.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StageThreeTest {
    @TempDir
    Path temporaryDirectory;

    private Repository repository;
    private CommitService commits;

    @BeforeEach
    void initializeRepository() throws IOException {
        // Each test owns an isolated repository, never the learning sandbox.
        repository = new Repository(temporaryDirectory.resolve("repo"));
        repository.initialize();
        commits = new CommitService(repository);
    }

    @Test
    void commitCapturesTheIndexWhileStatusKeepsUnstagedAndUntrackedChanges() throws IOException {
        writeFile("hello.txt", "staged content");
        stage("hello.txt");
        String stagedBlob = loadIndex().entries().get("hello.txt");

        writeFile("hello.txt", "unstaged content");
        writeFile("draft.txt", "untracked content");

        String commitId = commits.createCommit("Test Author", "Initial snapshot");
        Commit commit = commits.readCommit(commitId);

        assertNull(commit.parentId());
        assertEquals("Test Author", commit.author());
        assertEquals("Initial snapshot", commit.message());
        assertEquals(Map.of("hello.txt", stagedBlob), commits.readSnapshot(commit.snapshotId()));
        assertArrayEquals("staged content".getBytes(StandardCharsets.UTF_8),
                new ObjectStore(repository).readBlob(stagedBlob));
        assertEquals(commitId, new ReferenceStore(repository).currentCommitId());

        StatusService status = new StatusService(repository);
        assertEquals(new StagedStatus(List.of(), List.of(), List.of()), status.inspectStaged());
        assertEquals(new WorkingTreeStatus(List.of("hello.txt"), List.of(), List.of("draft.txt")),
                status.inspect());
    }

    @Test
    void separateAddInvocationsPreserveEarlierEntriesAndRestagingReplacesOnlyOnePath()
            throws IOException {
        writeFile("first.txt", "first version");
        stage("first.txt");
        String originalFirst = loadIndex().entries().get("first.txt");

        writeFile("second.txt", "second file");
        stage("second.txt");
        Map<String, String> before = loadIndex().entries();
        assertEquals(2, before.size());
        assertEquals(originalFirst, before.get("first.txt"));

        writeFile("first.txt", "new first version");
        stage("first.txt");
        Map<String, String> after = loadIndex().entries();
        assertEquals(2, after.size());
        assertNotEquals(originalFirst, after.get("first.txt"));
        assertEquals(before.get("second.txt"), after.get("second.txt"));
    }

    @Test
    void commitsLinkToTheirParentsAndHistoryRunsNewestFirst() throws IOException {
        writeFile("hello.txt", "version one");
        stage("hello.txt");
        String firstId = commits.createCommit("Author", "First");

        writeFile("hello.txt", "version two");
        stage("hello.txt");
        assertEquals(List.of("hello.txt"), new StatusService(repository).inspectStaged().modified());
        String secondId = commits.createCommit("Author", "Second");

        assertEquals(firstId, commits.readCommit(secondId).parentId());
        assertEquals(secondId, new ReferenceStore(repository).currentCommitId());
        List<CommitEntry> history = commits.history();
        assertEquals(List.of(secondId, firstId), history.stream().map(CommitEntry::id).toList());
        assertEquals(List.of("Second", "First"),
                history.stream().map(entry -> entry.commit().message()).toList());
    }

    @Test
    void stagedDeletionCanProduceAnEmptySnapshotWithoutDestroyingHistory() throws IOException {
        writeFile("hello.txt", "keep this in history");
        stage("hello.txt");
        String firstId = commits.createCommit("Author", "Add file");
        Map<String, String> firstSnapshot = commits.readSnapshot(commits.readCommit(firstId).snapshotId());

        Files.delete(repository.workingDirectory().resolve("hello.txt"));
        StatusService status = new StatusService(repository);
        assertEquals(List.of("hello.txt"), status.inspect().deleted());
        assertTrue(status.inspectStaged().deleted().isEmpty());

        stage("hello.txt");
        assertTrue(loadIndex().entries().isEmpty());
        assertEquals(List.of("hello.txt"), status.inspectStaged().deleted());
        assertTrue(status.inspect().deleted().isEmpty());

        String secondId = commits.createCommit("Author", "Delete file");
        assertTrue(commits.readSnapshot(commits.readCommit(secondId).snapshotId()).isEmpty());
        assertEquals(firstSnapshot, commits.readSnapshot(commits.readCommit(firstId).snapshotId()));
        assertArrayEquals("keep this in history".getBytes(StandardCharsets.UTF_8),
                new ObjectStore(repository).readBlob(firstSnapshot.get("hello.txt")));
    }

    @Test
    void unstagedEditsDoNotPermitAnotherCommitOrMoveTheBranch() throws IOException {
        writeFile("hello.txt", "staged version");
        stage("hello.txt");
        String firstId = commits.createCommit("Author", "First");
        writeFile("hello.txt", "only the working file changed");

        assertThrows(IOException.class, () -> commits.createCommit("Author", "No staged change"));
        assertEquals(firstId, new ReferenceStore(repository).currentCommitId());
        assertEquals(1, commits.history().size());
    }

    @Test
    void corruptBlobPreventsCommitAndLeavesTheBranchWithoutACommit() throws IOException {
        writeFile("hello.txt", "original");
        stage("hello.txt");
        String blobId = loadIndex().entries().get("hello.txt");
        Path blobPath = repository.metadataDirectory().resolve("objects")
                .resolve(blobId.substring(0, 2)).resolve(blobId.substring(2));
        Files.write(blobPath, new byte[] {1, 2, 3});

        assertThrows(IOException.class, () -> commits.createCommit("Author", "Must fail"));
        assertNull(new ReferenceStore(repository).currentCommitId());
    }

    @Test
    void brokenIndexDoesNotPartiallyReplaceLoadedEntries() throws IOException {
        writeFile("hello.txt", "staged");
        stage("hello.txt");
        StagingArea staging = loadIndex();
        Map<String, String> original = staging.entries();
        Path indexPath = repository.metadataDirectory().resolve("index");
        byte[] encoded = Files.readAllBytes(indexPath);
        Files.write(indexPath, Arrays.copyOf(encoded, encoded.length - 1));

        assertThrows(IOException.class, staging::load);
        assertEquals(original, staging.entries());
    }

    @Test
    void snapshotEncodingIgnoresMapInsertionOrderAndRoundTripsEntries() throws IOException {
        Map<String, String> forward = new LinkedHashMap<>();
        forward.put("a.txt", "a".repeat(64));
        forward.put("b.txt", "b".repeat(64));
        Map<String, String> reverse = new LinkedHashMap<>();
        reverse.put("b.txt", "b".repeat(64));
        reverse.put("a.txt", "a".repeat(64));

        byte[] encoded = SnapshotCodec.encode(forward);
        assertArrayEquals(encoded, SnapshotCodec.encode(reverse));
        assertEquals(forward, SnapshotCodec.decode(encoded));
        assertThrows(IOException.class,
                () -> SnapshotCodec.decode(Arrays.copyOf(encoded, encoded.length - 1)));
    }

    @Test
    void commitCodecPreservesParentAndNanosecondsAndRejectsTrailingData() throws IOException {
        Instant timestamp = Instant.parse("2026-01-02T03:04:05.123456789Z");
        List<Commit> examples = List.of(
                new Commit("a".repeat(64), null, "Author", timestamp, "First\nDetails"),
                new Commit("a".repeat(64), "b".repeat(64), "Author", timestamp, "Second"));

        for (Commit original : examples) {
            byte[] encoded = CommitCodec.encode(original);
            assertEquals(original, CommitCodec.decode(encoded));
            assertThrows(IOException.class,
                    () -> CommitCodec.decode(Arrays.copyOf(encoded, encoded.length + 1)));
        }
    }

    private void writeFile(String name, String content) throws IOException {
        Path file = repository.workingDirectory().resolve(name);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private StagingArea loadIndex() throws IOException {
        StagingArea staging = new StagingArea(repository);
        staging.load();
        return staging;
    }

    private void stage(String path) throws IOException {
        // Match the CLI's persistence boundary across separate add invocations.
        StagingArea staging = loadIndex();
        staging.stagePath(Path.of(path));
        staging.save();
    }
}
