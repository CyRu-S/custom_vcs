package com.vcs.customvcs.service;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import com.vcs.core.*;

// Collect an existing repository into one React response; VCS commands live in RepositoryVcsService.
@Service
public class WorkspaceService {
    private static final int HISTORY_LIMIT = 200;
    private static final int FILE_LIMIT = 2000;
    private static final int BYTE_LIMIT = 8 * 1024 * 1024;
    private final RepositoryAccess access;
    private final RepositoryService metadata;

    public WorkspaceService(RepositoryAccess access, RepositoryService metadata) {
        this.access = access; this.metadata = metadata;
    }

    public record WebCommit(String id, String message, String date, String author,
            List<String> parents, Map<String, String> files, String branch) {}
    public record Change(String path, String before, String after, boolean staged, boolean binary) {}
    public record Conflict(String path, String ours, String theirs, boolean resolved, boolean binary) {}
    public record Merge(String source, String tip, Map<String, String> files, List<Conflict> conflicts) {}
    public record View(String id, String name, String description, String language, String color,
            String current, Map<String, String> branches, List<WebCommit> commits,
            Map<String, String> files, List<Change> changes, Merge merge, List<String> unreadableFiles,
            int historyLimit) {}

    public View read(UUID id) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            var info = metadata.findById(id).orElseThrow();
            ReferenceStore refs = new ReferenceStore(repository);
            CommitService commits = new CommitService(repository);
            ObjectStore objects = new ObjectStore(repository);
            String current = refs.currentBranchName();
            Map<String, String> branches = new TreeMap<>();
            for (String name : refs.listBranches()) branches.put(name, refs.branchCommitId(name));
            branches.putIfAbsent(current, "");

            List<WebCommit> history = history(branches, commits, current);
            StagingArea staging = new StagingArea(repository);
            staging.load();
            Map<String, String> index = staging.entries();
            Map<String, String> head = refs.currentCommitId() == null ? Map.of()
                    : commits.readSnapshot(commits.readCommit(refs.currentCommitId()).snapshotId());

            List<String> unreadable = new ArrayList<>();
            Map<String, String> files = workingFiles(repository, unreadable);
            StatusService status = new StatusService(repository);
            var staged = status.inspectStaged();
            var working = status.inspect();
            List<Change> changes = new ArrayList<>();
            Set<String> stagedPaths = new TreeSet<>(staged.added());
            stagedPaths.addAll(staged.modified()); stagedPaths.addAll(staged.deleted());
            for (String path : stagedPaths) {
                String before = blob(objects, head.get(path));
                String after = blob(objects, index.get(path));
                changes.add(new Change(path, before, after, true,
                        (head.containsKey(path) && before == null) || (index.containsKey(path) && after == null)));
            }
            Set<String> workingPaths = new TreeSet<>(working.modified());
            workingPaths.addAll(working.deleted()); workingPaths.addAll(working.untracked());
            for (String path : workingPaths) {
                String before = blob(objects, index.get(path));
                changes.add(new Change(path, before, files.get(path), false,
                        (index.containsKey(path) && before == null) || unreadable.contains(path)));
            }

            Merge merge = null;
            MergeState state = new MergeStateStore(repository).load();
            if (state != null) {
                Map<String, String> ours = commits.readSnapshot(commits.readCommit(state.ours()).snapshotId());
                Map<String, String> theirs = commits.readSnapshot(commits.readCommit(state.theirs()).snapshotId());
                List<Conflict> conflicts = new ArrayList<>();
                for (String path : state.conflicts()) {
                    String left = blob(objects, ours.get(path)), right = blob(objects, theirs.get(path));
                    conflicts.add(new Conflict(path, left, right, false,
                            (ours.containsKey(path) && left == null) || (theirs.containsKey(path) && right == null)));
                }
                merge = new Merge(state.sourceBranch(), state.theirs(), files, conflicts);
            }
            return new View(id.toString(), info.name(), info.description(), "CustomVCS", "blue",
                    current, branches, history, files, changes, merge, unreadable, HISTORY_LIMIT);
        }
    }

    // Traverse both parents and branch tips; cap the browser view without deleting any history.
    private List<WebCommit> history(Map<String, String> branches, CommitService commits, String current) throws IOException {
        ArrayDeque<String> pending = new ArrayDeque<>();
        for (String id : branches.values()) if (!id.isEmpty()) pending.add(id);
        Set<String> seen = new HashSet<>();
        List<WebCommit> result = new ArrayList<>();
        while (!pending.isEmpty() && result.size() < HISTORY_LIMIT) {
            String id = pending.removeFirst();
            if (!seen.add(id)) continue;
            Commit commit = commits.readCommit(id);
            List<String> parents = new ArrayList<>();
            if (commit.parentId() != null) { parents.add(commit.parentId()); pending.add(commit.parentId()); }
            if (commit.secondParentId() != null) { parents.add(commit.secondParentId()); pending.add(commit.secondParentId()); }
            String branch = branches.entrySet().stream().filter(e -> e.getValue().equals(id))
                    .map(Map.Entry::getKey).findFirst().orElse(current);
            result.add(new WebCommit(id, commit.message(), commit.timestamp().toString(), commit.author(),
                    parents, Map.of(), branch));
        }
        return result;
    }

    private Map<String, String> workingFiles(Repository repository, List<String> unreadable) throws IOException {
        Map<String, String> result = new TreeMap<>();
        int[] totals = {0, 0};
        // Prune metadata before traversal; object files are never browser source files.
        Files.walkFileTree(repository.workingDirectory(), new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (directory.equals(repository.metadataDirectory())) return FileVisitResult.SKIP_SUBTREE;
                if (!directory.equals(repository.workingDirectory())) {
                    String name = repository.workingDirectory().relativize(directory).toString().replace('\\', '/');
                    access.resolvePath(repository, name);
                }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path path, BasicFileAttributes attributes) throws IOException {
                if (++totals[0] > FILE_LIMIT) throw tooLarge();
                String name = repository.workingDirectory().relativize(path).toString().replace('\\', '/');
                if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                    unreadable.add(name); return FileVisitResult.CONTINUE;
                }
                access.resolvePath(repository, name);
                byte[] content;
                try (var input = Files.newInputStream(path, LinkOption.NOFOLLOW_LINKS)) {
                    content = input.readNBytes(1_048_577);
                }
                totals[1] += content.length;
                if (totals[1] > BYTE_LIMIT) throw tooLarge();
                String text = content.length > 1_048_576 ? null : text(content);
                if (text == null) unreadable.add(name); else result.put(name, text);
                return FileVisitResult.CONTINUE;
            }
        });
        return result;
    }

    // Load immutable historical bytes on demand instead of returning every snapshot with each refresh.
    public Map<String, String> snapshot(UUID id, String commitId) throws IOException {
        if (!commitId.matches("[0-9a-f]{64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid commit ID.");
        }
        synchronized (access) {
            Repository repository = access.open(id);
            CommitService commits = new CommitService(repository);
            Map<String, String> snapshot = commits.readSnapshot(commits.readCommit(commitId).snapshotId());
            Map<String, String> result = new TreeMap<>();
            ObjectStore objects = new ObjectStore(repository);
            int bytes = 0;
            for (var entry : snapshot.entrySet()) {
                String content = blob(objects, entry.getValue());
                if (content != null) {
                    bytes += content.getBytes(StandardCharsets.UTF_8).length;
                    if (bytes > BYTE_LIMIT || result.size() >= FILE_LIMIT) throw tooLarge();
                    result.put(entry.getKey(), content);
                }
            }
            return result;
        }
    }

    private String blob(ObjectStore store, String id) throws IOException {
        if (id == null) return null;
        byte[] bytes = store.readBlob(id);
        return bytes.length > 1_048_576 ? null : text(bytes);
    }
    private String text(byte[] bytes) {
        // Binary and malformed UTF-8 data must not masquerade as an editable text file.
        try {
            String value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return value.indexOf('\0') < 0 ? value : null;
        } catch (CharacterCodingException failure) { return null; }
    }
    private ResponseStatusException tooLarge() {
        return new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                "Repository exceeds the browser view limit (2000 files / 8 MiB). Use the CLI for larger repositories.");
    }
}
