package com.vcs.customvcs.service;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.LinkOption;
import java.nio.file.FileVisitResult;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.vcs.core.Repository;
import com.vcs.customvcs.config.StorageProperties;


// Connect the Spring application to filesystem storage and the pure Java engine.
@Service 
public class RepositoryStorage {
    private final Path root;

    public RepositoryStorage(StorageProperties properties) {
        // Resolve the configured root once, when Spring creates the service.
        this.root = properties.root().toAbsolutePath().normalize();
    }

    public void initialize(UUID id) throws IOException {
        Files.createDirectories(root);

        // Use the repository ID as its folder name; reject an existing directory.
        Path directory = root.resolve(id.toString());
        Files.createDirectory(directory);

        // Let vcs-core create .customvcs and its internal structure.
        new Repository(directory).initialize();
    }

    // Resolve only server-generated IDs; clients never choose an absolute storage path.
    public Repository open(UUID id) throws IOException {
        Path directory = checkedDirectory(id);
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                || !Files.isDirectory(directory.resolve(".customvcs"), LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(directory.resolve(".customvcs"))) {
            throw new IOException("Repository storage is missing or invalid.");
        }
        Path metadata = directory.resolve(".customvcs");
        if (!metadata.toRealPath().equals(metadata)) {
            throw new IOException("Repository metadata is redirected.");
        }
        return new Repository(directory);
    }

    private Path checkedDirectory(UUID id) throws IOException {
        Path directory = root.toRealPath().resolve(id.toString());
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)
                && (Files.isSymbolicLink(directory) || !directory.toRealPath().equals(directory))) {
            throw new IOException("Repository storage is redirected.");
        }
        return directory;
    }

    // A failed deletion can be retried; never follow links into another directory.
    public void delete(UUID id) throws IOException {
        Path directory = checkedDirectory(id);
        if (Files.notExists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Files.walkFileTree(directory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attributes) throws IOException {
                // Reject directory redirects, including Windows junctions, before traversal.
                if (Files.isSymbolicLink(path) || !path.toRealPath().equals(path)) {
                    throw new IOException("Repository contains a redirected directory.");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(path);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}
