package com.vcs.core;

// History needs both the object's ID and its decoded commit metadata.
public record CommitEntry(String id, Commit commit) {
}
