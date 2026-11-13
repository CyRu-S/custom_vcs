package com.vcs.customvcs.entity;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.Setter;

// Database metadata for a repository, separate from its files and VCS objects.
@Entity
@Table(name = "repositories")
@Data
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RepositoryEntity {

    // The same ID identifies this row and the repository's storage directory.
    @Id
    @EqualsAndHashCode.Include
    @Setter(AccessLevel.NONE)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String name;

    @Column(name = "owner_id", updatable = false)
    private UUID ownerId;

    @Builder.Default
    @Column(nullable = false, length = 300)
    private String description = "";

    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // Store lifecycle names so enum ordering cannot change their meaning.
    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=20)
    private RepositoryStatus status;
    
}
