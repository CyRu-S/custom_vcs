package com.vcs.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class DiffServiceTest {
    @TempDir Path root;
    private Repository repository;
    private DiffService diffs;

    @BeforeEach
    void initialize() throws IOException {
        repository = new Repository(root);
        repository.initialize();
        diffs = new DiffService(repository);
    }

    @Test
    void workingAndStagedDiffsUseIndependentBaselinesAndDoNotStoreObjects() throws IOException {
        write("hello.txt", "one\ntwo\n");
        stage("hello.txt");
        new CommitService(repository).createCommit("Author", "Initial");
        write("hello.txt", "one\nstaged\n");
        stage("hello.txt");
        write("hello.txt", "one\nworking\n");
        write("untracked.txt", "not part of diff");
        long before = objectCount();

        FileDiff staged = diffs.staged().getFirst();
        assertText(staged, "one\ntwo\n", "one\nstaged\n");
        FileDiff working = diffs.workingTree().getFirst();
        assertText(working, "one\nstaged\n", "one\nworking\n");
        assertEquals(1, diffs.workingTree().size());
        assertEquals(before, objectCount());
    }

    @Test
    void stagedDiffIncludesAdditionsDeletionsAndEmptyFiles() throws IOException {
        write("old.txt", "old\n");
        stage("old.txt");
        new CommitService(repository).createCommit("Author", "Initial");
        Files.delete(root.resolve("old.txt"));
        stage("old.txt");
        write("empty.txt", "");
        stage("empty.txt");
        List<FileDiff> changes = diffs.staged();
        assertEquals(List.of("empty.txt", "old.txt"), changes.stream().map(FileDiff::path).toList());
        assertEquals(FileDiff.Change.ADDED, changes.get(0).change());
        assertTrue(changes.get(0).lines().isEmpty());
        assertEquals(FileDiff.Change.DELETED, changes.get(1).change());
        assertText(changes.get(1), "old\n", "");
    }

    @Test
    void firstCommitBaselineIsEmptyAndMissingWorkingFileIsADeletion() throws IOException {
        write("new.txt", "new content");
        stage("new.txt");
        assertEquals(FileDiff.Change.ADDED, diffs.staged().getFirst().change());
        assertText(diffs.staged().getFirst(), "", "new content");
        Files.delete(root.resolve("new.txt"));
        assertEquals(FileDiff.Change.DELETED, diffs.workingTree().getFirst().change());
        assertText(diffs.workingTree().getFirst(), "new content", "");
    }

    @Test
    void nulBytesAndInvalidUtf8AreReportedAsBinary() throws IOException {
        Files.write(root.resolve("nul.bin"), new byte[] {0, 1});
        Files.write(root.resolve("invalid.bin"), new byte[] {(byte) 0xc3, 0x28});
        stage("nul.bin");
        stage("invalid.bin");
        assertTrue(diffs.staged().stream().allMatch(FileDiff::binary));
        assertTrue(diffs.staged().stream().allMatch(change -> change.lines().isEmpty()));
    }

    @Test
    void newlineAndCrLfChangesRemainVisible() throws IOException {
        write("hello.txt", "first\r\nlast\n");
        stage("hello.txt");
        write("hello.txt", "first\nlast");
        FileDiff diff = diffs.workingTree().getFirst();
        assertText(diff, "first\r\nlast\n", "first\nlast");
        assertTrue(diff.lines().stream().anyMatch(line -> line.kind() == DiffLine.Kind.ADDED
                && line.text().equals("last")));
    }

    @Test
    void lineEditsReconstructBothInputsAcrossRepeatedAndEmptyLines() {
        Random random = new Random(42);
        for (int example = 0; example < 100; example++) {
            String before = randomText(random);
            String after = randomText(random);
            List<DiffLine> lines = LineDiff.compare(LineDiff.lines(before), LineDiff.lines(after));
            assertEquals(before, reconstruct(lines, false));
            assertEquals(after, reconstruct(lines, true));
        }
    }

    @Test
    void largeChangedRegionsUseBoundedMemoryAndStillReconstructBothInputs() {
        List<String> before = java.util.stream.IntStream.range(0, 2_100)
                .mapToObj(i -> "old " + i + "\n").toList();
        List<String> after = java.util.stream.IntStream.range(0, 2_100)
                .mapToObj(i -> "new " + i + "\n").toList();
        List<DiffLine> lines = LineDiff.compare(before, after);
        assertEquals(String.join("", before), reconstruct(lines, false));
        assertEquals(String.join("", after), reconstruct(lines, true));
    }

    private String randomText(Random random) {
        StringBuilder result = new StringBuilder();
        for (int i = random.nextInt(12); i > 0; i--) {
            result.append(random.nextInt(4)).append('\n');
        }
        if (random.nextBoolean()) { result.append("tail"); }
        return result.toString();
    }
    private static String reconstruct(List<DiffLine> lines, boolean after) {
        StringBuilder result = new StringBuilder();
        for (DiffLine line : lines) {
            if (line.kind() == DiffLine.Kind.CONTEXT || line.kind()
                    == (after ? DiffLine.Kind.ADDED : DiffLine.Kind.REMOVED)) {
                result.append(line.text());
            }
        }
        return result.toString();
    }
    private static void assertText(FileDiff diff, String before, String after) {
        assertFalse(diff.binary());
        assertEquals(before, reconstruct(diff.lines(), false));
        assertEquals(after, reconstruct(diff.lines(), true));
    }
    private long objectCount() throws IOException {
        try (var paths = Files.walk(repository.metadataDirectory().resolve("objects"))) {
            return paths.filter(Files::isRegularFile).count();
        }
    }
    private void write(String name, String text) throws IOException { Files.writeString(root.resolve(name), text); }
    private void stage(String name) throws IOException {
        StagingArea staging = new StagingArea(repository);
        staging.load();
        staging.stagePath(Path.of(name));
        staging.save();
    }
}
