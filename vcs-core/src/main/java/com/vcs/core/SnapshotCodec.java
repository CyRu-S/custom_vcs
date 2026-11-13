package com.vcs.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public final class SnapshotCodec {
    private SnapshotCodec() {
        
    }

    public static byte[] encode(Map<String, String> entries) throws IOException {
        // Sort paths so insertion order cannot change the snapshot ID.
        Map<String, String> sorted = new TreeMap<>(entries);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        // Write binary fields into memory instead of a file.
        try (DataOutputStream output = new DataOutputStream(buffer)) {
            output.writeInt(1); // Snapshot format version.
            output.writeInt(sorted.size());

            for (Map.Entry<String, String> entry : sorted.entrySet()) {
                output.writeUTF(entry.getKey());
                output.writeUTF(entry.getValue());
            }
        }

        return buffer.toByteArray();
    }

    public static Map<String, String> decode(byte[] content) throws IOException {
        Map<String, String> entries = new TreeMap<>();

        // Read binary fields from the supplied bytes, not from a file.
        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(content))) {
            if (input.readInt() != 1) {
                throw new IOException("Unsupported snapshot version.");
            }

            int count = input.readInt();
            if (count < 0) {
                throw new IOException("Invalid snapshot entry count.");
            }

            for (int i = 0; i<count; i++) {
                String path = input.readUTF();
                String objectId = input.readUTF();

                if (!objectId.matches("[0-9a-f]{64}")) {
                    throw new IOException("Invalid blob in snapshot.");
                }

                if (entries.put(path, objectId) != null) {
                    throw new IOException("Duplicate path in snapshot.");
                }
            }

            // After the declared entries, no bytes should remain.
            if (input.read() != -1) {
                throw new IOException("Unexpected data after snapshot entries.");
            }
        }

        return Collections.unmodifiableMap(entries);
    }
}
