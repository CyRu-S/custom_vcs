# Stage 6: REST backend and database

Implemented. All local VCS operations are exposed through Spring Boot and connected to React. Accounts, session authentication and owner checks now protect these APIs.

`Controller -> service -> vcs-core`, with PostgreSQL for users/metadata and filesystem storage for objects, references, index and merge state. `RepositoryAccess.open(id)` checks the current owner and READY status. File services synchronize on the shared access bean, coordinating one server process.

Status, staging, unstaging, commits and history share `RepositoryVcsController` and `RepositoryVcsService`. These are the starting point for learning the backend. Branches/checkout/diff/merge remain in `RepositoryOperationsController` and `RepositoryOperationsService`; file editing, repository metadata and authentication each have their own area.

## Repository routes

Prefix: `/api/repositories`.

| Method | Suffix | Purpose |
|---|---|---|
| POST / GET | collection | Create / paginated owner metadata |
| GET / PATCH / DELETE | `/{id}` | Metadata / name-description / delete |
| GET / DELETE | `/{id}/files?path=...` | List directory / delete working file |
| GET / PUT | `/{id}/files/content` | Read query path / write path-content body |
| GET | `/{id}/status` | Staged and working changes |
| POST | `/{id}/stage`, `/{id}/stage/undo` | Stage / unstage one path |
| POST / GET | `/{id}/commits` | Create commit / read history |
| GET | `/{id}/commits/{commitId}/files` | Saved text snapshot |
| GET / POST / DELETE | `/{id}/branches` | List / create / delete query name |
| POST | `/{id}/checkout` | Restore branch named in body |
| GET | `/{id}/diff?scope=working` or `staged` | Compare the relevant states |
| GET / POST | `/{id}/merge` | Pending state / start merge |
| POST | `/{id}/merge/abort` | Abort pending merge |
| GET | `/{id}/workspace` | Read-only React projection |

Authentication: GET `/api/auth/csrf`, POST `/api/auth/register`, POST `/api/auth/login`, GET/PATCH `/api/auth/me`, POST `/api/auth/logout`. Unsafe requests require CSRF. Swagger documents schemas and provides token authorization. REST commit/merge author comes from the authenticated profile; any compatibility author field is ignored.

Files are UTF-8 text without NUL, up to 1 MiB. Paths cannot escape the repository or target metadata, symlinks/junctions, Windows aliases or alternate streams. Checkout/merge require clean state. Expected preconditions return 409; nonexistent or foreign repositories return 404. Validation and malformed JSON return Problem Detail 400 responses.

Flyway migrations V1-V3 own schema changes, and Hibernate validates mappings. Creation uses INITIALIZING -> READY/FAILED. Deletion records DELETING until filesystem cleanup succeeds. Do not edit applied migrations.

The full request flows, object formats, ownership, limitations and troubleshooting are in [PROJECT-WORKFLOW.md](PROJECT-WORKFLOW.md). Executed checks are in [VERIFICATION.md](VERIFICATION.md).
