# Start here

The project combines a VCS engine, a backend and a frontend. You can learn those separately. Start with one backend request; the detailed PDF is a reference for later.

## The whole app in four lines

```text
React screen -> Controller -> Service -> Java VCS engine
                                |
                         PostgreSQL metadata
```

The controller accepts an HTTP request. The service checks access and asks the engine to do the work. The engine stores version history in files. PostgreSQL stores accounts and repository names/owners, not file snapshots.

## Start with just two files

- `vcs-server/src/main/java/com/vcs/customvcs/controller/RepositoryVcsController.java`
- `vcs-server/src/main/java/com/vcs/customvcs/service/RepositoryVcsService.java`

They contain status, stage, unstage, commit and history in the same order. Previously these were spread across six controller/service files. Each operation now has one short route method and one service method to follow.

Read **only `stage` first**:

1. The client sends `POST /api/repositories/{id}/stage` with `{"path":"hello.txt"}`.
2. The controller validates the body and calls `vcs.stage(id, request.path())`.
3. The service opens the owned repository and validates the path.
4. It loads the existing index, stages that file and saves the index.
5. The controller returns 204: success with no body.

`RepositoryAccess` handles the ownership/path checks. You can understand what those checks guarantee before reading each Windows filesystem rule.

## Keep these three states straight

| State | Meaning |
|---|---|
| Working file | What you are editing now |
| Index / staging area | The exact version selected for the next commit |
| HEAD snapshot | The last committed version on this branch |

**Save edits -> stage -> commit.** If you edit again after staging, the commit still saves the staged version. Unstage changes the selection; it does not erase your working edits.

The local CLI is installed as `vcs`: run `vcs init`, `vcs add hello.txt`, `vcs status`, and `vcs commit --author "Your Name" -m "Initial commit"` inside your own working folder. The CLI calls the same engine directly and needs no backend. Restart VS Code once after installation so its terminals see the command.

## Read the next pieces when ready

| Question | Code to follow |
|---|---|
| How do accounts/repository names reach SQL? | RepositoryController -> RepositoryService -> RepositoryMetadataRepository |
| What does staging do internally? | RepositoryVcsService.stage -> core StagingArea.stagePath |
| What does commit save? | RepositoryVcsService.commit -> core CommitService.createCommit |
| How does login work? | AuthController -> AuthService, then SecurityConfig |
| How do branches and merges work? | RepositoryOperationsController -> RepositoryOperationsService, then the core methods they call |
| How does React receive one complete screen? | WorkspaceController -> WorkspaceService |

You can leave object encoding, merge algorithms, deployment scripts and the frontend store until you reach those questions. They support the same basic flow; they are not prerequisites for understanding a Spring controller/service request.

Read the short `//` comments for project decisions. Use [the detailed workflow](PROJECT-WORKFLOW.md) or PDF when you want implementation details. You need to explain the flow and its state changes, not reproduce every file from memory.
