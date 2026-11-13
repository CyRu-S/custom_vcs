package com.vcs.customvcs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Editing changes the working tree only; staging remains an explicit separate operation.
public record WriteFileRequest(
        @NotBlank @Size(max = 1024) String path,
        @NotNull @Size(max = 1_048_576) String content) {
}
