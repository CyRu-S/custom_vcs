import { browserMode } from './runtime'
import { browserApi } from './browser-api'

export type User = { id: string; name: string; email: string }
type Problem = { detail?: string; errors?: Record<string, string> }

export class ApiError extends Error {
  status: number
  fields: Record<string, string>
  constructor(status: number, message: string, fields: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fields = fields
  }
}

let csrf: { token: string; headerName: string } | null = null
let csrfRequest: Promise<void> | null = null
export function resetCsrf() {
  csrf = null
  csrfRequest = null
}

async function csrfToken() {
  if (csrf) return csrf
  if (!csrfRequest) {
    csrfRequest = fetch('/api/auth/csrf', { credentials: 'include' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Could not verify this session. Refresh and try again.')
        csrf = await response.json()
      })
      .finally(() => {
        csrfRequest = null
      })
  }
  await csrfRequest
  return csrf!
}

// Cookies carry authentication. JavaScript never stores a password or an authentication token.
export async function api<T>(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  if (browserMode) return browserApi<T>(path, options)
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const token = await csrfToken()
    headers[token.headerName] = token.token
  }
  let response: Response
  try {
    response = await fetch('/api' + path, {
      method,
      headers,
      credentials: 'include',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    })
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error
    throw new Error('Cannot reach the server. Check your connection and try again.')
  }
  if (!response.ok) {
    const problem = (await response.json().catch(() => ({}))) as Problem
    if (response.status === 401) {
      // An expired session also invalidates its CSRF token; login needs a fresh one.
      resetCsrf()
      if (!path.startsWith('/auth/')) window.dispatchEvent(new Event('cvcs:session-expired'))
    }
    if (response.status === 403) resetCsrf()
    throw new ApiError(
      response.status,
      problem.detail ?? `Request failed (${response.status}).`,
      problem.errors,
    )
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}

export const repositoryPath = (id: string) => `/repositories/${encodeURIComponent(id)}`
