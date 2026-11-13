package com.vcs.core;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public final class CheckoutService {
    private final Repository repository;
    private final ReferenceStore references;
    private final CommitService commits;
    private final ObjectStore objectStore;

    public CheckoutService(Repository repository) {
        this.repository = repository;
        this.references = new ReferenceStore(repository);
        this.commits = new CommitService(repository);
        this.objectStore = new ObjectStore(repository);
    }

    public void checkout(String branch) throws IOException {
        if (new MergeStateStore(repository).load() != null) {
            throw new RepositoryStateException("Finish or abort the merge before checkout.");
        }
        requireCleanWorkingTree();

        Commit targetCommit = commits.readCommit(references.branchCommitId(branch));
        Map<String, String> targetSnapshot = commits.readSnapshot(targetCommit.snapshotId());
        String currentId = references.currentCommitId();
        Map<String, String> currentSnapshot = currentId == null ? Map.of()
                : commits.readSnapshot(commits.readCommit(currentId).snapshotId());

        restore(currentSnapshot, targetSnapshot);
        // HEAD moves last; full multi-file crash recovery belongs to the reliability stage.
        references.selectBranch(branch);
    }

    // Merge and abort share checkout's path validation and collision protection.
    void restore(Map<String, String> currentSnapshot, Map<String, String> targetSnapshot)
            throws IOException {
        validateRestore(currentSnapshot, targetSnapshot);
        Map<Path, byte[]> currentFiles = prepareFiles(currentSnapshot);
        Map<Path, byte[]> targetFiles = prepareFiles(targetSnapshot);
        List<Path> removals = planRemovals(currentFiles, targetFiles);
        checkTargetCollisions(currentFiles, targetFiles, removals);

        for (Path file : removals) {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Tracked path is no longer a regular file: " + file);
                }
                Files.delete(file);
                removeEmptyParents(file);
            }
        }
        for (Map.Entry<Path, byte[]> entry : targetFiles.entrySet()) {
            writeWorkingFile(entry.getKey(), entry.getValue());
        }

        StagingArea staging = new StagingArea(repository);
        staging.replaceEntries(targetSnapshot);
        staging.save();
    }

    void validateSnapshot(Map<String, String> snapshot) throws IOException {
        prepareFiles(snapshot);
    }

    void validateRestore(Map<String, String> currentSnapshot, Map<String, String> targetSnapshot)
            throws IOException {
        Map<Path, byte[]> current = prepareFiles(currentSnapshot);
        Map<Path, byte[]> target = prepareFiles(targetSnapshot);
        List<Path> removals = planRemovals(current, target);
        for (Path file : removals) {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IOException("Tracked path is no longer a regular file: " + file);
            }
        }
        checkTargetCollisions(current, target, removals);
    }

    private void writeWorkingFile(Path destination, byte[] content) throws IOException {
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".cvcs-checkout-", ".tmp");
        try {
            Files.write(temporary, content);
            // Replace the file rather than modifying an inode shared through a hard link.
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void requireCleanWorkingTree() throws IOException {
        StatusService status = new StatusService(repository);
        WorkingTreeStatus working = status.inspect();
        StagedStatus staged = status.inspectStaged();

        // Checkout replaces the index, so its pending changes must be committed first.
        if (!staged.added().isEmpty()
                || !staged.modified().isEmpty()
                || !staged.deleted().isEmpty()) {
                    throw new RepositoryStateException("Commit staged changes before checkout.");
        }

        if (!working.modified().isEmpty() || !working.deleted().isEmpty()) {
            throw new RepositoryStateException("Commit or restore working-file changes before checkout.");
        }

        // Initial policy protects all untracked files, including potential target collisions.
        if (!working.untracked().isEmpty()) {
            throw new RepositoryStateException("Move untracked files aside, or stage and commit them before checkout.");
        }
    }

    private Map<Path, byte[]> prepareFiles(Map<String, String> snapshot) throws IOException {
        Path root = repository.workingDirectory().toRealPath();
        Map<Path, byte[]> prepared = new TreeMap<>();

        for (Map.Entry<String, String> entry : snapshot.entrySet()) {
            Path target = WorkingPaths.resolve(root, entry.getKey());

            // Verify every target blob before checkout starts changing working files.
            byte[] content = objectStore.readBlob(entry.getValue());
            if (prepared.put(target, content) != null) {
                throw new IOException("Conflicting snapshot paths.");
            }
        }

        // A snapshot cannot use the same path as both a file and a directory.
        for (Path target : prepared.keySet()) {
            for (Path parent = target.getParent(); !parent.equals(root); parent = parent.getParent()) {
                if (prepared.containsKey(parent)) {
                    throw new IOException("File/directory conflict in snapshot.");
                }
            }
        }

        return prepared;
    }

    private List<Path> planRemovals(Map<Path, byte[]> current, Map<Path, byte[]> target) {
        List<Path> removals = new ArrayList<>();
        Set<String> targetSpellings = new HashSet<>();
        target.keySet().forEach(path -> targetSpellings.add(path.toString()));

        // Only tracked files absent from the target snapshot may be removed.
        for (Path file : current.keySet()) {
            // Recreate case-only renames too, including on case-insensitive filesystems.
            if (!target.containsKey(file) || !targetSpellings.contains(file.toString())) {
                removals.add(file);
            }
        }

        removals.sort(Comparator.comparingInt(Path::getNameCount).reversed());

        return List.copyOf(removals);
    }

    private void removeEmptyParents(Path removedFile) throws IOException {
        Path root = repository.workingDirectory().toRealPath();
        Path directory = removedFile.getParent();

        while (!directory.equals(root)) {
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                break;
            }

            try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
                if (children.iterator().hasNext()) {
                    break;
                }
            }

            // Prune empty containers without deleting unrelated contents.
            Files.delete(directory);
            directory = directory.getParent();
        }
    }

    private void checkTargetCollisions(
            Map<Path, byte[]> current,
            Map<Path, byte[]> target,
            List<Path> removals) throws IOException {
        Path root = repository.workingDirectory().toRealPath();
        Set<Path> removable = new HashSet<>(removals);

        for (Path destination : target.keySet()) {
            if (Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isDirectory(destination, LinkOption.NOFOLLOW_LINKS)) {
                    requireRemovableDirectory(destination, removable);
                } else if (!Files.isRegularFile(destination, LinkOption.NOFOLLOW_LINKS)
                        || !current.containsKey(destination)) {
                    throw new IOException(
                            "Checkout would overwrite an untracked path: " + destination);
                }
            }

            // A blocking parent file must be scheduled for removal.
            for (Path parent = destination.getParent(); !parent.equals(root);
                    parent = parent.getParent()) {
                // A directory case rename must also be able to prune its old spelling.
                if (Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                        && !parent.toRealPath().toString().equals(parent.toString())) {
                    requireRemovableDirectory(parent, removable);
                }
                if (Files.exists(parent, LinkOption.NOFOLLOW_LINKS)
                        && !Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)
                        && !removable.contains(parent)) {
                    throw new IOException("Checkout path is blocked: " + parent);
                }
            }
        }
    }

    private void requireRemovableDirectory(
            Path directory, Set<Path> removable) throws IOException {

        // Cleanup can remove a directory only when tracked deletions empty it.
        if (removable.stream().noneMatch(file -> file.startsWith(directory))) {
            throw new IOException(
                    "Checkout would overwrite an untracked directory: " + directory);
        }

        try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
            for (Path child : children) {
                if (Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    requireRemovableDirectory(child, removable);
                } else if (!Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)
                        || !removable.contains(child)) {
                    throw new IOException(
                            "Checkout would overwrite untracked contents: " + child);
                }
            }
        }
    }
}
