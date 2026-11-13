package com.vcs.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StageFourTest {
    @TempDir Path temporary;
    private Repository repository;
    private ReferenceStore references;
    private CommitService commits;
    private CheckoutService checkout;

    @BeforeEach
    void initialize() throws IOException {
        repository = new Repository(temporary.resolve("repo"));
        repository.initialize();
        references = new ReferenceStore(repository);
        commits = new CommitService(repository);
        checkout = new CheckoutService(repository);
    }

    @Test
    void branchesRequireACommitAndRejectDuplicatesAndInvalidNames() throws IOException {
        assertThrows(IOException.class, () -> references.createBranch("feature"));
        write("hello.txt", "initial");
        stage("hello.txt");
        String initial = commit("Initial");
        references.createBranch("feature");
        assertEquals(List.of("feature", "main"), references.listBranches());
        assertEquals("main", references.currentBranchName());
        assertEquals(initial, references.branchCommitId("feature"));
        assertThrows(IOException.class, () -> references.createBranch("feature"));
        assertThrows(IOException.class, () -> references.createBranch("../escape"));
        assertEquals(initial, references.branchCommitId("feature"));
    }

    @Test
    void switchingBranchesRestoresFilesIndexAndIndependentHistory() throws IOException {
        write("hello.txt", "initial");
        stage("hello.txt");
        String initial = commit("Initial");
        references.createBranch("feature");
        write("hello.txt", "main version");
        write("main-only.txt", "main only");
        stage("hello.txt");
        stage("main-only.txt");
        String main = commit("Main update");

        checkout.checkout("feature");
        assertEquals("initial", read("hello.txt"));
        assertFalse(Files.exists(root().resolve("main-only.txt")));
        assertEquals("feature", references.currentBranchName());
        assertEquals(initial, references.currentCommitId());

        write("hello.txt", "feature version");
        write("src/new.txt", "nested feature file");
        stage("hello.txt");
        stage("src/new.txt");
        String feature = commit("Feature update");
        checkout.checkout("main");
        assertEquals("main version", read("hello.txt"));
        assertFalse(Files.exists(root().resolve("src")));
        assertEquals(main, references.currentCommitId());
        assertEquals(main, commits.history().getFirst().id());

        checkout.checkout("feature");
        assertEquals("nested feature file", read("src/new.txt"));
        assertEquals(feature, references.currentCommitId());
        assertEquals(commits.readSnapshot(commits.readCommit(feature).snapshotId()), index().entries());
        assertEquals(main, references.branchCommitId("main"));
        assertClean();
    }

    @Test
    void dirtyWorkingFilePreventsAnyCheckoutMutation() throws IOException {
        seed();
        write("hello.txt", "uncommitted");
        byte[] oldIndex = Files.readAllBytes(repository.metadataDirectory().resolve("index"));
        assertThrows(IOException.class, () -> checkout.checkout("feature"));
        assertEquals("uncommitted", read("hello.txt"));
        assertEquals("main", references.currentBranchName());
        assertArrayEquals(oldIndex, Files.readAllBytes(repository.metadataDirectory().resolve("index")));
    }

    @Test
    void stagedAndUntrackedChangesBothPreventCheckout() throws IOException {
        seed();
        write("hello.txt", "staged update");
        stage("hello.txt");
        assertThrows(IOException.class, () -> checkout.checkout("feature"));
        commit("Commit update");
        write("draft.txt", "untracked");
        assertThrows(IOException.class, () -> checkout.checkout("feature"));
        assertEquals("untracked", read("draft.txt"));
        assertEquals("main", references.currentBranchName());
    }

    @Test
    void missingBranchDoesNotChangeFilesOrHead() throws IOException {
        seed();
        assertThrows(IOException.class, () -> checkout.checkout("missing"));
        assertEquals("initial", read("hello.txt"));
        assertEquals("main", references.currentBranchName());
    }

    @Test
    void fileAndDirectoryTransitionsWorkInBothDirections() throws IOException {
        write("node", "file version");
        stage("node");
        commit("File layout");
        references.createBranch("file-layout");

        Files.delete(root().resolve("node"));
        stage("node");
        write("node/deep/child.txt", "directory version");
        stage("node/deep/child.txt");
        commit("Directory layout");

        checkout.checkout("file-layout");
        assertEquals("file version", read("node"));
        checkout.checkout("main");
        assertEquals("directory version", read("node/deep/child.txt"));
        assertClean();
    }

    @Test
    void emptyUntrackedDirectoryCollisionIsRejectedBeforeOtherFilesChange() throws IOException {
        seed();
        write("hello.txt", "target update");
        write("blocked", "target file");
        stage("hello.txt");
        stage("blocked");
        commit("Target");
        checkout.checkout("feature");
        Files.createDirectory(root().resolve("blocked"));

        assertThrows(IOException.class, () -> checkout.checkout("main"));
        assertTrue(Files.isDirectory(root().resolve("blocked")));
        assertEquals("initial", read("hello.txt"));
        assertEquals("feature", references.currentBranchName());
    }

    @Test
    void untrackedEmptySubdirectoryBlocksDirectoryReplacementWithoutDeletingTrackedChildren()
            throws IOException {
        write("node", "file");
        stage("node");
        commit("File");
        references.createBranch("file-layout");
        Files.delete(root().resolve("node"));
        stage("node");
        write("node/child.txt", "child");
        stage("node/child.txt");
        commit("Directory");
        Files.createDirectory(root().resolve("node/keep"));

        assertThrows(IOException.class, () -> checkout.checkout("file-layout"));
        assertEquals("child", read("node/child.txt"));
        assertTrue(Files.isDirectory(root().resolve("node/keep")));
        assertEquals("main", references.currentBranchName());
    }

    @Test
    void corruptTargetBlobIsRejectedBeforeChangingWorkingFilesOrHead() throws IOException {
        seed();
        ObjectStore objects = new ObjectStore(repository);
        String id = objects.writeBlob("target".getBytes(StandardCharsets.UTF_8));
        pointBranch("broken", Map.of("hello.txt", id));
        Files.write(repository.metadataDirectory().resolve("objects")
                .resolve(id.substring(0, 2)).resolve(id.substring(2)), new byte[] {7, 8});
        assertThrows(IOException.class, () -> checkout.checkout("broken"));
        assertEquals("initial", read("hello.txt"));
        assertEquals("main", references.currentBranchName());
    }

    @Test
    void traversalAndConflictingSnapshotPathsAreRejectedWithoutMutation() throws IOException {
        seed();
        String blob = new ObjectStore(repository).writeBlob(new byte[] {1});
        pointBranch("escape", Map.of("../outside.txt", blob));
        assertThrows(IOException.class, () -> checkout.checkout("escape"));
        assertFalse(Files.exists(temporary.resolve("outside.txt")));
        pointBranch("conflict", Map.of("node", blob, "node/child", blob));
        assertThrows(IOException.class, () -> checkout.checkout("conflict"));
        assertEquals("initial", read("hello.txt"));
        assertEquals("main", references.currentBranchName());
    }

    @Test
    void caseOnlyFileAndDirectoryRenamesRestoreTheStoredSpelling() throws IOException {
        write("Folder/Name.txt", "same bytes");
        stage("Folder/Name.txt");
        commit("Original spelling");
        references.createBranch("original");
        Files.move(root().resolve("Folder"), root().resolve("temporary-name"));
        Files.move(root().resolve("temporary-name"), root().resolve("folder"));
        Files.move(root().resolve("folder/Name.txt"), root().resolve("folder/temporary.txt"));
        Files.move(root().resolve("folder/temporary.txt"), root().resolve("folder/name.txt"));
        // Set the intended snapshot explicitly; this also exercises portable stored path spelling.
        StagingArea staged = index();
        String blob = staged.entries().get("Folder/Name.txt");
        staged.replaceEntries(Map.of("folder/name.txt", blob));
        staged.save();
        commit("Changed spelling");

        checkout.checkout("original");
        assertTrue(root().resolve("Folder/Name.txt").toRealPath().toString().endsWith("Folder"
                + root().getFileSystem().getSeparator() + "Name.txt"));
        checkout.checkout("main");
        assertTrue(root().resolve("folder/name.txt").toRealPath().toString().endsWith("folder"
                + root().getFileSystem().getSeparator() + "name.txt"));
        assertClean();
    }

    private void pointBranch(String name, Map<String, String> snapshot) throws IOException {
        ObjectStore objects = new ObjectStore(repository);
        String snapshotId = objects.writeObject("snapshot", SnapshotCodec.encode(snapshot));
        String commitId = objects.writeObject("commit", CommitCodec.encode(new Commit(snapshotId,
                references.currentCommitId(), "Author", Instant.EPOCH, "Test target")));
        Files.writeString(repository.metadataDirectory().resolve("refs/heads").resolve(name), commitId + "\n");
    }

    private void seed() throws IOException {
        write("hello.txt", "initial");
        stage("hello.txt");
        commit("Initial");
        references.createBranch("feature");
    }

    private Path root() { return repository.workingDirectory(); }
    private String read(String path) throws IOException { return Files.readString(root().resolve(path)); }
    private void write(String path, String text) throws IOException {
        Path file = root().resolve(path);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }
    private StagingArea index() throws IOException {
        StagingArea staging = new StagingArea(repository);
        staging.load();
        return staging;
    }
    private void stage(String name) throws IOException {
        StagingArea staging = index();
        staging.stagePath(Path.of(name));
        staging.save();
    }
    private String commit(String message) throws IOException { return commits.createCommit("Author", message); }
    private void assertClean() throws IOException {
        StatusService status = new StatusService(repository);
        assertEquals(new WorkingTreeStatus(List.of(), List.of(), List.of()), status.inspect());
        assertEquals(new StagedStatus(List.of(), List.of(), List.of()), status.inspectStaged());
    }
}
