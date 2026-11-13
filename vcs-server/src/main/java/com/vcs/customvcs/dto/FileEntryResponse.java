package com.vcs.customvcs.dto;

// Paths are relative to the repository; directories have size zero.
public record FileEntryResponse(String name, String path, boolean directory, long size) {
}
