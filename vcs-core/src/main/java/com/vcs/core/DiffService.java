package com.vcs.core;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

public final class DiffService {
    private final Repository repository;
    private final ObjectStore objects;

    public DiffService(Repository repository) {
        this.repository = repository;
        this.objects = new ObjectStore(repository);
    }

    private Map<String, String> index() throws IOException {
        if (!Files.isDirectory(repository.metadataDirectory())) {
            throw new IOException("Not a CustomVCS repository.");
        }
        StagingArea staging = new StagingArea(repository);
        staging.load();
        return staging.entries();
    }

    public List<FileDiff> workingTree() throws IOException {
        Map<String, String> indexed = index();
        Path root = repository.workingDirectory().toRealPath();
        List<FileDiff> result = new ArrayList<>();
        // Untracked files have no index baseline and are shown by status instead.
        for (String name : new TreeSet<>(indexed.keySet())) {
            Path file = WorkingPaths.resolve(root, name);
            byte[] before = objects.readBlob(indexed.get(name));
            byte[] after = null;
            if (!Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Cannot diff a non-regular working path: " + name);
                }
                after = Files.readAllBytes(file);
            }
            append(result, name, before, after);
        }
        return List.copyOf(result);
    }

    public List<FileDiff> staged() throws IOException {
        Map<String, String> indexed = index();
        String currentId = new ReferenceStore(repository).currentCommitId();
        Map<String, String> committed = Map.of();
        if (currentId != null) {
            CommitService commits = new CommitService(repository);
            committed = commits.readSnapshot(commits.readCommit(currentId).snapshotId());
        }
        TreeSet<String> names = new TreeSet<>(committed.keySet());
        names.addAll(indexed.keySet());
        List<FileDiff> result = new ArrayList<>();
        for (String name : names) {
            String oldId = committed.get(name);
            String newId = indexed.get(name);
            if (oldId != null && oldId.equals(newId)) {
                continue;
            }
            append(result, name, oldId == null ? null : objects.readBlob(oldId),
                    newId == null ? null : objects.readBlob(newId));
        }
        return List.copyOf(result);
    }

    private void append(List<FileDiff> result, String name, byte[] before, byte[] after) {
        if (before != null && after != null && Arrays.equals(before, after)) {
            return;
        }
        FileDiff.Change change = before == null ? FileDiff.Change.ADDED
                : after == null ? FileDiff.Change.DELETED : FileDiff.Change.MODIFIED;
        String oldText = text(before);
        String newText = text(after);
        if (oldText == null || newText == null) {
            result.add(new FileDiff(name, change, true, 0, 0, List.of()));
            return;
        }
        List<String> oldLines = LineDiff.lines(oldText);
        List<String> newLines = LineDiff.lines(newText);
        result.add(new FileDiff(name, change, false, oldLines.size(), newLines.size(),
                LineDiff.compare(oldLines, newLines)));
    }

    private String text(byte[] content) {
        if (content == null) {
            return "";
        }
        for (byte value : content) {
            if (value == 0) {
                return null;
            }
        }
        // Never replace invalid bytes with characters and pretend that binary data is text.
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content)).toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }
}
