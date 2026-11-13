package com.vcs.customvcs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// The server uses the signed-in profile as author; this optional field keeps old CLI/API clients compatible.
public record CreateCommitRequest(
    @Size(max = 200) 
    String author,

    @NotBlank(message = "Commit message is required.")
    @Size(max = 4000)
    String message
) {

}
