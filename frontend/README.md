# Connected frontend

React/TypeScript/Vite now calls the authenticated Spring Boot API. Demo repository fixtures and browser demo sessions are retired. Local storage holds only theme and per-user stars; source drafts are scoped in memory. Repositories, history and authentication come from the server.

Use `npm ci` and `npm run dev` in this directory after starting the backend on port 8080. Vite proxies `/api`; set `CVCS_API_TARGET` to use another backend. Register an account to start with an empty workspace.

For Vercel, select branch `main`, Root Directory `frontend`, Node 22.x, and set
`CVCS_BACKEND_ORIGIN` to your backend's publicly reachable HTTPS origin (without `/api`).
`vercel.mjs` generates the API proxy and SPA fallback. Your backend can run locally through
an HTTPS tunnel, using local PostgreSQL. Enable `CVCS_SECURE_COOKIES=true` on that backend.
See [the Vercel deployment guide](../docs/VERCEL.md) for setup and Docker alternatives.
Run `npm run test:deployment` to check routing and origin validation.

Use `npm run lint`, `npm run typecheck`, `npm run build` and `npm test`. Playwright needs a disposable backend and creates test accounts/repositories. `CVCS_E2E_URL` targets an already-running combined release or development app.

See `../DESIGN.md`, `../UX-CONTRACT.md` and `../docs/PROJECT-WORKFLOW.md` for the visual contract, API behavior and complete learning guide. Use the root release script to package this UI inside Spring Boot. The standalone Vite preview command does not itself host the API.
