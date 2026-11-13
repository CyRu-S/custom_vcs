package com.vcs.customvcs.dto;

import java.util.List;

// Keep the public pagination format independent of Spring Data.
public record RepositoryPageResponse(
    List<RepositoryResponse> repositories,
    int page, 
    int size,
    long totalElements,
    int totalPages
) {
    public RepositoryPageResponse {
        repositories = List.copyOf(repositories);
    }
}
