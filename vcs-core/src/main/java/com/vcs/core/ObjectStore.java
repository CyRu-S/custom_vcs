package com.vcs.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

public final class ObjectStore {
    private final Path objectDirectory;

    public ObjectStore(Repository repository) {
        this.objectDirectory = repository.metadataDirectory().resolve("objects");
    }

    private byte[] encodeObject(String type, byte[] content) {
        if (type == null || !type.matches("[a-z]+")) {
            throw new IllegalArgumentException("Invalid object type.");
        }

        // Header: object type + byte count + zero byte.
        byte[] header = (type + " " + content.length + "\0")
                        .getBytes(StandardCharsets.UTF_8);
        
        // Join the header and file bytes in memory.
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        buffer.writeBytes(header);
        buffer.writeBytes(content);

        return buffer.toByteArray();
    }

    public String writeObject(String type, byte[] content) throws IOException {
        
        // Run init before storing objects.
        if (!Files.isDirectory(objectDirectory)) {
            throw new IOException("Repository object directory is missing.");
        }

        byte[] encoded = encodeObject(type, content);
        // Hash the header and content together.
        String objectId = ObjectId.sha256(encoded);

        // First 2 hash characters name the folder; the rest name the file.
        Path prefixDirectory = objectDirectory.resolve(objectId.substring(0, 2));
        Files.createDirectories(prefixDirectory);

        Path objectPath = prefixDirectory.resolve(objectId.substring(2));

        // Reuse existing objects instead of overwriting them.
        if (!Files.exists(objectPath)) {
            Files.write(objectPath, encoded, StandardOpenOption.CREATE_NEW);
        }

        return objectId;
    }

    public String writeBlob(byte[] content) throws IOException {
        // Use the shared writer with the blob type.
        return writeObject("blob", content);
    }

    public byte[] readObject(String objectId) throws IOException {
        // Check the ID before using it as a file path.
        if (objectId == null || !objectId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(
                "Object ID must be 64 lowercase hexadecimal characters."
            );
        } 

        Path objectPath = objectDirectory.resolve(objectId.substring(0, 2))
                                        .resolve(objectId.substring(2));

        byte[] encoded = Files.readAllBytes(objectPath);

        // Rehash the stored bytes to detect changed content.
        if (!ObjectId.sha256(encoded).equals(objectId)) {
            throw new IOException ("Object content does not match its ID.");
        }

        // These bytes still include the object header.
        return encoded;
    }

    public byte[] readContent(String expectedType, String objectId) throws IOException {
        byte[] encoded = readObject(objectId);

        // The first zero byte marks the end of the header.
        int headerEnd = -1;
        for (int i = 0; i<encoded.length; i++) {
            if (encoded[i] == 0) {
                headerEnd = i;
                break;
            }
        }

        if (headerEnd == -1) {
            throw new IOException("Object header separator is missing.");
        }

        String header = new String(encoded, 0, headerEnd, StandardCharsets.UTF_8);
        
        // Check the type and byte count against the actual content.
        int contentLength = encoded.length - headerEnd - 1;

        if (!header.equals(expectedType + " " + contentLength)) {
            throw new IOException("Invalid object type or content length.");
        }

        // Return the object payload without its header.
        return Arrays.copyOfRange(encoded, headerEnd + 1, encoded.length); 
    }

    public byte[] readBlob(String objectId) throws IOException {
        // Use the shared reader with the blob type.
        return readContent("blob", objectId);
    }

    public String hashBlob(byte[] content) {
        // Calculate the blob ID without writing an object.
        return ObjectId.sha256(encodeObject("blob", content));
    }
}
