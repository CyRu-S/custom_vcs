package com.vcs.customvcs.controller;
import java.io.IOException;
import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;

import com.vcs.customvcs.dto.CreateRepositoryRequest;
import com.vcs.customvcs.dto.RenameRepositoryRequest;
import com.vcs.customvcs.dto.RepositoryPageResponse;
import com.vcs.customvcs.dto.RepositoryResponse;
import com.vcs.customvcs.service.RepositoryService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/repositories")
public class RepositoryController {
    private final RepositoryService repositoryService;

    public RepositoryController (RepositoryService repositoryService) {
        this.repositoryService = repositoryService;
    }

    // Validate the request before asking the service to create a repository.
    @PostMapping
    public ResponseEntity<RepositoryResponse> create (@Valid @RequestBody CreateRepositoryRequest request) throws IOException {
        RepositoryResponse response = repositoryService.create(request);

        // Return 201 with the new repository's API address in the Location header.
        URI location = URI.create("/api/repositories/" + response.id());

        return ResponseEntity.created(location).body(response);
    }

    // A found record becomes 200 with JSON; a missing ID becomes 404.
    @GetMapping("/{id}")
    public ResponseEntity<RepositoryResponse> findById(@PathVariable("id") UUID id) {
        return ResponseEntity.of(repositoryService.findById(id));
    }

    // No extra path: GET uses the class-level /api/repositories route.
    @GetMapping
    public RepositoryPageResponse list(
        @RequestParam(name = "page", defaultValue = "0") int page,
        @RequestParam(name = "size", defaultValue = "20") int size
    ) {
        // Bound database work and reject invalid pagination requests.
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be non-negative and size must be between 1 and 100."
            );
        }

        // Use the ID to give a consistent order when creation timestamps match.
        Sort sort = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("id")
        );

        Page<RepositoryResponse> result = repositoryService.list(PageRequest.of(page, size, sort));

        return new RepositoryPageResponse(
            result.getContent(), 
            result.getNumber(), 
            result.getSize(), 
            result.getTotalElements(), 
            result.getTotalPages()
        );
    }

    @PatchMapping("/{id}")
    public RepositoryResponse rename(@PathVariable("id") UUID id,
            @Valid @RequestBody RenameRepositoryRequest request) throws IOException {
        return repositoryService.rename(id, request);
    }

    // This explicit request removes the repository's working files, objects, and metadata.
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable("id") UUID id) throws IOException {
        repositoryService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
