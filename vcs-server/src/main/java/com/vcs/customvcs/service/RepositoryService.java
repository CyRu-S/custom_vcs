package com.vcs.customvcs.service;

import java.time.Instant;
import java.util.UUID;
import java.io.IOException;
import com.vcs.customvcs.dto.RepositoryResponse;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.vcs.customvcs.dto.RenameRepositoryRequest;

import com.vcs.customvcs.dto.CreateRepositoryRequest;
import com.vcs.customvcs.entity.RepositoryEntity;
import com.vcs.customvcs.entity.RepositoryStatus;
import com.vcs.customvcs.repository.RepositoryMetadataRepository;

// Coordinate database metadata with filesystem storage.
@Service 
public class RepositoryService {
    private final RepositoryMetadataRepository metadataRepository;
    private final RepositoryStorage storage;
    private final RepositoryAccess access;

    public RepositoryService(
        RepositoryMetadataRepository metadataRepository,
        RepositoryStorage storage,
        RepositoryAccess access
    ) {
        this.metadataRepository = metadataRepository;
        this.storage = storage;
        this.access = access;
    }

    // Record the creation attempt before allocating repository storage.
    private RepositoryEntity createMetadata(String name) {
        RepositoryEntity repository = RepositoryEntity.builder()
                                            .id(UUID.randomUUID())
                                            .name(name)
                                            .ownerId(access.user().id())
                                            .createdAt(Instant.now())
                                            .status(RepositoryStatus.INITIALIZING)
                                            .build();
                                            
        return metadataRepository.saveAndFlush(repository);
    }

    // Move the repository from INITIALIZING to READY, or record a storage failure.
    public RepositoryResponse create(CreateRepositoryRequest request) throws IOException {
        RepositoryEntity repository = createMetadata(request.name());
        repository.setDescription(request.description() == null ? "" : request.description().strip());

        try {
            storage.initialize(repository.getId());
        } catch (IOException | RuntimeException failure) {
            // Retain failed metadata so incomplete setup can be inspected later.
            repository.setStatus(RepositoryStatus.FAILED);

            try {
                metadataRepository.saveAndFlush(repository);
            } catch (RuntimeException statusFailure) {
                // Preserve the storage error if recording FAILED also fails.
                failure.addSuppressed(statusFailure);
            }

            throw failure;
        }

        // Storage is ready; keep it intact if publishing READY fails.
        repository.setStatus(RepositoryStatus.READY);
        repository = metadataRepository.saveAndFlush(repository);

        // Expose the public DTO instead of returning the persistence entity.
        return response(repository);
    }

    // Return lifecycle metadata even when setup is incomplete; absence stays explicit.
    public Optional<RepositoryResponse> findById(UUID id) {
        return metadataRepository.findByIdAndOwnerId(id, access.user().id()).map(this::response);
    }

    // Fetch one page of database metadata without reading repository files.
    public Page<RepositoryResponse> list(Pageable pageable) {
        return metadataRepository.findAllByOwnerId(access.user().id(), pageable).map(this::response);
    }

    public RepositoryResponse rename(UUID id, RenameRepositoryRequest request) throws IOException {
        synchronized (access) {
            RepositoryEntity entity = requireMetadata(id);
            // Creation still owns its metadata until setup has finished.
            if (entity.getStatus() == RepositoryStatus.DELETING
                    || entity.getStatus() == RepositoryStatus.INITIALIZING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Repository setup or deletion is in progress.");
            }
            entity.setName(request.name());
            entity.setDescription(request.description() == null ? entity.getDescription() : request.description().strip());
            return response(metadataRepository.saveAndFlush(entity));
        }
    }

    // DB transactions cannot undo filesystem deletion: retain DELETING until cleanup succeeds.
    public void delete(UUID id) throws IOException {
        synchronized (access) {
            RepositoryEntity entity = requireMetadata(id);
            if (entity.getStatus() == RepositoryStatus.INITIALIZING) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Repository setup is in progress.");
            }
            entity.setStatus(RepositoryStatus.DELETING);
            metadataRepository.saveAndFlush(entity);
            storage.delete(id);
            metadataRepository.deleteById(id);
        }
    }

    private RepositoryEntity requireMetadata(UUID id) {
        return metadataRepository.findByIdAndOwnerId(id, access.user().id()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Repository not found."));
    }

    private RepositoryResponse response(RepositoryEntity entity) {
        return new RepositoryResponse(entity.getId(), entity.getName(),
                entity.getCreatedAt(), entity.getStatus(), entity.getDescription());
    }
}
