# Connected frontend

React/TypeScript/Vite runs standalone by default. It opens a local profile and stores actual
browser-created repositories, working files, staging and history in localStorage. No backend,
database, tunnel, account or password is needed. Data is local to each browser/site origin;
Settings provides backup export/import. Clearing site data removes the workspace.

Use `npm ci` and `npm run dev` in this directory to open the standalone frontend.
To opt into authenticated Java mode, set `VITE_CVCS_MODE=server`, start the backend on 8080,
and restart Vite. Vite proxies `/api`; `CVCS_API_TARGET` selects another backend.

For Vercel, select branch `main`, Root Directory `frontend`, and Node 22.x. No backend
variable is required by default. `vercel.mjs` supplies SPA routing. Only explicit server mode
requires `CVCS_BACKEND_ORIGIN`, a reachable HTTPS backend, and secure cookies.
See [the deployment guide](../docs/VERCEL.md). Run `npm run test:deployment` for config checks
and `npm run test:browser` for standalone browser workflow/accessibility tests.

Use `npm run lint`, `npm run typecheck`, `npm run build` and `npm test`. Playwright needs a disposable backend and creates test accounts/repositories. `CVCS_E2E_URL` targets an already-running combined release or development app.

See `../DESIGN.md`, `../UX-CONTRACT.md` and `../docs/PROJECT-WORKFLOW.md` for the visual contract, API behavior and complete learning guide. Use the root release script to package this UI inside Spring Boot. The standalone Vite preview command does not itself host the API.
