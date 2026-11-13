import { createContext, useCallback, useContext, useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'
import { Check, X } from '@phosphor-icons/react'
import type { Repository, Snapshot, Workspace } from './model'
import { api, ApiError, repositoryPath, resetCsrf } from './api'
import type { User } from './api'

type Store = {
  workspace: Workspace
  user: User | null
  authLoading: boolean
  loading: boolean
  busy: boolean
  error: string
  total: number
  notice: (message: string) => void
  authenticate: (
    register: boolean,
    body: { name?: string; email: string; password: string },
  ) => Promise<void>
  signOut: () => Promise<void>
  reload: () => Promise<void>
  loadMore: () => Promise<void>
  refresh: (id: string) => Promise<void>
  createRepository: (name: string, description: string) => Promise<string>
  command: (
    id: string,
    path: string,
    body?: unknown,
    method?: string,
    message?: string,
  ) => Promise<void>
  removeRepository: (id: string) => Promise<void>
  profile: (name: string) => Promise<void>
  loadSnapshot: (id: string, commitId: string) => Promise<Snapshot>
  update: (id: string, change: (repository: Repository) => Repository) => void
  set: (change: (workspace: Workspace) => Workspace) => void
}
const Context = createContext<Store | null>(null)
const empty = (): Workspace => ({
  version: 2,
  name: 'Developer',
  theme: localStorage.getItem('customvcs.theme') === 'dark' ? 'dark' : 'light',
  repositories: [],
})
const failure = (error: unknown) =>
  error instanceof Error ? error.message : 'Something went wrong. Please try again.'

function normalize(value: Repository): Repository {
  return {
    ...value,
    starred: false,
    changes: value.changes.map((change) => ({
      ...change,
      before: change.before ?? undefined,
      after: change.after ?? undefined,
    })),
    merge: value.merge
      ? {
          ...value.merge,
          conflicts: value.merge.conflicts.map((conflict) => ({
            ...conflict,
            ours: conflict.ours ?? undefined,
            theirs: conflict.theirs ?? undefined,
          })),
        }
      : null,
  }
}

export function StoreProvider({ children }: { children: ReactNode }) {
  const [workspace, setWorkspace] = useState<Workspace>(empty)
  const [user, setUser] = useState<User | null>(null)
  const [authLoading, setAuthLoading] = useState(true),
    [loading, setLoading] = useState(false)
  const [busy, setBusy] = useState(false),
    [error, setError] = useState(''),
    [message, setMessage] = useState('')
  const [total, setTotal] = useState(0),
    [page, setPage] = useState(0)
  const activeUser = useRef<string | null>(null),
    mutation = useRef(false)
  activeUser.current = user?.id ?? null

  useEffect(() => {
    // Demo fixtures were explicitly retired. Only presentation preferences remain in the browser.
    localStorage.removeItem('customvcs.studio.v2')
    sessionStorage.removeItem('customvcs.demo-session.v1')
    const controller = new AbortController()
    api<User>('/auth/me', { signal: controller.signal })
      .then(setUser)
      .catch((error) => {
        if (!(error instanceof ApiError && error.status === 401) && error.name !== 'AbortError')
          setError(failure(error))
      })
      .finally(() => {
        if (!controller.signal.aborted) setAuthLoading(false)
      })
    return () => controller.abort()
  }, [])
  useEffect(() => {
    const expired = () => {
      setUser(null)
      setWorkspace((w) => ({ ...w, repositories: [] }))
      setError('Your session expired. Sign in again to continue.')
    }
    window.addEventListener('cvcs:session-expired', expired)
    return () => window.removeEventListener('cvcs:session-expired', expired)
  }, [])
  useEffect(() => {
    document.documentElement.dataset.theme = workspace.theme
    localStorage.setItem('customvcs.theme', workspace.theme)
  }, [workspace.theme])
  useEffect(() => {
    if (!message) return
    const timer = setTimeout(() => setMessage(''), 4200)
    return () => clearTimeout(timer)
  }, [message])

  const loadPage = useCallback(async (number: number, append: boolean, signal?: AbortSignal) => {
    const owner = activeUser.current
    if (!owner) return
    setLoading(true)
    try {
      const result = await api<{ repositories: { id: string }[]; totalElements: number }>(
        '/repositories?page=' + number + '&size=20',
        { signal },
      )
      const repositories = await Promise.all(
        result.repositories.map((item) =>
          api<Repository>(repositoryPath(item.id) + '/workspace', { signal }).then(normalize),
        ),
      )
      if (owner !== activeUser.current || signal?.aborted) return
      const starred: string[] = JSON.parse(localStorage.getItem('customvcs.stars.' + owner) ?? '[]')
      setWorkspace((w) => ({
        ...w,
        repositories: [
          ...(append ? w.repositories : []),
          ...repositories.map((r) => ({ ...r, starred: starred.includes(r.id) })),
        ],
      }))
      setTotal(result.totalElements)
      setPage(number)
      setError('')
    } catch (error) {
      if (
        !(error instanceof DOMException && error.name === 'AbortError') &&
        owner === activeUser.current
      ) {
        setError(failure(error))
        throw error
      }
    } finally {
      if (owner === activeUser.current) setLoading(false)
    }
  }, [])

  useEffect(() => {
    setWorkspace((w) => ({ ...w, name: user?.name ?? 'Developer', repositories: [] }))
    setTotal(0)
    setPage(0)
    if (!user) return
    const controller = new AbortController()
    void loadPage(0, false, controller.signal).catch(() => {})
    return () => controller.abort()
  }, [user?.id, loadPage])

  const refresh = async (id: string) => {
    const owner = activeUser.current
    const repository = normalize(await api<Repository>(repositoryPath(id) + '/workspace'))
    if (owner !== activeUser.current) return
    setWorkspace((w) => ({
      ...w,
      repositories: w.repositories.map((r) =>
        r.id === id ? { ...repository, starred: r.starred } : r,
      ),
    }))
  }
  const mutate = async <T,>(operation: () => Promise<T>): Promise<T> => {
    if (mutation.current) throw new Error('Wait for the current operation to finish.')
    mutation.current = true
    setBusy(true)
    setError('')
    try {
      return await operation()
    } catch (error) {
      setError(failure(error))
      throw error
    } finally {
      mutation.current = false
      setBusy(false)
    }
  }
  const command: Store['command'] = (id, path, body, method = 'POST', message = 'Changes saved.') =>
    mutate(async () => {
      await api(repositoryPath(id) + path, { method, body })
      try {
        await refresh(id)
      } catch {
        throw new Error(
          'The operation was saved, but the view could not refresh. Use Refresh before making more changes.',
        )
      }
      setMessage(message)
    })
  const value: Store = {
    workspace,
    user,
    authLoading,
    loading,
    busy,
    error,
    total,
    notice: setMessage,
    refresh,
    reload: () => loadPage(0, false),
    loadMore: () => loadPage(page + 1, true),
    authenticate: async (register, body) => {
      const user = await api<User>('/auth/' + (register ? 'register' : 'login'), {
        method: 'POST',
        body,
      })
      resetCsrf()
      setError('')
      setUser(user)
    },
    signOut: async () => {
      await api('/auth/logout', { method: 'POST' })
      resetCsrf()
      setUser(null)
      setWorkspace((w) => ({ ...w, repositories: [] }))
    },
    createRepository: (name, description) =>
      mutate(async () => {
        const created = await api<{ id: string }>('/repositories', {
          method: 'POST',
          body: { name, description },
        })
        const repository = normalize(
          await api<Repository>(repositoryPath(created.id) + '/workspace'),
        )
        setWorkspace((w) => ({ ...w, repositories: [repository, ...w.repositories] }))
        setTotal((t) => t + 1)
        setMessage(name + ' is ready.')
        return created.id
      }),
    command,
    removeRepository: (id) =>
      mutate(async () => {
        await api(repositoryPath(id), { method: 'DELETE' })
        setWorkspace((w) => ({ ...w, repositories: w.repositories.filter((r) => r.id !== id) }))
        setTotal((t) => Math.max(0, t - 1))
        setMessage('Repository removed.')
      }),
    profile: async (name) => {
      const updated = await api<User>('/auth/me', { method: 'PATCH', body: { name } })
      setUser(updated)
      setWorkspace((w) => ({ ...w, name: updated.name }))
      setMessage('Profile saved.')
    },
    loadSnapshot: async (id, commitId) => {
      if (!commitId) return {}
      const files = await api<Snapshot>(
        repositoryPath(id) + '/commits/' + encodeURIComponent(commitId) + '/files',
      )
      setWorkspace((w) => ({
        ...w,
        repositories: w.repositories.map((r) =>
          r.id === id
            ? { ...r, commits: r.commits.map((c) => (c.id === commitId ? { ...c, files } : c)) }
            : r,
        ),
      }))
      return files
    },
    // Stars and theme are presentation preferences; this function never changes VCS state.
    update: (id, change) =>
      setWorkspace((w) => {
        const repositories = w.repositories.map((r) =>
          r.id === id ? { ...r, starred: change(r).starred } : r,
        )
        if (user)
          localStorage.setItem(
            'customvcs.stars.' + user.id,
            JSON.stringify(repositories.filter((r) => r.starred).map((r) => r.id)),
          )
        return { ...w, repositories }
      }),
    set: (change) => setWorkspace((w) => ({ ...w, theme: change(w).theme })),
  }
  return (
    <Context.Provider value={value}>
      {children}
      <div className={`toast ${message ? 'visible' : ''}`} role="status">
        {message && (
          <>
            <span className="toast-check">
              <Check size={14} />
            </span>
            {message}
            <button aria-label="Dismiss notification" onClick={() => setMessage('')}>
              <X size={14} />
            </button>
          </>
        )}
      </div>
    </Context.Provider>
  )
}
export function useStore() {
  const value = useContext(Context)
  if (!value) throw new Error('Workspace provider missing')
  return value
}
export function download(content: string, name: string) {
  const url = URL.createObjectURL(
    new Blob([content], { type: name.endsWith('.json') ? 'application/json' : 'text/plain' }),
  )
  const anchor = document.createElement('a')
  anchor.href = url
  anchor.download = name
  anchor.click()
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}
