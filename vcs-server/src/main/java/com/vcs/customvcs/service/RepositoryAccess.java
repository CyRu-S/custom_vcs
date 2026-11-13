package com.vcs.customvcs.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vcs.core.Repository;
import com.vcs.customvcs.entity.RepositoryStatus;
import com.vcs.customvcs.repository.RepositoryMetadataRepository;
import com.vcs.customvcs.security.CurrentUser;
import com.vcs.customvcs.security.SessionUser;

// Look up a repository and return its engine object.
@Service
public class RepositoryAccess {
    private final RepositoryMetadataRepository metadata;
    private final RepositoryStorage storage;
    private final CurrentUser current;

    public RepositoryAccess(RepositoryMetadataRepository metadata, RepositoryStorage storage, CurrentUser current) {
        this.metadata = metadata;
        this.storage = storage;
        this.current = current;
    }

    public SessionUser user() { return current.get(); }

    // Services use synchronized(access) to keep file operations from overlapping.
    public Repository open(UUID id) throws IOException {
        // Return 404 for another user's ID as well, so ownership is never inferred from guessing IDs.
        var entity = metadata.findByIdAndOwnerId(id, user().id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found."));
        if (entity.getStatus() != RepositoryStatus.READY) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Repository is not ready.");
        }
        return storage.open(id);
    }

    // Accept canonical slash-separated paths, never absolute paths, traversal, or metadata.
    public Path resolvePath(Repository repository, String name) throws IOException {
        if (name == null || name.isBlank() || name.length() > 1024
                || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid repository-relative path.");
        }
        for (String component : name.split("/", -1)) {
            // Trailing dots/spaces and device names have special meanings on Windows.
            if (component.isEmpty() || component.equals(".") || component.equals("..")
                    || component.endsWith(".") || component.endsWith(" ")
                    || component.equalsIgnoreCase(".customvcs")
                    || component.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")
                    || component.chars().anyMatch(c -> c < 32 || "<>\"|?*".indexOf(c) >= 0)) {
                throw error(HttpStatus.BAD_REQUEST, "Invalid repository-relative path.");
            }
        }
        try {
            Path root = repository.workingDirectory();
            Path relative = Path.of(name);
            Path target = root.resolve(relative).normalize();
            if (relative.isAbsolute() || !target.startsWith(root) || target.equals(root)) {
                throw error(HttpStatus.BAD_REQUEST, "Invalid repository-relative path.");
            }
            for (Path cursor = target; !cursor.equals(root); cursor = cursor.getParent()) {
                if (Files.exists(cursor, LinkOption.NOFOLLOW_LINKS)) {
                    if (Files.isSymbolicLink(cursor) || !cursor.toRealPath().equals(cursor)) {
                        throw error(HttpStatus.BAD_REQUEST, "Linked working paths are not supported.");
                    }
                    if (!cursor.equals(target) && !Files.isDirectory(cursor, LinkOption.NOFOLLOW_LINKS)) {
                        throw error(HttpStatus.CONFLICT, "A parent path is not a directory.");
                    }
                }
            }
            return target;
        } catch (InvalidPathException exception) {
            throw error(HttpStatus.BAD_REQUEST, "Invalid repository-relative path.");
        }
    }

    private ResponseStatusException error(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }
}
