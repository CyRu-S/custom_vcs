package com.vcs.core;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class StagingArea {

    private final Path indexPath;
    // CVSI means Custom VCS Index: the marker at the start of our index file.
    private static final int INDEX_MAGIC = 0x43565349;
    private static final int INDEX_VERSION = 1;

    private final Repository repository;

    // Keep staged paths sorted so saving has a predictable order.
    private final Map<String, String> entries = new TreeMap<>();
    private final java.util.Set<String> stagedPaths = new java.util.HashSet<>();

    public StagingArea(Repository repository) {
        this.indexPath = repository.metadataDirectory().resolve("index");
        this.repository = repository;
    }

    public void save() throws IOException {
        try (DataOutputStream output = new DataOutputStream(
            new BufferedOutputStream(Files.newOutputStream(indexPath)))) {
                
                // Write the format marker, version, and number of staged files.
                output.writeInt(INDEX_MAGIC);
                output.writeInt(INDEX_VERSION);
                output.writeInt(entries.size());

                for (Map.Entry<String, String> entry : entries.entrySet()) {
                    // Save each file path followed by its blob ID.
                    output.writeUTF(entry.getKey());
                    output.writeUTF(entry.getValue());
                }
        }
        // A conflict is resolved only after its chosen bytes or deletion reach the index.
        new MergeStateStore(repository).resolve(stagedPaths);
        stagedPaths.clear();
    }

    public void load() throws IOException {
        stagedPaths.clear();
        // No index yet means nothing is staged.
        if (!Files.exists(indexPath)) {
            entries.clear();
            return;
        }

        // Read into a temporary map so a broken index cannot leave partial entries.
        Map<String, String> loaded = new TreeMap<>();

        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
            Files.newInputStream(indexPath)))) {
                if (input.readInt() != INDEX_MAGIC || input.readInt() != INDEX_VERSION) {
                    throw new IOException("Unsupported index format.");
                } 

                int count = input.readInt();
                if (count < 0) {
                    throw new IOException("Invalid index entry count.");
                }

                for (int i = 0; i<count; i++) {
                    String path = input.readUTF();
                    String objectId = input.readUTF();

                    if (loaded.put(path, objectId) != null) {
                        throw new IOException("Duplicate path in index.");
                    }
                }

                if (input.read() != -1) {
                    throw new IOException("Unexpected data after index entries.");
                }
        }

        entries.clear();
        // Replace the entries only after the whole file is valid.
        entries.putAll(loaded);
    }

    public String stageFile(Path file) throws IOException {
        // Resolve the actual file location before checking its boundaries.
        Path root = repository.workingDirectory().toRealPath();
        Path filePath = root.resolve(file).toRealPath();

        if (!filePath.startsWith(root) || filePath.startsWith(root.resolve(".customvcs"))) {
            throw new IOException("File must be inside the working directory.");
        }

        if (!Files.isRegularFile(filePath)) {
            throw new RepositoryStateException("Only regular files can be staged.");
        }

        // Store the file's current bytes as a blob.
        byte[] content = Files.readAllBytes(filePath);
        String objectId = new ObjectStore(repository).writeBlob(content);

        String relativePath = root.relativize(filePath)
                            .toString()
                            .replace('\\', '/');
        
        // Staging this path again replaces its previous blob ID.
        // On case-insensitive filesystems, a case-only rename replaces the old spelling.
        entries.keySet().removeIf(existing -> !existing.equals(relativePath)
                && Path.of(existing).equals(Path.of(relativePath)));
        entries.put(relativePath, objectId);
        stagedPaths.add(relativePath);
        return objectId;
    }

    public Map<String, String> entries() {
        // Give callers a sorted copy they cannot change.
        return Collections.unmodifiableMap(new TreeMap<>(entries));
    }

    public void replaceEntries(Map<String, String> snapshot) {
        Map<String, String> replacement = Map.copyOf(snapshot);
        // Checkout replaces the entire index, including paths absent from the target.
        entries.clear();
        entries.putAll(replacement);
    }

    public void stagePath(Path file) throws IOException {
        Path candidate = repository.workingDirectory()
                    .resolve(file)
                    .normalize();

        // Adding a missing indexed path stages its deletion.
        if (Files.notExists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            stageRemoval(file);
        } else {
            stageFile(file);
        }
    }

    private void stageRemoval(Path file) throws IOException {
        Path root = repository.workingDirectory().toRealPath();
        Path filePath = root.resolve(file).normalize();

        if (!filePath.startsWith(root) || filePath.equals(root) 
                || filePath.startsWith(root.resolve(".customvcs"))) {
            throw new IOException("Invalid path for staged deletion.");
        }

        String relativePath = root.relativize(filePath)
                .toString()
                .replace('\\', '/');
        
        // Old commits retain their blobs; only the next snapshot loses this path.
        MergeState merge = new MergeStateStore(repository).load();
        if (entries.remove(relativePath) == null
                && (merge == null || !merge.conflicts().contains(relativePath))) {
            throw new RepositoryStateException("File is not in the index: " + relativePath);
        }
        stagedPaths.add(relativePath);
    }
}
