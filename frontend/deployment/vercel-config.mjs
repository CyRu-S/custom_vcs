export function createVercelConfig(backendOrigin) {
  if (!backendOrigin) {
    throw new Error('Set CVCS_BACKEND_ORIGIN in Vercel to your reachable HTTPS backend origin.')
  }
  let backend
  try {
    backend = new URL(backendOrigin)
  } catch {
    throw new Error('CVCS_BACKEND_ORIGIN must be an absolute HTTPS origin.')
  }
  if (
    backend.protocol !== 'https:' ||
    backend.username ||
    backend.password ||
    backend.pathname !== '/' ||
    backend.search ||
    backend.hash ||
    backend.hostname === 'localhost' ||
    backend.hostname.endsWith('.localhost') ||
    backend.hostname.endsWith('.local') ||
    backend.hostname === '[::1]' ||
    backend.hostname.startsWith('127.') ||
    backend.hostname === 'host.docker.internal'
  ) {
    throw new Error(
      'CVCS_BACKEND_ORIGIN must be a public HTTPS origin with no path or credentials.',
    )
  }

  return {
    framework: 'vite',
    installCommand: 'npm ci',
    buildCommand: 'npm run build',
    outputDirectory: 'dist',
    rewrites: [
      { source: '/api/:path*', destination: `${backend.origin}/api/:path*` },
      { source: '/(.*)', destination: '/index.html' },
    ],
    headers: [
      { source: '/api/:path*', headers: [{ key: 'Cache-Control', value: 'private, no-store' }] },
    ],
  }
}
