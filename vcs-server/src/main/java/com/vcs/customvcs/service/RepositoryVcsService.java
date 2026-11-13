package com.vcs.customvcs.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.vcs.core.CommitEntry;
import com.vcs.core.CommitService;
import com.vcs.core.MergeStateStore;
import com.vcs.core.ReferenceStore;
import com.vcs.core.Repository;
import com.vcs.core.RepositoryStateException;
import com.vcs.core.StagingArea;
import com.vcs.core.StatusService;
import com.vcs.customvcs.dto.RepositoryStatusResponse;

// Translate the basic web operations into engine calls; no HTTP or SQL logic belongs in the engine.
@Service
public class RepositoryVcsService {
    private final RepositoryAccess access;

    public RepositoryVcsService(RepositoryAccess access) {
        this.access = access;
    }

    // Every operation checks ownership through access.open and waits for other server file operations.
    public RepositoryStatusResponse status(UUID id) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            StatusService status = new StatusService(repository);
            ReferenceStore references = new ReferenceStore(repository);

            return new RepositoryStatusResponse(
                    references.currentBranchName(), references.currentCommitId(),
                    status.inspectStaged(), status.inspect());
        }
    }

    // Save the file's current bytes in the index; edits made afterward remain unstaged.
    public void stage(UUID id, String path) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            Path file = access.resolvePath(repository, path);
            StagingArea staging = new StagingArea(repository);
            staging.load();
            staging.stagePath(repository.workingDirectory().relativize(file));
            staging.save();
        }
    }

    // Put one index entry back to its committed version, without changing the working file.
    public void unstage(UUID id, String path) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            access.resolvePath(repository, path);
            if (new MergeStateStore(repository).load() != null) {
                throw new RepositoryStateException("Finish or abort the merge before unstaging.");
            }

            StagingArea staging = new StagingArea(repository);
            staging.load();
            Map<String, String> entries = new TreeMap<>(staging.entries());
            String headId = new ReferenceStore(repository).currentCommitId();
            String committedBlob = null;

            // Before the first commit there is no saved version to restore.
            if (headId != null) {
                CommitService commits = new CommitService(repository);
                String snapshotId = commits.readCommit(headId).snapshotId();
                committedBlob = commits.readSnapshot(snapshotId).get(path);
            }

            if (committedBlob == null) {
                entries.remove(path);
            } else {
                entries.put(path, committedBlob);
            }
            staging.replaceEntries(entries);
            staging.save();
        }
    }

    // The engine saves the index as a snapshot and advances the current branch.
    public CommitEntry commit(UUID id, String message) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            CommitService commits = new CommitService(repository);
            String commitId = commits.createCommit(access.user().name(), message);
            return new CommitEntry(commitId, commits.readCommit(commitId));
        }
    }

    // Read saved commits from the current branch, including both sides of completed merges.
    public List<CommitEntry> history(UUID id) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            return new CommitService(repository).history();
        }
    }
}
