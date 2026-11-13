package com.vcs.customvcs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Select one working-file path to add, update, or remove from the next commit.
public record StageFileRequest (
    @NotBlank(message = "File path is required.")
    @Size(max = 1024, message = "File path is too long.")
    String path
) {

}
