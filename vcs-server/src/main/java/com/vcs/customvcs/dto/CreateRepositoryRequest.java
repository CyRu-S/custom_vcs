package com.vcs.customvcs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Incoming API data; validation runs when the controller uses @Valid.
public record  CreateRepositoryRequest(
    // Accept a repository label, never a client-selected storage path.
    @NotBlank(message="Repository name is required.")
    @Size(max = 64, message="Repository name must not exceed 64 characters.")
    @Pattern(
        regexp="[A-Za-z0-9][A-Za-z0-9_-]*",
        message="Repository name must start with a letter or digit and contain only letters, digits, underscore, or hyphens."
    )
    String name,
    @Size(max = 300) String description
) {
    public CreateRepositoryRequest(String name) { this(name, ""); }
}
