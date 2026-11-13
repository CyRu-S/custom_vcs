# Vercel frontend with your local PostgreSQL database

The connection is:

`Browser -> Vercel /api proxy -> public HTTPS backend tunnel -> local Spring Boot -> local PostgreSQL`

Vercel hosts the frontend. Your database and repository files stay on your computer.
The computer, database, backend and tunnel must stay running whenever you use the site.
Vercel cannot reach localhost on your computer. Tunnel only the authenticated backend API,
not PostgreSQL port 5432.

## Start the backend

Keep actual credentials in the root `.env`, which is excluded from Git and Docker builds.
Confirm `CVCS_DB_URL` points to your existing local database and `CVCS_STORAGE_ROOT`
points to the repository files associated with it. From the project root:

```powershell
mvn install -DskipTests
$env:CVCS_SECURE_COOKIES = 'true'
mvn -pl vcs-server spring-boot:run
```

Expose `http://localhost:8080` through an HTTPS tunnel, such as Cloudflare Tunnel or ngrok.
Keep the tunnel running. Use a stable hostname where possible; if it changes, update
Vercel and redeploy. Enable `CVCS_SECURE_COOKIES=true` for this public HTTPS backend.
For plain HTTP local development, use `false` and a separate browser session.

## Deploy the frontend

1. Import GitHub repository `CyRu-S/custom_vcs` into Vercel.
2. Select production branch **main**, **Root Directory** `frontend`, and Node.js **22.x**.
3. Set `CVCS_BACKEND_ORIGIN` in Vercel's Environment Variables for each environment you use
   to the public HTTPS backend origin, for example `https://your-backend.example.com`.
   Do not append `/api`. Never put database passwords in Vercel or `VITE_*` variables.
4. Deploy. The config uses Vite, `npm ci`, `npm run build`, and output directory `dist`.
5. Refresh a bookmarked route, then register/login, create a repository, edit a file and
   commit. Confirm the same records are available through the local backend.

`frontend/vercel.mjs` generates an API proxy before the SPA fallback at deployment time.
The build rejects a missing target or localhost target. All browser API calls remain on
the Vercel origin, preserving session cookies and CSRF headers. API responses are marked
`private, no-store`. No permissive CORS or third-party cookie setting is required.

## Docker backend connected to existing host PostgreSQL

In the root `.env`, set `CVCS_DOCKER_DB_URL` to
`jdbc:postgresql://host.docker.internal:5432/<your-existing-database>`. Set the real
`CVCS_DB_USERNAME` and `CVCS_DB_PASSWORD`. Set `CVCS_DOCKER_STORAGE_ROOT` to the matching
repository storage directory (default `./data/repositories`).

PostgreSQL must accept connections from Docker's host interface. Configure its listen
address and pg_hba.conf for the required Docker network/user; keep SQL off the public internet.

```powershell
$env:CVCS_SECURE_COOKIES = 'true'
docker compose -f compose.local-db.yaml up --build -d app
docker compose -f compose.local-db.yaml logs -f app
```

Tunnel `http://localhost:8080` and use the Vercel steps above. This Compose file does not
start or migrate a second database. It uses the existing local database and repository files.

For frontend and backend both in Docker, using plain HTTP on your computer:

```powershell
$env:CVCS_SECURE_COOKIES = 'false'
docker compose -f compose.local-db.yaml up --build -d
```

Open `http://localhost:3000`. Nginx forwards `/api` to backend service `app:8080` and
serves bookmarked SPA routes. Do not run both Compose stacks at once; both publish 8080.

## Supplied Docker configurations

| File | Purpose | Image |
| --- | --- | --- |
| `Dockerfile` | Combined React + Spring Boot | `arpandutta030705/customvcs:latest` |
| `backend.Dockerfile` | API-only Java 25 backend | `arpandutta030705/customvcs-backend:latest` |
| `frontend/Dockerfile` | React build served by Nginx | `arpandutta030705/customvcs-frontend:latest` |
| `compose.yaml` | Combined app + Docker PostgreSQL | Separate named database volume |
| `compose.local-db.yaml` | Backend/frontend using host PostgreSQL | Existing database on your computer |

The default Compose stack does not automatically use rows from host PostgreSQL.
Building images does not publish them to Docker Hub. Vercel builds from Git and does not
use the frontend Docker image. SQL metadata and repository files must be backed up together.

## Checks

```powershell
docker compose config --quiet
docker compose -f compose.local-db.yaml config --quiet
mvn verify
cd frontend
npm run lint
npm run build
npm run test:deployment
```

Local checks validate configuration and builds. A public tunnel URL and a Vercel project
are still required to verify a live deployment against your local data.
