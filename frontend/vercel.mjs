import { createVercelConfig } from './deployment/vercel-config.mjs'

// Vercel evaluates this at deployment time. This variable is never bundled into React.
export const config = createVercelConfig(
  process.env.CVCS_BACKEND_ORIGIN,
  process.env.VITE_CVCS_MODE ?? 'browser',
)
