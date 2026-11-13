package com.vcs.customvcs.dto;

// The initial current branch has no commit ID until its first commit.
public record BranchResponse(String name, String commitId, boolean current) {
}
