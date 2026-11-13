# Stage 5: merging and conflict resolution

Stage 5 is implemented in the pure Java engine. The CLI calls the same services
that the Spring Boot backend can reuse in Stage 6. No Git commands implement
these operations.

## What a merge means

A branch is a pointer to a commit. Merging changes the current branch; it does
not switch to the source branch or change the source branch's pointer.

The engine walks both parent links when checking ancestry:

- **Already up to date:** the source tip is already an ancestor of our tip.
- **Fast-forward:** our tip is an ancestor of the source tip. Restore the source
  snapshot and move our branch pointer to that existing commit.
- **Diverged:** neither tip contains the other. Find their best common ancestor,
  merge the two snapshots against that base, and create a commit with two parents.

The first parent is our previous tip; the second parent is the source tip. A
two-parent commit records that both histories have been incorporated, even if
the resulting snapshot happens to equal ours. `log` visits both parents, displays
merge parents, and lists each commit once, with children before ancestors.

Ordinary commits still use commit format version 1. Merge commits use version 2,
which adds the second parent before the author field. The decoder reads both,
so existing repositories and stored object IDs remain usable.

## Three-way decisions

For each repository-relative path, compare the base, ours, and theirs blob IDs.
A missing ID means that the path does not exist in that snapshot.

| Comparison | Result |
| --- | --- |
| Ours equals theirs | Keep that content, or their shared deletion |
| Ours equals base | Take their change, including deletion |
| Theirs equals base | Keep our change, including deletion |
| Both changed a text file | Compare their edits against base lines |
| One changed a file while the other deleted it | Keep surviving content and require resolution |
| Both changed binary content | Keep ours and require resolution |

Text merging reuses the line-diff engine to produce edits in base coordinates.
Nonoverlapping edits are combined. Identical edits are applied once. Competing
overlapping edits produce a conflict; insertions at replacement boundaries are
handled conservatively. Line endings and a missing final newline are preserved
in successful automatic merges.

Text conflicts show the complete versions of the file:

```text
<<<<<<< HEAD
our content
=======
their content
>>>>>>> feature
```

Only strict UTF-8 content without NUL bytes is treated as text. Binary conflicts
do not get marker bytes inserted into them. Both original versions remain
available through the parent snapshots and `cat-object` with their blob IDs.

## CLI workflow

Run commands from a repository root. In PowerShell, set the JAR path once for
these examples; this does not install a global command:

```powershell
$cvcsJar = 'D:\CustomVCS\vcs-cli\target\vcs-cli-0.0.1-SNAPSHOT.jar'
java -jar $cvcsJar checkout main
java -jar $cvcsJar merge feature --author "Your Name" -m "Merge feature"
```

Fast-forward and up-to-date merges do not need an author because they create no
commit. A diverged merge requires `--author`; `-m` is optional and otherwise gets
a default message. A conflict-free merge creates its two-parent commit immediately.

When conflicts occur, `merge` exits with code 1 and leaves the operation pending:

```powershell
java -jar $cvcsJar status
# Edit hello.txt to the final content, removing markers where present.
java -jar $cvcsJar add hello.txt
java -jar $cvcsJar commit --author "Your Name" -m "Resolve feature merge"
java -jar $cvcsJar log
```

Repeat `add` for every conflict path. To choose deletion, delete the file and
`add` its missing path. Staging is the explicit resolution decision: the engine
does not guess from the presence or absence of marker-like text. Review your
result before committing. Use the message on the final `commit`; a message
passed to a merge that pauses is not saved for reuse.

`commit` refuses unresolved paths. Once all are staged, it uses the saved source
tip as the second parent and clears merge state after advancing the branch.
The index remains the next snapshot, as it does for ordinary commits.

To discard the pending merge and its resolutions:

```powershell
java -jar $cvcsJar merge --abort
```

Abort restores the starting committed snapshot and index. It discards edits to
paths managed by this merge, including any partial resolutions. Unrelated files
created after the merge began remain on disk; path collisions can block abort
until you move the blocking contents aside. Abort does not move either branch.

## State and safety boundaries

`.customvcs/MERGE_STATE` stores both original tips, branch names, managed paths,
and unresolved paths. Replacing this file uses a temporary file and an atomic
move where supported. Separate CLI processes can continue the same merge.
Staging marks a conflict resolved only after saving the chosen index entry or
deletion. `status` displays pending merges even if no file differs from the index.

Merging requires a clean index and working tree, with no untracked regular files.
Checkout and another merge are blocked while a merge is pending. The engine
validates snapshot paths, referenced blobs, and destination collisions before
changing working files. Checkout, merge, and abort share the same restoration
code. A conflicted merge leaves our branch tip unchanged until the final commit.

Current limits:

- Renames are represented as deletions and additions; there is no rename detector.
- Unrelated histories and multiple best merge bases (criss-cross history) are
  rejected. The engine does not invent a common base or choose one arbitrarily.
- Combined file/directory or filesystem case collisions are rejected before
  changing working files. Resolve those layouts on a branch before merging.
- Large changed text regions use the diff engine's bounded-memory replacement
  fallback and may produce conservative conflicts.
- Individual file replacement does not make the entire operation transactional.
  Process interruption, disk failures, and concurrent writers still require the
  locking and recovery work planned for Stage 9. A saved merge state is not a
  complete crash-recovery journal.

## Validation

Run `mvn verify` from the project root. Stage 5 tests use temporary repositories
and cover format compatibility, both ancestry outcomes, two-parent history,
automatic text merging, add/add, modify/delete and binary conflicts, resolution,
abort, structural collisions, malformed snapshots, and merge-base ambiguity.
CLI tests launch separate JVM processes to exercise persisted conflict state,
status, resolution, abort, fast-forward, and automatic merge commands.

The next stage is Stage 6: Spring Boot REST endpoints and PostgreSQL integration.
