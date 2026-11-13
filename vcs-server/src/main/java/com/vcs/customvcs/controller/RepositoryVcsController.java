package com.vcs.customvcs.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.vcs.core.CommitEntry;
import com.vcs.customvcs.dto.CreateCommitRequest;
import com.vcs.customvcs.dto.RepositoryStatusResponse;
import com.vcs.customvcs.dto.StageFileRequest;
import com.vcs.customvcs.service.RepositoryVcsService;

import jakarta.validation.Valid;

// The everyday VCS workflow is here: check status, stage a file, commit, and read history.
@RestController
@RequestMapping("/api/repositories/{id}")
public class RepositoryVcsController {
    private final RepositoryVcsService vcs;

    public RepositoryVcsController(RepositoryVcsService vcs) {
        this.vcs = vcs;
    }

    @GetMapping("/status")
    public RepositoryStatusResponse status(@PathVariable("id") UUID id) throws IOException {
        return vcs.status(id);
    }

    // Staging changes the saved index, so success needs no response body.
    @PostMapping("/stage")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stage(@PathVariable("id") UUID id,
            @Valid @RequestBody StageFileRequest request) throws IOException {
        vcs.stage(id, request.path());
    }

    @PostMapping("/stage/undo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unstage(@PathVariable("id") UUID id,
            @Valid @RequestBody StageFileRequest request) throws IOException {
        vcs.unstage(id, request.path());
    }

    @PostMapping("/commits")
    @ResponseStatus(HttpStatus.CREATED)
    public CommitEntry commit(@PathVariable("id") UUID id,
            @Valid @RequestBody CreateCommitRequest request) throws IOException {
        return vcs.commit(id, request.message());
    }

    @GetMapping("/commits")
    public List<CommitEntry> history(@PathVariable("id") UUID id) throws IOException {
        return vcs.history(id);
    }
}
