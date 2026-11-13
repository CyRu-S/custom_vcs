// The static frontend works by itself. Java/API mode remains an explicit option.
export const browserMode = import.meta.env.VITE_CVCS_MODE !== 'server'
