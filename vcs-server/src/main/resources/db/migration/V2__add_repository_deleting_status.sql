-- Preserve V1's checksum; extend the lifecycle through a new migration.
ALTER TABLE repositories DROP CONSTRAINT repositories_status_check;
ALTER TABLE repositories ADD CONSTRAINT repositories_status_check
    CHECK (status IN ('INITIALIZING', 'READY', 'FAILED', 'DELETING'));
