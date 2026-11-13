package com.vcs.customvcs.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.vcs.customvcs.entity.RepositoryEntity;

// Spring Data supplies database operations for metadata; VCS objects stay on disk.
public interface RepositoryMetadataRepository extends JpaRepository<RepositoryEntity, UUID>{
    java.util.Optional<RepositoryEntity> findByIdAndOwnerId(UUID id, UUID ownerId);
    org.springframework.data.domain.Page<RepositoryEntity> findAllByOwnerId(
            UUID ownerId, org.springframework.data.domain.Pageable pageable);
}
