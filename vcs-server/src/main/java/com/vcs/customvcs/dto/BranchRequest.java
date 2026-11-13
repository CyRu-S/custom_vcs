package com.vcs.customvcs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Match the engine's single-component branch names rather than accepting filesystem paths.
public record BranchRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String name) {
}
