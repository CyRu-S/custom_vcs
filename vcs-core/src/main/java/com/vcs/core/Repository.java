package com.vcs.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

public class Repository {
    private final Path workingDirectory;

    public Repository(Path workingDirectory) {
        // Keep one absolute path for this repository.
        this.workingDirectory = Objects.requireNonNull(workingDirectory)
                                        .toAbsolutePath()
                                        .normalize();
    }

    public Path workingDirectory() {
        return workingDirectory;
    }

    // Our VCS data lives inside .customvcs.
    public Path metadataDirectory() {
        return workingDirectory.resolve(".customvcs");
    }

    public void initialize() throws IOException {
        // Create storage folders; existing folders stay as they are.
        Files.createDirectories(metadataDirectory().resolve("objects"));
        Files.createDirectories(metadataDirectory().resolve("refs").resolve("heads"));

        Path headPath = metadataDirectory().resolve("HEAD");

        // Keep the current branch when init runs again.
        if (!Files.exists(headPath)) {
            // A new repository starts on main.
            Files.writeString(
                headPath, 
                "ref: refs/heads/main\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW
            );
        }
    }
}
