package com.vcs.customvcs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// The signed-in profile supplies the real author; caller-supplied author is ignored.
public record MergeRequest(
        @NotBlank @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]*") String sourceBranch,
        @Size(max = 200) String author,
        @Size(max = 4000) String message) {
}
