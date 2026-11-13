package com.vcs.customvcs.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vcs.core.Repository;
import com.vcs.customvcs.dto.FileContentResponse;
import com.vcs.customvcs.dto.FileEntryResponse;
import com.vcs.customvcs.dto.WriteFileRequest;

@Service
public class WorkingFileService {
    private static final int MAX_FILE_BYTES = 1_048_576;
    private static final int MAX_DIRECTORY_ENTRIES = 1000;
    private final RepositoryAccess access;

    public WorkingFileService(RepositoryAccess access) {
        this.access = access;
    }

    // List one directory at a time, keeping object storage out of the file browser.
    public List<FileEntryResponse> list(UUID id, String path) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            Path directory = resolve(repository, path, true);
            if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
                throw error(HttpStatus.NOT_FOUND, "Directory not found.");
            }
            List<FileEntryResponse> result = new ArrayList<>();
            try (var children = Files.newDirectoryStream(directory)) {
                for (Path child : children) {
                    if (directory.equals(repository.workingDirectory())
                            && child.getFileName().toString().equalsIgnoreCase(".customvcs")) {
                        continue;
                    }
                    String relative = relative(repository, child);
                    // Never follow linked files or Windows junctions while browsing.
                    Path checked = resolve(repository, relative, false);
                    boolean folder = Files.isDirectory(checked, LinkOption.NOFOLLOW_LINKS);
                    if (!folder && !Files.isRegularFile(checked, LinkOption.NOFOLLOW_LINKS)) {
                        throw error(HttpStatus.CONFLICT, "Unsupported working-file type.");
                    }
                    result.add(new FileEntryResponse(child.getFileName().toString(), relative,
                            folder, folder ? 0 : Files.size(checked)));
                    if (result.size() > MAX_DIRECTORY_ENTRIES) {
                        throw error(HttpStatus.PAYLOAD_TOO_LARGE,
                                "Directory exceeds the file browser limit of 1000 entries.");
                    }
                }
            }
            result.sort(Comparator.comparing(FileEntryResponse::directory).reversed()
                    .thenComparing(FileEntryResponse::name));
            return List.copyOf(result);
        }
    }

    public FileContentResponse read(UUID id, String path) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            Path file = resolve(repository, path, false);
            requireFile(file);
            // Bound the read itself, even if a file grows after its size was checked.
            byte[] bytes;
            try (var input = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS)) {
                bytes = input.readNBytes(MAX_FILE_BYTES + 1);
            }
            requireSize(bytes.length);
            try {
                String text = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(bytes)).toString();
                if (text.indexOf('\0') >= 0) {
                    throw error(HttpStatus.UNPROCESSABLE_CONTENT, "The editor supports UTF-8 text files only.");
                }
                return new FileContentResponse(relative(repository, file), text);
            } catch (CharacterCodingException exception) {
                throw error(HttpStatus.UNPROCESSABLE_CONTENT, "The editor supports UTF-8 text files only.");
            }
        }
    }

    // Replacing through a temporary file avoids modifying an inode shared through a hard link.
    public FileContentResponse write(UUID id, WriteFileRequest request) throws IOException {
        byte[] bytes = request.content().getBytes(StandardCharsets.UTF_8);
        requireSize(bytes.length);
        if (request.content().indexOf('\0') >= 0) {
            throw error(HttpStatus.UNPROCESSABLE_CONTENT, "The editor supports UTF-8 text files only.");
        }
        synchronized (access) {
            Repository repository = access.open(id);
            Path file = resolve(repository, request.path(), false);
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)
                    && !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw error(HttpStatus.CONFLICT, "A non-file path already exists at this location.");
            }
            Files.createDirectories(file.getParent());
            Path temporary = Files.createTempFile(file.getParent(), ".cvcs-edit-", ".tmp");
            try {
                Files.write(temporary, bytes);
                try {
                    Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException exception) {
                    Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            return new FileContentResponse(relative(repository, file), request.content());
        }
    }

    // Delete only one working file; staging its removal is a separate learning step.
    public void delete(UUID id, String path) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            Path file = resolve(repository, path, false);
            requireFile(file);
            Files.delete(file);
        }
    }

    private void requireFile(Path file) {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            throw error(HttpStatus.NOT_FOUND, "File not found.");
        }
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw error(HttpStatus.CONFLICT, "Requested path is not a regular file.");
        }
    }

    private void requireSize(int length) {
        if (length > MAX_FILE_BYTES) {
            throw error(HttpStatus.PAYLOAD_TOO_LARGE, "The editor supports files up to 1 MiB.");
        }
    }

    // Directory browsing alone may request the repository root.
    private Path resolve(Repository repository, String name, boolean allowRoot) throws IOException {
        if (allowRoot && "".equals(name)) {
            return repository.workingDirectory();
        }
        return access.resolvePath(repository, name);
    }

    private String relative(Repository repository, Path path) {
        return repository.workingDirectory().relativize(path).toString().replace('\\', '/');
    }

    private ResponseStatusException error(HttpStatus status, String detail) {
        return new ResponseStatusException(status, detail);
    }
}
