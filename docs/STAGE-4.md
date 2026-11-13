# Stage 4: branches, checkout, and diff

## What to understand

A branch file contains one commit ID. `HEAD` contains a symbolic reference to
the selected branch. Creating a branch copies a pointer; it does not duplicate
the repository's blobs or snapshots. Committing advances only the selected branch.

The engine lives in `vcs-core`. CLI classes parse input and format results.
Spring Boot controllers and the connected React workspace now call these same services.

## Checkout

`CheckoutService.checkout(name)` performs these steps:

1. Require a clean index and working tree, including no untracked regular files.
2. Read the selected and target commits and snapshots.
3. Verify object hashes, object types, snapshot paths, and target blob bytes.
4. Calculate tracked removals and check filesystem collisions.
5. Remove obsolete tracked files and prune only their empty parent directories.
6. Restore target bytes, replacing individual files through temporary files.
7. Replace and save the index with the target snapshot.
8. Update `HEAD` to select the target branch.

Preparation happens before mutation so corrupt objects and predictable path
collisions are discovered early. `WorkingPaths` rejects absolute, noncanonical,
metadata, escaping, and redirected paths. A directory can become a file only
when scheduled tracked removals will empty that directory completely.

The strict clean-tree policy is a deliberate first implementation. Unrelated
untracked regular files also block checkout. Empty directories and links at a
destination receive additional collision checks.

Each target file is written to a temporary sibling and then moved into place.
An atomic move is used where supported. The whole checkout is not a transaction:
disk failures or concurrent processes can still interrupt it between files,
the index update, and the HEAD update. Locks, rollback, atomic metadata updates,
and crash recovery remain Stage 9 work.

## Diff baselines

| Operation | Before | After |
|---|---|---|
| `diff` | Saved index blobs | Current working-file bytes |
| `diff --staged` / `--cached` | Latest commit snapshot, or empty before the first commit | Saved index blobs |

Diff reads state without writing objects, changing the index, or moving references.
Untracked files have no saved baseline and are listed by status instead.

`DiffService` returns `FileDiff` records rather than terminal output. The result
contains the path, added/modified/deleted classification, binary flag, line counts,
and text edits. This keeps comparison results reusable by a REST API.

## Line comparison

`LineDiff` finds a longest common subsequence (LCS) of lines. Shared lines are
context; lines outside that subsequence become removals and additions. A normal
LCS table uses O(n*m) time and memory for the changed region.

Equal prefixes and suffixes are removed from the matrix calculation. The matrix
is capped at four million cells. Larger changed regions use a valid deletion-plus-
addition replacement, which may be nonminimal. Algorithm and large-file performance
improvements belong to Stage 9.

Line tokens retain their endings. Consequently CRLF/LF differences and adding or
removing the final newline remain observable. NUL-containing content or invalid
UTF-8 is classified as binary; it receives a change summary instead of text edits.
Text output uses one full-file, unified-style hunk. Empty-file additions and
deletions still have a file-level result even when no text lines exist.

## Scope

Branch names currently contain one simple component. Checkout targets existing
branches only, with no detached HEAD or force option. Operations currently run
from the repository root. Files are loaded into memory, and file permission bits,
executable modes, and symbolic-link contents are not part of snapshots.

## Validation

Run `mvn verify` from the project root. Stage 3 regression tests remain enabled.
Stage 4 tests cover branch references, dirty-state rejection, bidirectional
restoration, file/directory and case-only transitions, corrupt and malformed
targets, collision protection, independent diff baselines, binary handling,
newline preservation, and edit reconstruction. A CLI test launches real JVM
processes in a temporary repository to exercise command registration and output.

Tests use temporary repositories and do not change the learning sandbox.
