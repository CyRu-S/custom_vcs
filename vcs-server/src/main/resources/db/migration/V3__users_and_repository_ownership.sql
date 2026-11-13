CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Legacy repositories are preserved but not assigned to an arbitrary new account.
ALTER TABLE repositories ADD COLUMN owner_id UUID REFERENCES app_users(id);
ALTER TABLE repositories ADD COLUMN description VARCHAR(300) NOT NULL DEFAULT '';
CREATE INDEX repositories_owner_created_idx ON repositories(owner_id, created_at, id);
