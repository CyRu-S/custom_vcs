# CustomVCS

A Java 25 version control engine built from scratch, with a local Picocli CLI and a React workspace. The frontend now opens in standalone browser-storage mode by default; authenticated Spring Boot/PostgreSQL mode remains optional. Git does not implement our VCS operations.

**Start with [the short learning guide](docs/LEARNING-GUIDE.md).** For the everyday backend flow, read `RepositoryVcsController` and `RepositoryVcsService` together. Branch/merge operations, security setup and browser aggregation are separate supporting areas you can read afterward.

Implemented in Java: init, add/unstage, status, commits/history, branches, safe checkout, text diff, three-way merge, conflict resolution and abort. Browser mode independently supports file snapshots, staging, commits, branches and conservative file-level merging, with no seeded repositories or backend requests.

- `vcs-core`: framework-free engine and SHA-256 object storage.
- `vcs-cli`: local commands; no server required.
- `vcs-server`: sessions, CSRF, private ownership, REST and PostgreSQL metadata.
- `frontend`: React/TypeScript workspace, file editor, changes, history and merges.

For **Vercel**, follow [the deployment guide](docs/VERCEL.md). Use Root Directory `frontend`;
the default browser mode needs no backend environment variables or tunnel. Data persists only
in that browser and site origin; export a backup from Settings. Set `VITE_CVCS_MODE=server`
only when opting back into Java/API mode. Separate backend and frontend Dockerfiles remain
available. `compose.local-db.yaml` connects Docker to host PostgreSQL; default `compose.yaml`
starts a separate Docker database.

## Start locally

For the standalone frontend: `cd frontend`, `npm ci`, then `npm run dev`. No Java or SQL
server is needed. The instructions below are for optional server mode; set frontend
environment variable `VITE_CVCS_MODE=server` when following them.

Use JDK 25, Maven 3.9.12, Node 22.12+ and PostgreSQL. Create a database named `customvcs`, copy `.env.example` to `.env`, and supply your credentials. Use unquoted Java properties values and forward slashes for paths. Keep `.env` private.

```powershell
mvn install -DskipTests
mvn -pl vcs-server spring-boot:run
```

In a second terminal:

```powershell
cd frontend
npm ci
npm run dev
```

Open the Vite address, normally http://127.0.0.1:5173, and register. The API runs on port 8080. Keep one hostname throughout the session. DevTools restarts after Java changes compile; dependency changes require a manual restart.

## Combined release

Stop Vite before installing frontend dependencies, and stop a running packaged JAR before rebuilding on Windows.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-release.ps1
java -jar vcs-server/target/vcs-server-0.0.1-SNAPSHOT.jar
```

Open http://localhost:8080. This JAR includes the frontend and supports bookmarked browser routes. Swagger is at `/swagger-ui/index.html`: get `/api/auth/csrf`, authorize with its token, register/login, then fetch and authorize a fresh token after authentication.

## CLI

The Windows launcher is `bin/vcs.cmd`. Install it once for your user account:

```powershell
mvn -pl vcs-cli -am package -DskipTests
powershell -ExecutionPolicy Bypass -File scripts/install-cli.ps1
```

Restart VS Code/your terminal once after installation. In a folder you want to track:

```powershell
vcs init
vcs add hello.txt
vcs status
vcs commit --author "Your Name" -m "Initial commit"
vcs log
vcs branch feature
vcs checkout feature
vcs diff
vcs merge main --author "Your Name"
vcs --help
```

The launcher keeps your current directory, forwards arguments to the Java CLI and returns its exit code. `add` accepts one file at a time, including a missing tracked file to stage its deletion. Rebuild the CLI JAR after Java changes; no need to reinstall the command unless you move the project. It works in PowerShell and CMD, independently of server accounts. Do not use it against an actively used server-managed directory: server locking is in-process only.

## Verification and learning

```powershell
mvn verify
cd frontend
npm run lint
npm run typecheck
npm run build
# With a disposable backend running:
npm test
```

Read [the complete workflow guide](docs/PROJECT-WORKFLOW.md), [verification results](docs/VERIFICATION.md), [deployment notes](docs/DEPLOYMENT.md), and [the API guide](docs/STAGE-6.md). The rendered illustrated guide is in `output/pdf/CustomVCS-Project-Guide.pdf`; rebuild it with `python scripts/build_project_guide.py` after installing ReportLab. The script uses Windows Segoe UI/Consolas fonts.

PostgreSQL stores accounts and metadata; `data/repositories` stores working files and complete VCS history. Back up both together. Repositories created before authentication retain null ownership and are preserved but hidden; deliberate adoption instructions are in the workflow guide.

This is a single-instance learning project. Remote push/pull/clone, password recovery/email verification, distributed locking and advanced production hardening remain extensions. Docker configuration is supplied, but public deployment is not performed automatically.
