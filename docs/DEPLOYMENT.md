# Deployment and data

## Local development

For a Vercel frontend using the existing local database, follow [the Vercel guide](VERCEL.md).
It covers the HTTPS backend tunnel and `compose.local-db.yaml` host-database configuration.

Use `.env.example` to create a private `.env` in the project root. Values use Java properties syntax; do not add shell-style quotes. JDK 25, Maven 3.9.12, Node 22.12+ and PostgreSQL are required.

Create database `customvcs`, run `mvn install -DskipTests`, then `mvn -pl vcs-server spring-boot:run`. In another terminal run `npm ci` and `npm run dev` under `frontend`. Keep localhost/127.0.0.1 consistent throughout a browser session.

## Packaged release

Stop Vite before npm ci, and stop a running packaged JAR before rebuilding it on Windows. From the root:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/build-release.ps1
java -jar vcs-server/target/vcs-server-0.0.1-SNAPSHOT.jar
```

The generated JAR serves React and the API at http://localhost:8080. Register an account. The release script copies generated static assets into target/classes; it does not add dist files to Java source. A normal Maven clean removes generated frontend assets, so use the release script again after cleaning.

DevTools is for IDE/Maven development and is not active during normal packaged execution. It restarts only after Java sources compile. In-memory sessions may be lost on restart.

When dependencies are already installed, use `scripts/build-release.ps1 -SkipInstall` to avoid reinstalling native packages while Vite is running. For a fresh dependency installation use the normal script with Vite stopped.

The optional local command installer is `scripts/install-cli.ps1`. It adds `bin` to the current user's PATH, without administrator access or shell-profile changes. Restart the terminal/VS Code afterward. The `vcs` command operates on the caller's working directory and runs without the server.

## Docker

`docker compose up --build -d` builds the combined app and starts PostgreSQL 18. Set a non-placeholder CVCS_DB_PASSWORD in `.env` first. The compose database user is `cvcs`; its internal JDBC address is supplied directly to the app. SQL is not exposed on a host port. The application binds host port 8080 on loopback.

Use `docker compose logs -f app` to inspect startup and `docker compose down` to stop. Do not append `-v` unless deliberately deleting the named data volumes. Changing POSTGRES_PASSWORD after a volume is initialized does not automatically change that cluster's existing SQL password.

PostgreSQL 18 volume: `/var/lib/postgresql`. Repository volume: `/app/data/repositories`. Runtime uses a non-root user. Container execution was not verified here because the Docker daemon was unavailable; compose syntax was checked.

## One server instance

The application serializes file commands with a shared in-process monitor. Run one instance and do not run CLI operations against its live storage. A second instance or CLI process does not share that lock. Use HTTPS and CVCS_SECURE_COOKIES=true for a public endpoint; put it behind a reverse proxy and add throttling, monitoring and recovery procedures before admitting untrusted traffic.

## Backups

Stop writes. Back up PostgreSQL (users, repositories and migration history) and the entire configured repository storage root together. Keep their association intact; SQL rows alone do not contain commits or file bytes. Store backups outside the application data directory. Restore both into disposable storage and confirm account ownership, branch tips, historical content and pending merge state before considering the process reliable.

Existing repositories with null owner_id are deliberately preserved and hidden after V3. Adopt individual repositories only after checking the intended account/repository UUIDs; see PROJECT-WORKFLOW.md. Never assign all old data to the first registered account automatically.
