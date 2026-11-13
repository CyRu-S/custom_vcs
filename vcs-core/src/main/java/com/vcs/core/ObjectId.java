package com.vcs.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ObjectId {
    // No instance needed; call ObjectId.sha256 directly.
    private ObjectId() {
    }

    public static String sha256(byte[] data) {
        try {
            // Same bytes give the same SHA-256 hash.
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            // Turn 32 hash bytes into 64 hex characters.
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                "SHA-256 is unavailable.", exception
            );
        }
    }
}
