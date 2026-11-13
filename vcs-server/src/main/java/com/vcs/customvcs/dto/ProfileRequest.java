package com.vcs.customvcs.dto;

import jakarta.validation.constraints.*;

public record ProfileRequest(@NotBlank @Size(max = 80) String name) {
}
