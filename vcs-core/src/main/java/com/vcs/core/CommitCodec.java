package com.vcs.core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;

public final class CommitCodec {
    private CommitCodec() {

    }

    public static byte[] encode(Commit commit) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        try (DataOutputStream output = new DataOutputStream(buffer)) {
            // Ordinary commits keep their original format and object IDs.
            output.writeInt(commit.secondParentId() == null ? 1 : 2);
            output.writeUTF(commit.snapshotId());

            // The first commit has no parent ID to write.
            boolean hasParent = commit.parentId() != null;
            output.writeBoolean(hasParent);

            if (hasParent) {
                output.writeUTF(commit.parentId());
            }
            if (commit.secondParentId() != null) {
                if (!hasParent) throw new IOException("Merge requires two parents.");
                output.writeUTF(commit.secondParentId());
            }

            output.writeUTF(commit.author());

            // getEpochSecond gives whole seconds; getNano gives the fractional part.
            output.writeLong(commit.timestamp().getEpochSecond());
            output.writeInt(commit.timestamp().getNano());

            output.writeUTF(commit.message());
        }

        return buffer.toByteArray();
    }

    public static Commit decode(byte[] content) throws IOException {

        try (DataInputStream input = new DataInputStream(new ByteArrayInputStream(content))) {
            int version = input.readInt();
            if (version != 1 && version != 2) {
                throw new IOException("Unsupported commit version.");
            }

            String snapshotId = input.readUTF();

            // Read a parent ID only when the saved flag is true.
            boolean hasParent = input.readBoolean();
            String parentId = hasParent ? input.readUTF() : null;
            String secondParentId = version == 2 ? input.readUTF() : null;
            if (version == 2 && (parentId == null || !secondParentId.matches("[0-9a-f]{64}"))) {
                throw new IOException("Invalid merge parents.");
            }

            String author = input.readUTF();
            long seconds = input.readLong();
            int nanos = input.readInt();
            String message = input.readUTF();

            if (!snapshotId.matches("[0-9a-f]{64}") || (parentId != null && !parentId.matches("[0-9a-f]{64}"))) {
                throw new IOException("Invalid object ID in commit.");
            }

            // Nanoseconds must fit within one second.
            if (nanos < 0 || nanos >= 1_000_000_000) {
                throw new IOException("Invalid commit nanoseconds.");
            }

            if (input.read() != -1) {
                throw new IOException("Unexpected data after commit fields.");
            }

            // Rebuild the Instant from its seconds and fractional nanoseconds.
            Instant timestamp = Instant.ofEpochSecond(seconds, nanos);
            return new Commit(snapshotId, parentId, author, timestamp, message, secondParentId);
        } catch (DateTimeException exception) {
            throw new IOException("Invalid commit timestamp.", exception);
        } 
    }
}
