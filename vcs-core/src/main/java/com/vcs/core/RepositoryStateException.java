package com.vcs.core;

import java.io.IOException;

// The operation is valid, but the repository needs attention before it can proceed.
// Unlike a disk failure, this can be explained to the user as a normal VCS condition.
public class RepositoryStateException extends IOException {
    public RepositoryStateException(String message) {
        super(message);
    }
}
