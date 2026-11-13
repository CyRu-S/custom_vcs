package com.vcs.core;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class MergeStateStore {
    private final Path path;

    public MergeStateStore(Repository repository) {
        path = repository.metadataDirectory().resolve("MERGE_STATE");
    }

    public MergeState load() throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Invalid merge-state file.");
        }
        try (DataInputStream input = new DataInputStream(Files.newInputStream(path))) {
            if (input.readInt() != 0x43564D31) throw new IOException("Unsupported merge state.");
            String ours = input.readUTF();
            String theirs = input.readUTF();
            if (!ours.matches("[0-9a-f]{64}") || !theirs.matches("[0-9a-f]{64}")) {
                throw new IOException("Invalid merge parent IDs.");
            }
            String current = input.readUTF();
            String source = input.readUTF();
            int length = input.readInt();
            if (length < 0 || length > input.available()) throw new IOException("Invalid merge snapshot.");
            Map<String, String> managed = SnapshotCodec.decode(input.readNBytes(length));
            int count = input.readInt();
            if (count < 0 || count > managed.size()) throw new IOException("Invalid conflict count.");
            Set<String> conflicts = new TreeSet<>();
            for (int i = 0; i < count; i++) {
                String name = input.readUTF();
                if (!managed.containsKey(name) || !conflicts.add(name)) {
                    throw new IOException("Invalid conflict path.");
                }
            }
            if (input.read() != -1) throw new IOException("Unexpected merge-state data.");
            return new MergeState(ours, theirs, current, source, managed, List.copyOf(conflicts));
        }
    }

    void save(MergeState state) throws IOException {
        Path temporary = Files.createTempFile(path.getParent(), ".merge-", ".tmp");
        try {
            try (DataOutputStream output = new DataOutputStream(Files.newOutputStream(temporary))) {
                output.writeInt(0x43564D31);
                output.writeUTF(state.ours());
                output.writeUTF(state.theirs());
                output.writeUTF(state.currentBranch());
                output.writeUTF(state.sourceBranch());
                byte[] snapshot = SnapshotCodec.encode(state.managedPaths());
                output.writeInt(snapshot.length);
                output.write(snapshot);
                output.writeInt(state.conflicts().size());
                for (String conflict : state.conflicts()) output.writeUTF(conflict);
            }
            // Readers see either the previous complete state or the next complete state.
            try {
                Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    void resolve(Set<String> stagedPaths) throws IOException {
        MergeState state = load();
        if (state == null || Collections.disjoint(state.conflicts(), stagedPaths)) return;
        List<String> remaining = state.conflicts().stream()
                .filter(name -> !stagedPaths.contains(name)).toList();
        save(new MergeState(state.ours(), state.theirs(), state.currentBranch(),
                state.sourceBranch(), state.managedPaths(), remaining));
    }

    void clear() throws IOException {
        Files.deleteIfExists(path);
    }
}
