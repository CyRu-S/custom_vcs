package com.vcs.customvcs.controller;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.vcs.customvcs.dto.BranchRequest;
import com.vcs.customvcs.dto.BranchResponse;
import com.vcs.core.FileDiff;
import com.vcs.customvcs.dto.MergeRequest;
import com.vcs.core.MergeResult;
import com.vcs.customvcs.dto.MergeStateResponse;
import com.vcs.customvcs.service.RepositoryOperationsService;

import jakarta.validation.Valid;

// Branches, checkout, diff and merge; everyday commands live in RepositoryVcsController.
@RestController
@RequestMapping("/api/repositories/{id}")
public class RepositoryOperationsController {
    private final RepositoryOperationsService operations;

    public RepositoryOperationsController(RepositoryOperationsService operations) {
        this.operations = operations;
    }

    @GetMapping("/branches")
    public List<BranchResponse> branches(@PathVariable("id") UUID id) throws IOException {
        return operations.branches(id);
    }

    @PostMapping("/branches")
    public ResponseEntity<BranchResponse> createBranch(@PathVariable("id") UUID id,
            @Valid @RequestBody BranchRequest request) throws IOException {
        return ResponseEntity.status(201).body(operations.createBranch(id, request.name()));
    }

    @PostMapping("/checkout")
    public BranchResponse checkout(@PathVariable("id") UUID id,
            @Valid @RequestBody BranchRequest request) throws IOException {
        return operations.checkout(id, request.name());
    }

    @DeleteMapping("/branches")
    public ResponseEntity<Void> deleteBranch(@PathVariable("id") UUID id,
            @RequestParam("name") String name) throws IOException {
        operations.deleteBranch(id, name);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/diff")
    public List<FileDiff> diff(@PathVariable("id") UUID id,
            @RequestParam(name = "scope", defaultValue = "working") String scope) throws IOException {
        return operations.diff(id, scope);
    }

    @PostMapping("/merge")
    public MergeResult merge(@PathVariable("id") UUID id,
            @Valid @RequestBody MergeRequest request) throws IOException {
        return operations.merge(id, request);
    }

    @GetMapping("/merge")
    public MergeStateResponse mergeState(@PathVariable("id") UUID id) throws IOException {
        return operations.mergeState(id);
    }

    @PostMapping("/merge/abort")
    public ResponseEntity<Void> abortMerge(@PathVariable("id") UUID id) throws IOException {
        operations.abortMerge(id);
        return ResponseEntity.noContent().build();
    }
}
