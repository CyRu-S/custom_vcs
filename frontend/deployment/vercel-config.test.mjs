import assert from 'node:assert/strict'
import test from 'node:test'
import { createVercelConfig } from './vercel-config.mjs'

test('API proxy takes priority over the SPA fallback and preserves the API path', () => {
  const config = createVercelConfig('https://backend.example.com/')
  assert.deepEqual(config.rewrites, [
    { source: '/api/:path*', destination: 'https://backend.example.com/api/:path*' },
    { source: '/(.*)', destination: '/index.html' },
  ])
  assert.equal(config.outputDirectory, 'dist')
  assert.deepEqual(config.headers[0], {
    source: '/api/:path*',
    headers: [{ key: 'Cache-Control', value: 'private, no-store' }],
  })
})

test('invalid or local deployment targets fail with an actionable error', () => {
  for (const origin of [
    undefined,
    '',
    'invalid',
    'http://backend.example.com',
    'https://localhost:8080',
    'https://127.0.0.1',
    'https://[::1]',
    'https://host.docker.internal',
    'https://backend.example.com/api',
    'https://backend.example.com?key=value',
    'https://user:password@backend.example.com',
    'https://backend.example.com#fragment',
  ]) {
    assert.throws(() => createVercelConfig(origin), /CVCS_BACKEND_ORIGIN/)
  }
})
