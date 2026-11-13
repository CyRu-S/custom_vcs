package com.vcs.customvcs.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

// Bind storage settings and validate them when Spring creates this bean.
@Validated 
@ConfigurationProperties(prefix="customvcs.storage")
public record StorageProperties(
    // Shared parent directory for repositories managed by this server.
    @NotNull Path root
) {

}
