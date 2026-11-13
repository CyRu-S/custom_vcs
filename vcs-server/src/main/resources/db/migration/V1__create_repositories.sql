-- Store repository metadata; VCS objects remain on disk.
CREATE TABLE repositories (
    id UUID PRIMARY KEY,
    name VARCHAR(64) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    status VARCHAR(20) NOT NULL,

    -- Keep database lifecycle values aligned with RepositoryStatus.
    CONSTRAINT repositories_status_check
        CHECK (status IN ('INITIALIZING', 'READY', 'FAILED'))
);

