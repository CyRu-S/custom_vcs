package com.vcs.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Stream;

public final class ReferenceStore {
    private final Repository repository;

    public ReferenceStore(Repository repository) {
        this.repository = repository;
    }

    private Path currentBranchPath() throws IOException {
        // HEAD selects a branch; the branch file stores its latest commit ID.
        String head = Files.readString(
            repository.metadataDirectory().resolve("HEAD"), 
            StandardCharsets.UTF_8).strip();

        String prefix = "ref: refs/heads/";
        if (!head.startsWith(prefix)) {
            throw new IOException("Unsupported HEAD format.");
        }

        String branch = head.substring(prefix.length());

        return branchPath(branch);
    }

    public void createBranch(String name) throws IOException {
        Path destination = branchPath(name);
        String commitId = currentCommitId();

        if (commitId == null) {
            throw new IOException("Create a commit before creating a branch.");
        }

        // New branches share the current commit; never overwrite an existing pointer.
        Files.writeString(destination, commitId + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }

    public String currentBranchName() throws IOException {
        // HEAD identifies the selected branch, even before its first commit.
        return currentBranchPath().getFileName().toString();
    }

    public List<String> listBranches() throws IOException {
        Path headsDirectory = repository.metadataDirectory()
                    .resolve("refs")
                    .resolve("heads");

        // Close the directory handle after collecting the reference names.
        try (Stream<Path> paths = Files.list(headsDirectory)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .toList();
        }
    }

    public String currentCommitId() throws IOException {
        Path branchPath = currentBranchPath();

        // Before the first commit, the branch file does not exist yet.
        if (Files.notExists(branchPath)) {
            return null;
        }

        return readCommitId(branchPath);
    }

    private String readCommitId(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Missing or invalid branch reference: " + path.getFileName());
        }
        String commitId = Files.readString(path, StandardCharsets.UTF_8).strip();

        if (!commitId.matches("[0-9a-f]{64}")) {
            throw new IOException("Invalid commit ID in branch reference.");
        }

        return commitId;
    }

    public String branchCommitId(String name) throws IOException {
        return readCommitId(branchPath(name));
    }

    // Delete only merged, non-current branches; commits and their objects are retained.
    public void deleteBranch(String name) throws IOException {
        Path target = branchPath(name);
        if (target.equals(currentBranchPath())) {
            throw new RepositoryStateException("Cannot delete the current branch.");
        }
        if (new MergeStateStore(repository).load() != null) {
            throw new RepositoryStateException("Finish or abort the merge before deleting a branch.");
        }
        String targetId = readCommitId(target);
        String currentId = currentCommitId();
        if (currentId == null || !new CommitGraph(new CommitService(repository)).ancestors(currentId).contains(targetId)) {
            throw new RepositoryStateException("Merge this branch before deleting it.");
        }
        Files.delete(target);
    }

    public void selectBranch(String name) throws IOException {
        // Checkout calls this after restoring the target files and index.
        branchCommitId(name);

        String storedName = branchPath(name).toRealPath().getFileName().toString();
        Files.writeString(repository.metadataDirectory().resolve("HEAD"), 
                        "ref: refs/heads/" + storedName + "\n", 
                        StandardCharsets.UTF_8);
    }

    public void updateCurrentBranch(String commitId) throws IOException {
        if (commitId == null || !commitId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Invalid commit ID.");
        }

        // Move the current branch to this commit without changing HEAD.
        Files.writeString(currentBranchPath(), commitId + "\n", StandardCharsets.UTF_8);
    }

    private Path branchPath(String branch) throws IOException {
        // Keep branch names within a single file beneath refs/heads.
        if (branch == null || !branch.matches("[A-Za-z0-9][A-Za-z0-9._-]*")) {
            throw new IOException("Invalid branch name.");
        }

        return repository.metadataDirectory()
                .resolve("refs")
                .resolve("heads")
                .resolve(branch);
    }
}
