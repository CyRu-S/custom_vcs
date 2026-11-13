package com.vcs.customvcs.dto;

import java.time.Instant;
import java.util.UUID;
import com.vcs.customvcs.entity.RepositoryStatus;

// Public metadata returned to clients, without exposing server storage paths.
public record  RepositoryResponse(
    UUID id,
    String name, 
    Instant createdAt,
    // Let clients distinguish usable repositories from incomplete setup.
    RepositoryStatus status,
    String description
) {

}
