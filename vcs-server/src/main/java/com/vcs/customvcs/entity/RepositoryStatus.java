package com.vcs.customvcs.entity;

public enum RepositoryStatus {
    // Metadata exists; storage setup has not been confirmed complete.
    INITIALIZING,
    // Storage setup completed and the repository can be used.
    READY,
    // Storage setup failed; retain the attempt for recovery.
    FAILED,
    // Block VCS operations while storage removal is pending or being retried.
    DELETING
}
