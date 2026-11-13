package com.vcs.customvcs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Renaming changes the display label, never the UUID-based storage directory.
public record RenameRepositoryRequest(
        @NotBlank(message = "Repository name is required.")
        @Size(max = 64)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9_-]*") String name,
        @Size(max = 300) String description) {
    public RenameRepositoryRequest(String name) { this(name, null); }
}
