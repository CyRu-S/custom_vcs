# 01 / What you built

CustomVCS is a version control system with its own Java engine. The engine hashes and stores content, remembers staged files, creates commits, moves branch pointers, restores snapshots, compares text, and performs three-way merges. Git never implements these operations.

The same engine has two interfaces. Picocli provides local terminal commands without a server. Spring Boot exposes HTTP endpoints for a React workspace. PostgreSQL stores accounts and repository metadata; ordinary files hold the actual version history. This separation is the central architectural idea of the project.

The browser now uses real APIs. Registration creates an account, signing in starts a server session, and each account sees only its own repositories. A newly created repository is empty. The previous frontend fixtures are removed and their old browser storage is retired. Existing server repositories are preserved.

Start with docs/LEARNING-GUIDE.md for the short reading path. The everyday backend workflow now lives in one RepositoryVcsController and one RepositoryVcsService. You do not need to memorize every method. Understand the state being read, the state being changed, and which layer owns each decision. For example, a controller understands HTTP, a service checks ownership and coordinates work, and the core engine understands commits without knowing anything about a web session.

## Current finish line

- Local engine: init, add, status, commit, history, branches, checkout, diff, merge and merge abort.
- Browser: registration/login/logout, private repositories, text files, staging, history, branches and merge resolution.
- Persistence: PostgreSQL accounts/metadata, Flyway migrations, and filesystem VCS storage.
- Delivery: tests, a combined frontend/backend JAR, Docker configuration and this learning guide.

Remote push/pull/clone, invitations, email verification, password reset, distributed locks and production-scale hosting remain extensions. The project is a complete educational workspace within this scope, rather than a replacement for every feature of Git hosting.

# 02 / The architecture at a glance

[diagram:architecture]

React renders the workspace and keeps temporary editor state. Requests go to Spring Boot through the same origin in a packaged release, or through Vite's /api proxy during development. Spring Security authenticates the session and checks CSRF before controllers execute.

Controllers translate HTTP input into service calls. The service layer checks repository ownership and readiness, then invokes vcs-core. The core has no Spring runtime dependency and no database dependency. It reads and writes a working directory and its .customvcs folder.

PostgreSQL answers questions such as "Who owns repository X?" and "What is its display name?" Object storage answers "Which bytes belong to commit Y?" A SQL transaction cannot roll back a filesystem write, which is why repository creation/deletion use lifecycle states instead of pretending both stores are one transaction.

The CLI bypasses the server entirely and calls the same core classes on a local repository. The server's synchronized monitor coordinates its own services only. Do not run a CLI against an actively used server-managed directory: they do not share a cross-process lock.

## Dependency rule

vcs-cli and vcs-server depend on vcs-core. vcs-core does not depend on either interface. The React application depends on the REST contract, not Java classes. Keeping this direction prevents a file-storage feature from becoming coupled to a web controller.

# 03 / Run the project locally

Use JDK 25, Maven 3.9.12, Node 22.12 or newer supported by the installed Vite version, and PostgreSQL. This project was developed with Java 25; you do not need to switch back to Java 21 for this version. Run commands from the folder containing the root pom.xml.

Create a PostgreSQL database named customvcs. Copy .env.example to .env and enter your local credentials. The file uses Java properties syntax, so values are unquoted; use forward slashes in storage paths. Never commit this file or include its contents in screenshots.

```text
CVCS_DB_URL=jdbc:postgresql://localhost:5432/customvcs
CVCS_DB_USERNAME=postgres
CVCS_DB_PASSWORD=your-local-password
CVCS_STORAGE_ROOT=./data/repositories
CVCS_SECURE_COOKIES=false
```

## Development: two processes

```powershell
mvn install -DskipTests
mvn -pl vcs-server spring-boot:run
# In another terminal:
cd frontend
npm ci
npm run dev
```

Open the Vite address printed in the terminal, normally http://127.0.0.1:5173. Keep the hostname consistent; localhost and 127.0.0.1 have different cookie scopes. Vite proxies /api to port 8080. You can override its target with CVCS_API_TARGET.

DevTools restarts the backend after changed Java classes compile. Saving a file alone is insufficient if the IDE has not built it. Dependency changes require a restart. In-memory login sessions can disappear when the application restarts, so sign in again if prompted.

## One packaged application

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-release.ps1
java -jar vcs-server/target/vcs-server-0.0.1-SNAPSHOT.jar
```

Open http://localhost:8080. The release script builds React, copies generated assets into Maven's target directory, and packages one Boot JAR. Stop the running JAR before replacing it on Windows. Stop Vite before npm ci because its native bundler can hold files open.

For short local commands, run scripts/install-cli.ps1 after building the CLI, then restart VS Code once. It adds bin/vcs.cmd to your user PATH. Inside any working folder, use vcs init, vcs add hello.txt, vcs status, vcs commit --author "Your Name" -m "Initial commit", and vcs log. This launcher calls the Java CLI directly without a server and keeps your current folder.

# 04 / Know your project folders

| Location | Responsibility | Start reading here |
|---|---|---|
| vcs-core/src/main/java/com/vcs/core | Pure Java version control | Repository, ObjectStore, StagingArea |
| vcs-cli/src/main/java/com/vcs/cli | Picocli command adapters | VcsCommand, AddCommand, CommitCommand |
| vcs-server/.../controller | HTTP routes and response status | RepositoryController, RepositoryVcsController |
| vcs-server/.../service | Ownership and command coordination | RepositoryAccess, RepositoryVcsService |
| vcs-server/.../entity | SQL row mappings | UserEntity, RepositoryEntity |
| vcs-server/.../repository | Spring Data database queries | UserRepository, RepositoryMetadataRepository |
| vcs-server/.../security | Current authenticated identity | SessionUser, CurrentUser |
| vcs-server/.../config | Security, storage and OpenAPI setup | SecurityConfig, StorageProperties |
| vcs-server/.../dto | Request and response contracts | StageFileRequest, RepositoryStatusResponse |
| vcs-server/src/main/resources/db/migration | Versioned SQL migrations | V1, V2, V3 in order |
| frontend/src | React pages, API and server state | api.ts, store.tsx, Source.tsx |
| frontend/tests | Real browser workflows | entry.spec.ts, studio.spec.ts |
| docs | Learning and implementation notes | This guide, STAGE-4.md, STAGE-5.md |
| data/repositories | Runtime repository files | UUID directory per repository |

The abbreviated server locations in this table are under vcs-server/src/main/java/com/vcs/customvcs. Maven target folders and frontend dist are generated output. Deleting generated output does not delete source; deleting data/repositories does delete real VCS data.

The frontend-previous directory and .learning-backups are old local reference material, excluded from source control and deployment. They are not loaded by the running application. The current frontend has no seeded repository dataset.

# 05 / The three states of a file

[diagram:states]

The working tree is the file you are editing now. The index, also called the staging area, records the exact version selected for the next commit. HEAD identifies the current branch, whose pointer identifies the last committed snapshot.

Suppose hello.txt says "one". You stage it, then edit it to "two". The index still references the blob containing "one". Creating a commit saves "one", while "two" remains an unstaged working edit. This is why add and commit are separate operations.

## What status compares

Staged status compares HEAD's snapshot with the index. Working status compares the index with files on disk. A file can therefore appear in both lists at the same time. That is expected, and the frontend keeps distinct entries for the staged and working versions of the same path.

- Added: a path appears in the newer state but not the older state.
- Modified: both states contain the path, but their blob IDs differ.
- Deleted: the older state has a path that the newer state lacks.
- Untracked: a working file is absent from the index.

Unstaging restores the index entry from HEAD and leaves working bytes alone. Before the first commit, unstaging removes the new entry from the index. Deleting a working file does not erase its previous commits; stage that missing tracked path to commit its deletion.

Think of the index as a complete proposed next snapshot, not merely a list of changed filenames. After a successful commit, the index still represents that committed snapshot. Status becomes clean because HEAD and the index now match.

# 06 / Object IDs and content storage

ObjectStore combines a typed header with payload bytes and hashes the complete object using SHA-256. The identifier is 64 lowercase hexadecimal characters. The object file is stored below .customvcs/objects using the first two characters as a directory and the remaining 62 as its filename.

```text
Payload: Hello
Stored bytes: blob 5<NUL>Hello
Object ID:
1301800ffa9c48e2a82cbfda7fe9d17d5605cfa5df7c673639c44d8fcc244a71
Path: objects/13/01800ffa9c48e2a82cbfda7fe9d17d5605cfa5df7c673639c44d8fcc244a71
```

The length is a byte count. A non-ASCII character can occupy several UTF-8 bytes. The NUL byte separates the header from the payload and is not a printable dot, even though a hex viewer may display a dot for it.

Content addressing gives deduplication: storing identical typed bytes returns the same ID and does not require another distinct object. Changing the content changes the ID. Including the object type in the hashed header keeps a blob and a commit with identical payload bytes from being the same object.

Objects are treated as immutable. Commits and branch pointers refer to IDs rather than embedding another copy of every file. Object reads validate the expected type and format. Hashing detects accidental changes; it is not encryption and does not make stored source confidential to someone who can read the server disk.

ObjectId handles ID validation/hashing. ObjectStore handles the typed object representation and storage. SnapshotCodec and CommitCodec handle their specific payload formats. This keeps binary serialization separate from filesystem placement.

# 07 / Index, snapshot and commit formats

The index starts with the four ASCII bytes CVSI, meaning CustomVCS Index. It then stores format version 1, an entry count, and path/blob-ID pairs. Java DataOutputStream/DataInputStream write and read matching binary fields. Integer fields are big-endian; writeUTF uses Java's length-prefixed modified UTF-8 format.

A snapshot payload contains version 1, entry count, and sorted path/blob-ID pairs. Sorting makes serialization deterministic: inserting the same entries into a map in a different order still produces the same snapshot object ID. The snapshot is a flat path map, which is simpler than Git's hierarchical tree objects.

## Commit payload

| Field | Meaning |
|---|---|
| version | 1 for ordinary commits, 2 for two-parent merge commits |
| snapshotId | Object ID of the saved path-to-blob map |
| hasParent / parentId | First parent; absent for the first commit |
| secondParentId | Merge source parent in version 2 |
| author | Name from the authenticated profile in the REST interface |
| epoch seconds / nanoseconds | Instant timestamp without locale-dependent text |
| message | User's explanation of the change |

CommitCodec decodes both versions, so ordinary commits retain their earlier representation. Invalid versions, malformed IDs, duplicate paths in snapshots, invalid timestamps and unexpected trailing bytes are rejected rather than silently guessed.

A commit ID identifies the whole commit, including its timestamp, parents and message. Two commits with the same files can have different IDs. A snapshot ID identifies just the file map. A blob ID identifies the typed file bytes. Keep these three identities separate when debugging.

# 08 / Repository initialization and references

Repository.initialize creates .customvcs/objects, .customvcs/refs/heads and a HEAD file. A new HEAD contains "ref: refs/heads/main" followed by a newline. Before the first commit, there is no main branch reference file because there is no commit to point to yet. The UI still presents this unborn branch as main.

```text
repository-root/
  hello.txt                     working file
  .customvcs/
    HEAD                        ref: refs/heads/main
    index                       persisted staging snapshot
    objects/13/...              immutable typed objects
    refs/heads/main             current commit ID
    refs/heads/feature          another branch's commit ID
    MERGE_STATE                 present only during a pending merge
```

Calling init again preserves an existing HEAD. It must not silently switch the user back to main or replace their history. ReferenceStore resolves HEAD, reads branch tips, validates branch names and updates branch pointer files.

Branch names are flat and restricted to the supported safe characters. A display name such as "My backend" in PostgreSQL is separate from a branch name or storage directory. Server repository directories use UUIDs, so renaming the display label does not move version data.

Branches are cheap because each is a small pointer. Creating a branch does not copy every object or working file. The branch stays at its original commit until commits are made while it is selected. Updating the current branch moves its pointer; HEAD continues to name that branch.

# 09 / Registration, login and CSRF

[diagram:auth]

Registration validates the name, email and password, normalizes the email, hashes the password with BCrypt cost 12, and saves an app_users row. The raw password is never returned and is not stored in browser local storage. Passwords must meet request validation and BCrypt's 72-byte UTF-8 limit.

Login looks up the normalized email and compares the submitted password with the saved hash. Unknown emails and incorrect passwords receive the same error message. A dummy hash comparison reduces the easiest timing distinction for unknown accounts.

On successful registration or login, AuthController rotates the session ID, creates a SecurityContext with a SessionUser principal, and explicitly saves that context through HttpSessionSecurityContextRepository. SessionUser contains only the public ID/name/email, not the password hash.

The browser receives an HttpOnly JSESSIONID cookie. It automatically sends that cookie on same-origin requests. HttpOnly prevents JavaScript from reading the cookie; it does not protect against every consequence of an XSS vulnerability. Logout invalidates the server session and clears the cookie. Sessions expire after 60 minutes of inactivity and are not persisted across application restarts.

## Why a second token?

Cookies are sent automatically, so unsafe requests also require a CSRF token. GET /api/auth/csrf returns a masked token and its header name. api.ts sends that token on POST, PUT, PATCH and DELETE. Authentication clears/rotates the CSRF state, so React fetches a fresh token after login, registration or logout.

This project uses server sessions instead of JWTs because the browser and API share an origin and the app is a single instance. Token refresh tables, bearer token storage and a JWT filter are unnecessary for this scope. HTTPS deployments must enable Secure cookies. Rate limiting, password recovery and email verification remain production work.

# 10 / Ownership and the SQL schema

| Table | Important columns | Purpose |
|---|---|---|
| app_users | id, name, email, password_hash, created_at | Account identity and BCrypt credentials |
| repositories | id, name, description, owner_id, created_at, status | Repository identity, owner and lifecycle |
| flyway_schema_history | version, description, checksum, success | Applied schema migration record |

RepositoryMetadataRepository queries by both repository ID and current owner ID. RepositoryAccess.open checks ownership and READY status before exposing a core Repository. File, status, commit, branch, diff, merge and workspace APIs all use this access path. Knowing another repository's UUID is not permission to read it.

Foreign and nonexistent repository IDs both return 404. This avoids confirming that another account's private repository exists. Repository lists filter by owner in SQL rather than fetching everyone's rows and hiding them in React. Frontend route hiding is a convenience, not an authorization boundary.

The REST commit and merge services derive the author from the signed-in profile. A legacy author field remains accepted for compatibility, but cannot impersonate another author through these endpoints. The local CLI still accepts --author because it has no server identity.

## Existing data after authentication is introduced

V3 deliberately leaves owner_id null on repositories created before accounts existed. Those repositories remain on disk and in PostgreSQL, but do not appear in any new account. They are not automatically assigned to the first person who registers.

If you want to adopt one, inspect both UUIDs in your own database and deliberately run an UPDATE for that exact repository and exact user. Back up first. Do not run an unrestricted UPDATE assigning every repository to one account.

```sql
SELECT id, name, owner_id FROM repositories;
SELECT id, email FROM app_users;
UPDATE repositories SET owner_id = '<your-user-uuid>'
WHERE id = '<specific-repository-uuid>' AND owner_id IS NULL;
```

# 11 / Flyway and repository lifecycle

Flyway runs migrations in version order before Hibernate checks the entities. V1 creates repository metadata. V2 expands the lifecycle constraint to include DELETING. V3 adds users, repository ownership, descriptions and an owner/listing index. Do not rewrite a migration that has already been applied; create V4 for the next schema change.

Hibernate ddl-auto=validate checks compatibility instead of silently altering the database. This makes SQL changes visible and repeatable. Flyway's schema history records migration checksums, so an edited old migration can stop startup with a validation error.

## Creation

1. Generate a UUID and insert INITIALIZING metadata with the current owner.
2. Create the UUID-named repository directory and call core initialize.
3. Publish READY metadata and return 201 with the public DTO.
4. If storage setup fails, attempt to record FAILED and return an error.

If storage succeeds but publishing READY fails, storage is retained. An operator can inspect the incomplete state rather than losing data automatically. The lifecycle is intentionally explicit because a SQL rollback cannot undo every filesystem operation.

## Deletion

The service records DELETING before removing storage. After storage deletion succeeds, it deletes the metadata row. If cleanup fails, DELETING remains visible to administrators and a retry can continue cleanup. The normal engine endpoints reject repositories that are not READY.

All server filesystem operations synchronize on the shared RepositoryAccess bean. This is easy to understand and prevents overlapping mutations within one application process. It also serializes different repositories, so it sacrifices throughput. It does not coordinate multiple server instances or unrelated CLI processes, and it is not a complete crash-recovery protocol.

# 12 / Follow one file from editor to commit

Start with an empty private repository and create hello.txt in Source. React sends PUT /api/repositories/{id}/files/content with path/content. WorkingFileService validates the path, applies UTF-8/size rules, writes a temporary sibling file and replaces the destination. The response acknowledges the working file, not a commit.

Open Changes and stage hello.txt. RepositoryVcsController passes the request to RepositoryVcsService, which opens the owned repository, loads the existing index, and calls StagingArea.stagePath. The engine stores the current file bytes as a blob and records path -> blob ID in the index, then persists the index.

Write a message and commit. RepositoryVcsService obtains the author from CurrentUser and calls the core CommitService. The engine serializes the index as a snapshot object, creates a commit pointing to that snapshot and the current parent, stores the commit object, and advances the selected branch pointer.

React refreshes the authoritative workspace after the mutation. Staged status is clean because HEAD now contains the index snapshot. If the working file was changed after staging, working status still reports that later edit. The history page loads the saved commit snapshot, so it displays the committed bytes rather than whatever happens to be in the editor today.

## Why not save everything in a controller?

The controller cannot safely decide the VCS snapshot by itself. Reusing the engine keeps the CLI and REST behavior aligned. The service adds HTTP-specific identity and coordination; it does not reimplement SHA-256 storage or merge logic. DTOs prevent accidental serialization of entities, password hashes or filesystem paths.

When debugging, inspect the request, ownership lookup, index entries, commit snapshot and branch pointer in that order. These are separate states, so a valid HTTP response alone does not prove the correct file version was committed.

# 13 / Status, history and the workspace view

GET /status returns the branch, current commit ID, staged added/modified/deleted lists, and working modified/deleted/untracked lists. It reports state without writing objects solely to compute status. Untracked files belong to status even though ordinary diff focuses on tracked/indexed comparisons.

GET /commits exposes the engine's history entries. The React graph uses GET /workspace, which traverses both parents and branch tips to assemble up to 200 commits. A merge is a graph, so following only one parent would hide the source side of the merge.

WorkspaceService joins metadata, branch pointers, current working text, index/HEAD comparisons and pending merge information into a read-only view. It is a convenience projection, not a second VCS database. Every mutation still uses its normal command endpoint.

To avoid returning every historical file on every refresh, commit entries initially contain no snapshot file payload. History requests /commits/{commitId}/files when a commit is selected, then compares it with its first parent. React may cache the fetched view in memory, but the engine remains the source of truth.

## Browser limits

The text editor accepts UTF-8 without NUL, at most 1 MiB per file. The aggregate workspace is limited to 2000 files and 8 MiB of inspected content. Binary or oversized files are listed as unavailable for text preview. The graph cap does not delete old commits; use the CLI for larger histories and byte-oriented inspection.

Metadata lists are paginated, 20 per frontend page. A dashboard reflects loaded repositories, not an invented global activity service. The JSON export is a loaded view for inspection, not a restorable backup of every object.

# 14 / Branches and safe checkout

Creating a branch writes a reference to the current commit. There must already be a commit to reference. The current branch does not change until checkout. In the UI, New branch creates the pointer; Branches lets you select it.

Checkout requires clean staged and working state. The conservative policy also refuses untracked files that could be lost or obstruct restoration. It validates the target snapshot and filesystem paths before restoring files, rebuilds the index to match the target snapshot, and selects the branch after restoration.

```text
main -> A
create feature: main -> A, feature -> A
checkout feature, commit B: main -> A, feature -> B
checkout main: working tree and index return to A
```

The key safety rule is that moving HEAD alone is insufficient. Users expect working files and the index to match the selected branch too. Changing HEAD before restoring files could leave the repository claiming one state while displaying another.

Branch deletion is conservative. The current branch cannot be deleted, a pending merge blocks deletion, and an unmerged tip cannot be removed. A merged non-current pointer can be deleted without deleting immutable commit objects. This app does not expose a force-delete branch option.

The current implementation is suitable for one local process. Disk failure partway through checkout is not equivalent to a transactional database rollback. Keep backups of important repositories and avoid manually modifying .customvcs while an operation is running.

# 15 / Diff: what changed, line by line

Working diff compares an indexed blob with current working bytes. Staged diff compares HEAD's blob with the indexed blob. After staging and then editing a file again, these two diffs deliberately show different changes.

TextDiff uses longest common subsequence reasoning to identify unchanged lines, removals and additions. An LCS table is roughly proportional to the product of the two line counts, so bounded input prevents a large text comparison from allocating excessive memory. This is an educational implementation, not Git's complete diff algorithm.

The UI DiffViewer formats text changes with line numbers, added/removed markers and unified or split presentation. Its client-side comparison is display logic. It never decides what bytes the index or a commit contains. Server GET /diff remains available to API clients, while React can display before/after text already supplied in the workspace projection.

## Important distinctions

- A plus line means text exists in the newer side; it does not mean it has been committed.
- A path can have one staged diff and another unstaged diff.
- A missing side represents a creation or deletion, not an empty string by assumption.
- Binary files are not safely rendered as text. Their changes can still be represented by IDs or staged through the engine.
- First-parent commit comparison is a presentation choice for a merge commit, not the entire merge ancestry.

If a diff looks surprising, first check which two states are being compared. Most confusion comes from comparing working bytes against HEAD while expecting working bytes against the index.

# 16 / Three-way merging

[diagram:merge]

Merging brings a source branch into the current branch. CommitGraph finds ancestry information and a common base. The engine compares base, ours (current tip) and theirs (source tip). Clean index/working state is required before merging.

## Four outcomes

| Kind | Meaning | Result |
|---|---|---|
| UP_TO_DATE | Source is already an ancestor of current | No new commit required |
| FAST_FORWARD | Current is an ancestor of source | Restore source snapshot and advance current pointer |
| MERGED | Branches diverged and changes combine | Create a two-parent commit |
| CONFLICTS | Automatic combination is ambiguous | Persist merge state and wait for resolutions |

For one path, if ours equals base, take theirs. If theirs equals base, keep ours. If ours equals theirs, keep that shared result. When both sides changed differently, text merging can combine non-overlapping edits; overlapping edits require user resolution. Modify/delete and binary disagreements can also conflict.

A normal merge commit records ours as its first parent and theirs as its second parent. This preserves why the branches became connected. Fast-forward does not need an extra merge commit because ancestry already describes the combined history.

The algorithm is intentionally smaller than Git's merging system. It does not implement every rename-detection strategy or sophisticated recursive handling of complex criss-cross ancestry. The value of this project is understanding the three-way decision and preserving both parents correctly.

# 17 / Resolve or abort a conflict

When a merge conflicts, MergeStateStore persists MERGE_STATE with the relevant tips, source branch and unresolved paths. Recovery state stays on disk, so a browser refresh does not lose the fact that a merge is pending. Text conflicts appear in working files and the UI offers both sides and an editable resolution.

Choose current, incoming, or edit the result. Saving the file changes working bytes. Staging that path records the chosen resolution and removes it from the unresolved conflict set. A modify/delete choice may deliberately delete the working file and stage its absence. Resolve every conflicted path before creating the merge commit.

The commit step reads the persisted merge state, adds the second parent, advances the current branch and clears recovery state. There can be a valid merge commit even when the final snapshot matches ours: recording the second parent still changes ancestry.

Abort restores the pre-merge snapshot and index and clears merge state. The UI asks for confirmation because edits made during conflict resolution can be discarded. Do not use abort as an ordinary "undo last commit" action.

## Browser versus CLI

The browser intentionally does not offer a text editor for binary conflicts. Use the CLI and an appropriate external editor for those bytes. This prevents a missing text preview from being mistaken for a request to delete a binary file.

If the application restarts during a conflict, sign in again and open the repository. Pending merge information comes from disk. If a storage error occurred during an operation, inspect status and merge state before retrying; do not automatically replay a merge or commit after an ambiguous network failure.

[screenshot:merge]

# 18 / How React stays connected

api.ts owns HTTP requests, credentials, CSRF tokens and Problem Detail errors. It sends cookies with requests and adds the CSRF header to unsafe methods. A protected 401 emits a session-expired event. Tokens stay in memory rather than local storage.

store.tsx owns the signed-in profile, loaded repository views, loading/error state, pending mutation guard and presentation preferences. It does not implement commits in JavaScript. After a successful mutation it reloads the repository view from the server. A refresh failure after a saved operation tells the user to refresh before further work.

App/Entry render startup and account navigation. Source edits actual files. Review stages and commits. History fetches saved snapshots. Branches/merge call engine-backed endpoints. Settings updates the profile or repository metadata. Shared Button and Modal keep controls and confirmations consistent.

Theme and per-user stars are local preferences. Editor drafts live only in memory, keyed by user, repository and path, so another account does not inherit them. Drafts can survive a session-expiry redirect within the same running page, but not closing the browser. Browser storage contains no repository fixtures, passwords or bearer tokens.

## Resilience rules

Preserve form input after an error. Disable duplicate mutations while a request is pending. Ignore stale results after a different account signs in. Do not optimistically move a branch or invent a commit while the server may reject it. Show useful empty states and retry actions. Confirm destructive actions and warn before leaving a dirty editor.

Vite proxying and the packaged same-origin deployment avoid a separate CORS configuration. If you later deploy frontend and API on different origins, revisit cookies, CSRF and allowed origins together rather than enabling wildcard CORS with credentials.

# 19 / API map and Swagger

All repository routes below start with /api/repositories. Every repository operation is owner-scoped. The UUID is a server repository identity; a commit ID is a separate 64-character content identifier.

| Method | Route | Purpose |
|---|---|---|
| POST / GET | /api/auth/register, /api/auth/me | Register / inspect current profile |
| POST | /api/auth/login, /api/auth/logout | Start / end a session |
| GET / PATCH | /api/auth/csrf, /api/auth/me | Fetch CSRF / update profile name |
| POST / GET | repository collection | Create / paginated metadata list |
| GET / PATCH / DELETE | /{id} | Metadata / rename-description / remove |
| GET / DELETE | /{id}/files?path=... | List directory / delete working file |
| GET / PUT | /{id}/files/content | Read path query / write path-content body |
| GET | /{id}/status | Staged and working status |
| POST | /{id}/stage, /{id}/stage/undo | Stage / unstage one path |
| POST / GET | /{id}/commits | Commit / history |
| GET | /{id}/commits/{commitId}/files | Saved text snapshot |
| GET / POST / DELETE | /{id}/branches | List / create / delete name query |
| POST | /{id}/checkout | Select and restore branch |
| GET | /{id}/diff?scope=working or staged | Compare appropriate states |
| GET / POST | /{id}/merge | Pending state / merge sourceBranch |
| POST | /{id}/merge/abort | Abort pending merge |
| GET | /{id}/workspace | Aggregated React read model |

## Swagger UI

Open http://localhost:8080/swagger-ui/index.html. Execute GET /api/auth/csrf, copy its token into Authorize (X-CSRF-TOKEN), then execute register or login. Fetch a fresh token and authorize again after authentication because the old CSRF token was cleared. The same browser retains its session cookie. Now try a protected mutation. Refresh authorization after logout or session expiry too.

The endpoint request schema documents validation. Commit author input is optional and ignored by the REST service; the authenticated profile wins. Read-only requests need the authenticated cookie but do not require a CSRF header.

# 20 / Errors, filesystem safety and practical limits

ApiExceptionHandler returns application Problem Details rather than exposing exception stack traces. Field validation includes an errors object. Invalid JSON has a clear 400 response. Missing or foreign resources return 404. Expected dirty-tree or pending-merge conditions return 409. Unexpected disk failures return a generic 500 and should be investigated in server logs.

| Symptom | First thing to check |
|---|---|
| PostgreSQL password authentication failed | .env username/password and DB service/port; restart after changing config |
| Missing table / Flyway validation error | Migration history and schema; do not rewrite applied V1/V2/V3 |
| 401 after restart | Session memory was lost; sign in again |
| 403 on a mutation | Refresh CSRF token; use the same hostname and browser session |
| 409 on checkout/merge | Stage/commit or deliberately resolve working changes first |
| Boot JAR cannot be renamed on Windows | Stop the running packaged server before building |
| npm ci cannot replace native bundler | Stop Vite before reinstalling dependencies |
| UI cannot reach backend | Check port 8080, Vite proxy target, and server startup log |
| Repository missing after adding accounts | It may be legacy ownerless data; inspect owner_id deliberately |

RepositoryAccess rejects paths escaping the working root, metadata paths, absolute paths, symlinks/junctions, Windows device aliases and alternate data streams. The browser cannot select an arbitrary server directory. Upload/edit size limits protect text endpoints, and aggregate/history limits bound browser responses.

This remains a trusted-host educational app. An attacker who can modify the server filesystem concurrently is outside its simple path-checking model. A global in-process lock is not a distributed lock. Do not expose this unchanged to untrusted public traffic without the production work listed later.

# 21 / Tests and what they prove

The Java test suite covers the engine, CLI, controllers, services, migrations, ownership and the actual Spring Security filter chain. Temporary directories isolate file operations. H2 in PostgreSQL compatibility mode provides fast repeatable integration tests; a separate real PostgreSQL smoke run verifies the packaged application without using your existing data.

Core tests cover staged-byte semantics, deterministic snapshots, history links, checkout safety, diff behavior, merge outcomes, conflict recovery and abort. CLI tests exercise packaged command behavior. Server tests check lifecycle failures, path rejection, correct HTTP errors, status/staging/commit flows, hashing, CSRF rotation, login/logout and cross-account isolation.

Playwright runs a real browser against a running backend. It registers unique accounts, creates actual repositories, edits text, stages, commits, reloads history, deletes a repository, creates divergent branches, resolves a conflict, completes a two-parent commit and deletes a merged branch. It also checks narrow layouts, theme behavior, failed requests and recovery. A session-expiry test checks that signing in again preserves an unsaved editor draft. Axe scans cover the registration and connected history screens.

## Run the checks

```powershell
mvn verify
cd frontend
npm ci
npm run lint
npm run typecheck
npm run build
# Start the backend, then:
npm test
```

Set CVCS_E2E_URL to an existing frontend or packaged-app URL if you do not want Playwright to start Vite. Browser tests create isolated test accounts on the target server, so use a disposable database/storage target. Test passwords are fixtures, not production credentials.

See docs/VERIFICATION.md for executed counts and the precise environment. A passing suite is evidence for these cases, not a proof of all crash states, high-load behavior or hostile deployments. The Windows symbolic-link test may be skipped when the OS does not permit link creation.

# 22 / Release, deployment and backups

The release PowerShell script produces one executable Spring Boot JAR with the React dist assets included. SpaController forwards known browser routes to index.html, so refreshing a history or source URL works. /api remains the API namespace. Static resources and authentication pages are public; repository data is protected by Spring Security and ownership checks.

Dockerfile builds React with Node, builds Java with Maven/JDK 25, then runs a JRE container as a non-root user. compose.yaml adds PostgreSQL 18 and separate named volumes for SQL and repository files. PostgreSQL 18 uses the /var/lib/postgresql volume location for its versioned cluster directories. The database port is not exposed by this compose configuration.

```powershell
# Set CVCS_DB_PASSWORD in .env first.
docker compose up --build -d
docker compose logs -f app
docker compose down
```

Do not use "down -v" unless you intentionally want to delete the named database and repository volumes. Docker configuration is provided; see the verification report for whether Docker was available for execution in this session.

## Before public deployment

Use HTTPS through a reverse proxy, enable CVCS_SECURE_COOKIES=true, keep one application instance, protect database credentials and restrict disk/database access. Add request throttling, monitoring, operational recovery and tested backups before inviting untrusted users. In-memory sessions mean restarts log users out.

Back up PostgreSQL and the entire repository storage root together while writes are stopped. The metadata references filesystem UUIDs, and those directories contain all objects/index/refs/merge state. Copying just the JAR or just the SQL rows is not a full backup. Restore both into a disposable environment and verify history plus file content before trusting a backup procedure.

# 23 / Screens you can trace to real operations

[screenshot:history]

History displays actual commit IDs and saved file content. Select a parent to inspect its snapshot; use Source to inspect the working file. The same path can show different bytes in those two views because one is immutable history and the other is editable state.

[screenshot:mobile]

An empty repository starts without fake files or commits. The next action is to create a working file. Theme and mobile navigation are presentation details; repository identity and data still come from the backend.

# 24 / Learn the project without memorizing it

Read in this order: Repository and ObjectId; ObjectStore; StagingArea and StatusService; SnapshotCodec and CommitCodec; CommitService and ReferenceStore; CheckoutService and TextDiff; CommitGraph, MergeService, TextMerge and MergeStateStore. Follow one file through those classes before studying every validation branch.

For Spring Boot, read the stage method in RepositoryVcsController, then stage in RepositoryVcsService, then core StagingArea.stagePath. Next follow commit in the same controller and service into core CommitService. Read SecurityConfig, AuthController, AuthService and CurrentUser together, because a controller method alone does not explain the filter chain and session persistence.

For React, read api.ts before store.tsx. Then trace Source save, Changes stage/commit and History snapshot loading. Notice that no frontend function writes a commit object or moves a branch pointer directly.

## Questions you should be able to answer

- Why can one file be both staged and unstaged?
- Why does changing a commit message change its ID without changing its snapshot ID?
- Why is a branch a pointer rather than a copied folder?
- Why must checkout restore the index as well as working files?
- Why does a merge need a common base and sometimes a second parent?
- Why does a server session still need CSRF protection?
- Why must ownership be checked on the server, even if React hides other repositories?
- Why can @Transactional not make PostgreSQL and normal filesystem writes one atomic operation?

## Useful small exercises

Stage a file, edit it again, commit, and explain the remaining working diff. Create two branches with independent edits and predict fast-forward versus a true merge. Cause a text conflict, restart the app, and finish it. Register another account and confirm that the first account's UUID returns 404. Restore a backup into disposable storage and compare a historical file's object ID.

Build confidence by predicting state transitions, then checking the result. Meaningful comments explain why the states and safeguards exist; they are a map for rereading, not a substitute for tracing one complete request.

# 25 / Roadmap and reference shelf

| Original stage | Current outcome |
|---|---|
| 1. Setup and architecture | Maven modules and clear dependency direction |
| 2. Initialization and SHA-256 objects | Implemented in the engine |
| 3. Staging, status, commits and history | Implemented and exposed through CLI/API/UI |
| 4. Branching, checkout and diff | Implemented with conservative safety rules |
| 5. Merging and conflict resolution | Three-way merge, two parents, resolution and abort |
| 6. REST backend and database | Implemented with migrations and metadata lifecycle |
| 7. Authentication and remote synchronization | Local authentication/ownership done; remote sync deferred |
| 8. React frontend | Connected to real data; demo fixtures retired |
| 9. Reliability, performance and security | Baseline tests/limits/safeguards; advanced hardening deferred |
| 10. Deployment and documentation | Release/Docker configuration and this guide; public hosting is a later operation |

The honest portfolio description is "a Java content-addressed VCS with a CLI and authenticated React/Spring workspace." Explain the learning-oriented limits directly. Do not claim Git protocol compatibility, distributed synchronization, enterprise security or atomic crash recovery that has not been implemented.

## Primary references

- Spring Security sessions: https://docs.spring.io/spring-security/reference/servlet/authentication/session-management.html
- Spring Security CSRF: https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html
- Spring MVC Problem Details: https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-ann-rest-exceptions.html
- Spring Boot DevTools: https://docs.spring.io/spring-boot/reference/using/devtools.html
- OpenAPI and Springdoc: https://springdoc.org/faq.html
- Official PostgreSQL container: https://hub.docker.com/_/postgres

Use the source code and migrations as the final authority for this particular app. These references explain the framework behavior; the storage formats and algorithms described in this guide come from this repository's own implementation.
