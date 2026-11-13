package com.vcs.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StatusService {

    private final Repository repository;
    private final ObjectStore objectStore;

    public StatusService(Repository repository) {
        this.repository = repository;
        this.objectStore = new ObjectStore(repository);
    }

    private boolean isModified(Path file, String stagedObjectId) throws IOException {
        // Compare current content with the staged blob without storing anything.
        byte[] content = Files.readAllBytes(file);
        String currentObjectId = objectStore.hashBlob(content);

        return !currentObjectId.equals(stagedObjectId);
    }

    // Find indexed files that are missing or changed.
    private void compareIndexedFiles(Map<String, String> indexed, List<String> modified, List<String> deleted) throws IOException {

        // Resolve the repository root before checking file boundaries.
        Path root = repository.workingDirectory().toRealPath();

        for (Map.Entry<String, String> entry : indexed.entrySet()) {

            Path file = root.resolve(entry.getKey()).normalize();
    
            // Index paths must stay in working files, never in repository metadata.
            if (!file.startsWith(root) || file.startsWith(root.resolve(".customvcs"))) {
                throw new IOException("Invalid path in index.");
            }
    
            // A missing indexed file is deleted; a changed file or type is modified.
            // A symbolic link replacing a regular file counts as a type change.
            if (Files.notExists(file, LinkOption.NOFOLLOW_LINKS)) {
                deleted.add(entry.getKey());
            } else if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                modified.add(entry.getKey());
            } else {
                // Check the actual location before reading the file.
                Path actualFile = file.toRealPath();
    
                if (!actualFile.startsWith(root) || actualFile.startsWith(root.resolve(".customvcs"))) {
                    throw new IOException("Indexed file resolves outside working files.");
                }
    
                if (isModified(actualFile, entry.getValue())) {
                    modified.add(entry.getKey());
                }
            }
        }
    }

    // Find working files whose paths are absent from the index.
    private void findUntrackedFiles(Map<String, String> indexed, List<String> untracked) throws IOException {
        Path root = repository.workingDirectory().toRealPath();
        Path metadata = root.resolve(".customvcs");

        // Prune metadata so stored objects never appear as working files.
        Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
            @Override 
            public FileVisitResult preVisitDirectory (Path directory, BasicFileAttributes attributes ) {
                    return directory.equals(metadata) ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
            }

            @Override 
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                // This version scans regular file content, not symbolic links.
                if (attributes.isRegularFile()) {
                    // Use the same repository-relative path format as the index.
                    String relativePath = root.relativize(file)
                                        .toString()
                                        .replace('\\', '/');

                    if (!indexed.containsKey(relativePath)) {
                        untracked.add(relativePath);
                    }
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    // Combine the working-file checks into one status result.
    public WorkingTreeStatus inspect() throws IOException {
        if (!Files.isDirectory(repository.metadataDirectory())) {
            throw new IOException("Not a CustomVCS repository.");
        }

        StagingArea stagingArea = new StagingArea(repository);
        stagingArea.load();
        Map<String, String> indexed = stagingArea.entries();

        List<String> modified = new ArrayList<>();
        List<String> deleted = new ArrayList<>();
        List<String> untracked = new ArrayList<>();

        compareIndexedFiles(indexed, modified, deleted);
        findUntrackedFiles(indexed, untracked);

        // Keep output stable across filesystem traversal order.
        modified.sort(String::compareTo);
        deleted.sort(String::compareTo);
        untracked.sort(String::compareTo);

        return new WorkingTreeStatus(modified, deleted, untracked);
    }

    public StagedStatus inspectStaged() throws IOException {
        StagingArea stagingArea = new StagingArea(repository);
        stagingArea.load();
        Map<String, String> indexed = stagingArea.entries();

        String commitId = new ReferenceStore(repository).currentCommitId();

        // With no current commit, every indexed file is a staged addition.
        Map<String, String> committed = Map.of();

        if (commitId != null) {
            CommitService commits = new CommitService(repository);
            Commit current = commits.readCommit(commitId);
            committed = commits.readSnapshot(current.snapshotId());
        }

        List<String> added = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> deleted = new ArrayList<>();

        // Compare the next snapshot with the last committed snapshot.
        for (Map.Entry<String, String> entry : indexed.entrySet()) {
            String path = entry.getKey();

            if (!committed.containsKey(path)) {
                added.add(path);
            } else if (!entry.getValue().equals(committed.get(path))) {
                modified.add(path);
            }
        }

        // Removing an entry removes that path from the next commit.
        for (String path : committed.keySet()) {
            if (!indexed.containsKey(path)) {
                deleted.add(path);
            }
        }

        added.sort(String::compareTo);
        modified.sort(String::compareTo);
        deleted.sort(String::compareTo);

        return new StagedStatus(added, modified, deleted);
    }
} 