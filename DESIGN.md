# CustomVCS Studio design context

This is an existing English-language developer workspace. Preserve the current visual
identity; browser storage is a behavior extension, not a redesign.

The canonical runtime tokens live in `frontend/src/style.css`: Public Sans body text,
Outfit headings, JetBrains Mono for source/data, blue actions (`--blue: #315bfa`), light
slate canvas (`--bg: #f6f7fb`), white surfaces and semantic success/warning/danger colors.
Dark mode uses the same stylesheet's theme variables. No token changes are required here.
`studio.css`, `connected.css` and the route components own the existing workspace density,
navigation rail, panels and responsive layout. Reuse them for the new storage labels.

Shared buttons, modals, empty states, tags and repository creation live in `src/ui.tsx`.
Notifications and pending/error state live in `src/store.tsx`; do not duplicate them per screen.
Use the established Phosphor icons. Keep keyboard focus, readable errors and reduced motion.

Browser mode must be identified as local to this browser. Never describe its data as
server-protected or synced. Source snapshots in browser mode are independent of the Java engine.
The optional server mode retains the existing authenticated Java/PostgreSQL behavior.
