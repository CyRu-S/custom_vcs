package com.vcs.customvcs.service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.vcs.core.CheckoutService;
import com.vcs.core.DiffService;
import com.vcs.core.MergeService;
import com.vcs.core.MergeStateStore;
import com.vcs.core.ReferenceStore;
import com.vcs.core.Repository;
import com.vcs.core.FileDiff;
import com.vcs.core.MergeResult;
import com.vcs.customvcs.dto.BranchResponse;
import com.vcs.customvcs.dto.MergeRequest;
import com.vcs.customvcs.dto.MergeStateResponse;

// Adapt existing engine operations; controllers never edit references or VCS objects directly.
@Service
public class RepositoryOperationsService {
    private final RepositoryAccess access;

    public RepositoryOperationsService(RepositoryAccess access) {
        this.access = access;
    }

    public List<BranchResponse> branches(UUID id) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            ReferenceStore refs = new ReferenceStore(repository);
            String current = refs.currentBranchName();
            List<BranchResponse> result = new ArrayList<>();
            for (String name : refs.listBranches()) {
                result.add(new BranchResponse(name, refs.branchCommitId(name), name.equals(current)));
            }
            // HEAD exists before the first branch pointer is written by a commit.
            if (result.stream().noneMatch(BranchResponse::current)) {
                result.add(new BranchResponse(current, refs.currentCommitId(), true));
            }
            result.sort(java.util.Comparator.comparing(BranchResponse::name));
            return List.copyOf(result);
        }
    }

    public BranchResponse createBranch(UUID id, String name) throws IOException {
        validateBranch(name);
        synchronized (access) {
            Repository repository = access.open(id);
            ReferenceStore refs = new ReferenceStore(repository);
            requireNoMerge(repository);
            if (refs.currentCommitId() == null) {
                throw conflict("Create an initial commit before creating a branch.");
            }
            if (existingBranch(refs, name) != null) {
                throw conflict("A branch with this name already exists.");
            }
            refs.createBranch(name);
            return new BranchResponse(name, refs.branchCommitId(name), false);
        }
    }

    public BranchResponse checkout(UUID id, String name) throws IOException {
        validateBranch(name);
        synchronized (access) {
            Repository repository = access.open(id);
            ReferenceStore refs = new ReferenceStore(repository);
            String target = requireBranch(refs, name);
            new CheckoutService(repository).checkout(target);
            return new BranchResponse(refs.currentBranchName(), refs.currentCommitId(), true);
        }
    }

    public void deleteBranch(UUID id, String name) throws IOException {
        validateBranch(name);
        synchronized (access) {
            var refs = new ReferenceStore(access.open(id));
            refs.deleteBranch(requireBranch(refs, name));
        }
    }

    public List<FileDiff> diff(UUID id, String scope) throws IOException {
        if (!"working".equals(scope) && !"staged".equals(scope)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Diff scope must be working or staged.");
        }
        synchronized (access) {
            Repository repository = access.open(id);
            DiffService diff = new DiffService(repository);
            if ("staged".equals(scope)) {
                return diff.staged();
            }
            return diff.workingTree();
        }
    }

    public MergeResult merge(UUID id, MergeRequest request) throws IOException {
        validateBranch(request.sourceBranch());
        if (request.message() != null && request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Merge message cannot be blank.");
        }
        synchronized (access) {
            Repository repository = access.open(id);
            ReferenceStore refs = new ReferenceStore(repository);
            String source = requireBranch(refs, request.sourceBranch());
            return new MergeService(repository).merge(source, access.user().name(), request.message());
        }
    }

    public MergeStateResponse mergeState(UUID id) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            var state = new MergeStateStore(repository).load();
            if (state == null) {
                return new MergeStateResponse(false, null, null, null, null, List.of());
            }
            return new MergeStateResponse(true, state.currentBranch(), state.sourceBranch(),
                    state.ours(), state.theirs(), state.conflicts());
        }
    }

    public void abortMerge(UUID id) throws IOException {
        synchronized (access) {
            Repository repository = access.open(id);
            // The engine checks whether abort is possible and restores the original files.
            new MergeService(repository).abort();
        }
    }

    private void requireNoMerge(Repository repository) throws IOException {
        if (new MergeStateStore(repository).load() != null) {
            throw conflict("Finish or abort the current merge first.");
        }
    }

    private String requireBranch(ReferenceStore refs, String name) throws IOException {
        String existing = existingBranch(refs, name);
        if (existing == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        return existing;
    }

    private String existingBranch(ReferenceStore refs, String name) throws IOException {
        // Path equality respects the host filesystem's case rules, including Windows.
        for (String branch : refs.listBranches()) {
            if (Path.of(branch).equals(Path.of(name))) {
                return branch;
            }
        }
        return null;
    }

    private void validateBranch(String name) {
        if (name == null || name.length() > 100 || !name.matches("[A-Za-z0-9][A-Za-z0-9._-]*")
                || name.endsWith(".")
                || name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\\..*)?")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid branch name.");
        }
    }

    private ResponseStatusException conflict(String detail) {
        return new ResponseStatusException(HttpStatus.CONFLICT, detail);
    }
}
