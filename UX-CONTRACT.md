# Workspace behavior

- Existing route components remain canonical for repositories, source, changes, history,
  branches, merge resolution and settings. Storage is selected centrally by `src/runtime.ts`
  and `src/api.ts`; screen components do not own an alternative repository engine.
- Browser mode is the default static deployment. A local profile opens without a sign-in
  form. No password or authentication token is stored. Shared-browser access is disclosed.
- `src/browser-api.ts` owns browser snapshots, staging, commits, branch pointers and
  conservative file-level three-way merging. Conflicting whole-file changes require explicit
  resolution. Checkout cannot discard uncommitted changes; branch deletion rejects unmerged work.
- Saves persist before success feedback. Storage/quota and corrupt-data errors are explicit;
  failed writes leave the previously saved workspace intact. Other tabs trigger refreshes.
- Browser backup exports include all repositories and their staged/history state. Import adds
  repositories without overwriting existing IDs. Removal requires the existing typed confirmation.
  Site data clearing and separate browser profiles/origins do not retain/share this workspace.
- `src/ui.tsx` owns forms, buttons, modal focus and empty states; `src/store.tsx` owns
  pending, errors, toast feedback and CRUD refresh. Existing native selects are maintained.
- Server mode is explicit (`VITE_CVCS_MODE=server`) and continues using session cookies and
  CSRF with no browser fallback after server errors. Docker server builds set this mode.
- Verify create/edit/stage/commit/history/reload/delete, branches/merge resolution, export/import,
  storage failure, keyboard navigation and a narrow viewport with browser regression tests.
