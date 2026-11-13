import type { Commit, Conflict, Repository, Snapshot } from './model'
import type { User } from './api'
import { reachable, uid } from './model'

export const BROWSER_KEY = 'customvcs.browser.v1'
type LocalRepository = Repository & {
  index: Record<string, string | null>
  mergeOriginalConflicts?: Conflict[]
}
type Database = { version: 1; user: User; repositories: LocalRepository[] }
const fresh = (): Database => ({
  version: 1,
  user: { id: 'browser', name: 'Developer', email: 'Saved on this browser' },
  repositories: [],
})

function read(): { db: Database; original: string | null } {
  let original: string | null
  try {
    original = localStorage.getItem(BROWSER_KEY)
  } catch {
    throw new Error('Browser storage is unavailable. Allow site storage and reload.')
  }
  if (!original) return { db: fresh(), original }
  try {
    const db = JSON.parse(original) as Database
    validateDatabase(db)
    return { db, original }
  } catch {
    throw new Error(
      'This browser workspace could not be read. Export its stored data before clearing site storage.',
    )
  }
}

function save(db: Database, original: string | null) {
  try {
    if (localStorage.getItem(BROWSER_KEY) !== original)
      throw new Error('Another tab changed this workspace. Refresh and try again.')
    localStorage.setItem(BROWSER_KEY, JSON.stringify(db))
  } catch (error) {
    if (error instanceof Error && error.message.startsWith('Another tab')) throw error
    throw new Error(
      'Browser storage is full or unavailable. Export your workspace and free space before saving again.',
    )
  }
}

export function exportBrowserWorkspace() {
  return JSON.stringify(read().db, null, 2)
}

export function importBrowserWorkspace(text: string) {
  const incoming = JSON.parse(text) as Database
  validateDatabase(incoming)
  const { db, original } = read()
  if (incoming.repositories.some((r) => db.repositories.some((current) => current.id === r.id)))
    throw new Error(
      'This export contains a repository already in your browser. Remove that repository first to restore its backup.',
    )
  db.repositories.push(...incoming.repositories)
  save(db, original)
}

function validateDatabase(db: Database) {
  if (
    !db ||
    db.version !== 1 ||
    !db.user ||
    ['id', 'name', 'email'].some(
      (key) => typeof (db.user as unknown as Record<string, unknown>)[key] !== 'string',
    ) ||
    !Array.isArray(db.repositories)
  )
    throw new Error('Choose a valid CustomVCS browser workspace export.')
  const ids = new Set<string>()
  for (const r of db.repositories) {
    if (
      !r ||
      ['id', 'name', 'description', 'language', 'color', 'current'].some(
        (key) => typeof (r as unknown as Record<string, unknown>)[key] !== 'string',
      ) ||
      !r.branches ||
      Array.isArray(r.branches) ||
      !r.files ||
      !r.index ||
      !Array.isArray(r.commits) ||
      !Object.hasOwn(r.branches, r.current) ||
      ids.has(r.id)
    )
      throw new Error('Invalid repository in browser workspace.')
    ids.add(r.id)
    for (const [name, tip] of Object.entries(r.branches)) {
      if (
        !/^[a-zA-Z0-9][a-zA-Z0-9._/-]*$/.test(name) ||
        ['__proto__', 'constructor', 'prototype'].includes(name) ||
        typeof tip !== 'string' ||
        (tip && !r.commits.some((c) => c.id === tip))
      )
        throw new Error('Invalid branch in browser workspace.')
    }
    for (const c of r.commits) {
      if (
        !c ||
        ['id', 'message', 'author', 'date', 'branch'].some(
          (key) => typeof (c as unknown as Record<string, unknown>)[key] !== 'string',
        ) ||
        !Array.isArray(c.parents) ||
        c.parents.some(
          (id) => typeof id !== 'string' || !r.commits.some((parent) => parent.id === id),
        )
      )
        throw new Error('Invalid history in browser workspace.')
    }
    for (const files of [r.files, r.index, ...r.commits.map((c) => c.files)]) {
      if (!files || typeof files !== 'object' || Array.isArray(files))
        throw new Error('Invalid snapshot in browser workspace.')
      for (const [path, content] of Object.entries(files)) {
        filePath(path)
        if (typeof content !== 'string' && !(files === r.index && content === null))
          throw new Error('Invalid file content in browser workspace.')
      }
    }
    if (
      r.merge &&
      (typeof r.merge.source !== 'string' ||
        typeof r.merge.tip !== 'string' ||
        !r.merge.files ||
        !Array.isArray(r.merge.conflicts))
    )
      throw new Error('Invalid merge state in browser workspace.')
  }
}

function filePath(path: string) {
  if (
    !path ||
    path.startsWith('/') ||
    path.includes('\\') ||
    path.includes('\0') ||
    path
      .split('/')
      .some(
        (p) =>
          !p || ['.', '..', '.git', '.cvcs', '__proto__', 'constructor', 'prototype'].includes(p),
      )
  )
    throw new Error('Use a relative file path without empty, parent, or reserved segments.')
}
const head = (r: LocalRepository): Snapshot =>
  r.commits.find((c) => c.id === r.branches[r.current])?.files ?? {}
function indexed(r: LocalRepository): Snapshot {
  const files = { ...head(r) }
  for (const [path, value] of Object.entries(r.index)) {
    if (value === null) delete files[path]
    else files[path] = value
  }
  return files
}
function changes(r: LocalRepository) {
  const before = head(r),
    staged = indexed(r)
  return [...new Set([...Object.keys(before), ...Object.keys(staged), ...Object.keys(r.files)])]
    .sort()
    .flatMap((path) => [
      ...(before[path] !== staged[path]
        ? [{ path, before: before[path], after: staged[path], staged: true }]
        : []),
      ...(staged[path] !== r.files[path]
        ? [{ path, before: staged[path], after: r.files[path], staged: false }]
        : []),
    ])
}
function view(r: LocalRepository): Repository {
  return {
    id: r.id,
    name: r.name,
    description: r.description,
    language: r.language,
    color: r.color,
    starred: r.starred,
    current: r.current,
    branches: r.branches,
    commits: r.commits,
    changes: changes(r),
    merge: r.merge,
    files: r.files,
    unreadableFiles: [],
    historyLimit: r.commits.length,
  }
}
function clean(r: LocalRepository) {
  if (r.merge || changes(r).length)
    throw new Error(
      'Commit your changes and finish any merge before switching or merging branches.',
    )
}
function commit(r: LocalRepository, user: User, message: string) {
  if (!message?.trim()) throw new Error('Enter a commit message.')
  if (r.merge?.conflicts.length)
    throw new Error('Resolve and stage every conflict before committing.')
  if (!r.merge && !changes(r).some((c) => c.staged))
    throw new Error('Stage at least one change before committing.')
  const previous = r.branches[r.current]
  const saved: Commit = {
    id: uid() + uid(),
    message: message.trim(),
    author: user.name,
    date: new Date().toISOString(),
    branch: r.current,
    parents: [...new Set([previous, r.merge?.tip].filter((id): id is string => !!id))],
    files: indexed(r),
  }
  r.commits.unshift(saved)
  r.branches[r.current] = saved.id
  r.index = {}
  r.merge = null
  delete r.mergeOriginalConflicts
  return saved
}

function merge(r: LocalRepository, source: string, user: User, message: string) {
  clean(r)
  if (!Object.hasOwn(r.branches, source) || source === r.current)
    throw new Error('Choose another existing branch.')
  const target = r.branches[source],
    current = r.branches[r.current]
  if (target === current || !target || reachable(r, current).has(target)) return
  const theirs = r.commits.find((c) => c.id === target)!.files
  if (!current || reachable(r, target).has(current)) {
    r.branches[r.current] = target
    r.files = { ...theirs }
    return
  }
  const ours = head(r)
  const ancestors = reachable(r, target)
  const queue = [current],
    visited = new Set<string>()
  let base: Snapshot = {}
  while (queue.length) {
    const id = queue.shift()!
    if (visited.has(id)) continue
    visited.add(id)
    const c = r.commits.find((c) => c.id === id)
    if (ancestors.has(id)) {
      base = c?.files ?? {}
      break
    }
    queue.push(...(c?.parents ?? []))
  }
  r.merge = { source, tip: target, files: { ...ours }, conflicts: [] }
  r.mergeOriginalConflicts = r.merge.conflicts
  for (const path of new Set([
    ...Object.keys(base),
    ...Object.keys(ours),
    ...Object.keys(theirs),
  ])) {
    if (ours[path] === theirs[path] || theirs[path] === base[path]) continue
    if (ours[path] === base[path]) {
      if (theirs[path] === undefined) delete r.files[path]
      else r.files[path] = theirs[path]
      r.index[path] = theirs[path] ?? null
    } else {
      r.merge.conflicts.push({ path, ours: ours[path], theirs: theirs[path], resolved: false })
      r.files[path] =
        `<<<<<<< ${r.current}\n${ours[path] ?? ''}\n=======\n${theirs[path] ?? ''}\n>>>>>>> ${source}\n`
    }
  }
  if (!r.merge.conflicts.length) commit(r, user, message || `Merge ${source} into ${r.current}`)
}

// Same API contract as the UI store, implemented with browser snapshots instead of HTTP.
export async function browserApi<T>(
  path: string,
  options: { method?: string; body?: unknown; signal?: AbortSignal } = {},
): Promise<T> {
  if (options.signal?.aborted) throw new DOMException('Request cancelled', 'AbortError')
  const { db, original } = read()
  const method = options.method ?? 'GET'
  const url = new URL(path, 'https://browser.invalid')
  const body = (options.body ?? {}) as Record<string, string>
  let result: unknown
  if (url.pathname === '/auth/me') {
    if (method === 'PATCH') {
      if (!body.name?.trim()) throw new Error('Enter a display name.')
      db.user.name = body.name.trim().slice(0, 80)
    }
    result = db.user
  } else if (url.pathname === '/auth/logout') return undefined as T
  else if (url.pathname === '/repositories') {
    if (method === 'POST') {
      if (!body.name?.trim() || body.name.trim().length > 64)
        throw new Error('Use a repository name between 1 and 64 characters.')
      if (db.repositories.some((r) => r.name.toLowerCase() === body.name.trim().toLowerCase()))
        throw new Error('A repository already uses this name.')
      const r: LocalRepository = {
        id: uid(),
        name: body.name.trim(),
        description: body.description ?? '',
        language: 'Text',
        color: 'blue',
        starred: false,
        current: 'main',
        branches: { main: '' },
        commits: [],
        changes: [],
        merge: null,
        files: {},
        index: {},
        unreadableFiles: [],
        historyLimit: 0,
      }
      db.repositories.unshift(r)
      result = view(r)
    } else {
      const page = Math.max(0, Number(url.searchParams.get('page')) || 0)
      const size = Math.min(100, Math.max(1, Number(url.searchParams.get('size')) || 20))
      result = {
        repositories: db.repositories.slice(page * size, (page + 1) * size).map(view),
        totalElements: db.repositories.length,
      }
    }
  } else {
    const match = /^\/repositories\/([^/]+)(.*)$/.exec(url.pathname)
    const r = db.repositories.find((r) => r.id === match?.[1])
    if (!r || !match)
      throw new Error(
        'This repository is not saved in this browser. Open your repositories to continue.',
      )
    const operation = match[2]
    if (operation === '/workspace') result = view(r)
    else if (!operation && method === 'DELETE')
      db.repositories = db.repositories.filter((item) => item.id !== r.id)
    else if (!operation && method === 'PATCH') {
      if (!body.name?.trim() || body.name.trim().length > 64)
        throw new Error('Use a repository name between 1 and 64 characters.')
      if (
        db.repositories.some(
          (item) => item.id !== r.id && item.name.toLowerCase() === body.name.trim().toLowerCase(),
        )
      )
        throw new Error('A repository already uses this name.')
      r.name = body.name.trim()
      r.description = body.description ?? ''
    } else if (operation === '/files/content' && method === 'PUT') {
      filePath(body.path)
      if (
        typeof body.content !== 'string' ||
        new TextEncoder().encode(body.content).length > 1048576
      )
        throw new Error('Use a text file no larger than 1 MiB.')
      if (
        Object.keys(r.files).some(
          (p) =>
            p !== body.path && (p.startsWith(body.path + '/') || body.path.startsWith(p + '/')),
        )
      )
        throw new Error('A file already occupies part of this path.')
      r.files[body.path] = body.content
    } else if (operation === '/files' && method === 'DELETE') {
      const path = url.searchParams.get('path') ?? ''
      filePath(path)
      if (!Object.hasOwn(r.files, path)) throw new Error('This file has already been removed.')
      delete r.files[path]
    } else if (operation === '/stage' || operation === '/stage/undo') {
      filePath(body.path)
      if (operation.endsWith('/undo')) {
        delete r.index[body.path]
        const original = r.mergeOriginalConflicts?.find((c) => c.path === body.path)
        if (r.merge && original && !r.merge.conflicts.some((c) => c.path === body.path))
          r.merge.conflicts.push(original)
      } else {
        if (
          !Object.hasOwn(r.files, body.path) &&
          !Object.hasOwn(head(r), body.path) &&
          !Object.hasOwn(r.index, body.path)
        )
          throw new Error('This file does not exist.')
        if (
          r.merge?.conflicts.some((c) => c.path === body.path) &&
          /^(<<<<<<<|=======|>>>>>>>)/m.test(r.files[body.path] ?? '')
        )
          throw new Error('Remove conflict markers before staging.')
        r.index[body.path] = r.files[body.path] ?? null
        if (r.merge) r.merge.conflicts = r.merge.conflicts.filter((c) => c.path !== body.path)
      }
    } else if (operation === '/commits' && method === 'POST')
      result = commit(r, db.user, body.message)
    else if (operation === '/commits' && method === 'GET')
      result = r.commits.map((c) => ({ id: c.id, commit: c }))
    else if (/^\/commits\/[^/]+\/files$/.test(operation)) {
      const c = r.commits.find((c) => c.id === operation.split('/')[2])
      if (!c) throw new Error('This commit is not available in browser history.')
      result = c.files
    } else if (operation === '/branches' && method === 'POST') {
      if (
        !body.name ||
        !/^[a-zA-Z0-9][a-zA-Z0-9._/-]*$/.test(body.name) ||
        body.name.includes('..') ||
        ['__proto__', 'constructor', 'prototype'].includes(body.name)
      )
        throw new Error('Use a valid branch name.')
      if (Object.hasOwn(r.branches, body.name)) throw new Error('This branch already exists.')
      r.branches[body.name] = r.branches[r.current]
    } else if (operation === '/branches' && method === 'DELETE') {
      const name = url.searchParams.get('name') ?? ''
      if (!Object.hasOwn(r.branches, name) || name === r.current)
        throw new Error('Choose an existing branch other than the current branch.')
      if (r.merge) throw new Error('Finish the current merge before deleting branches.')
      if (r.branches[name] && !reachable(r).has(r.branches[name]))
        throw new Error('Merge this branch before deleting it.')
      delete r.branches[name]
    } else if (operation === '/checkout') {
      clean(r)
      if (!Object.hasOwn(r.branches, body.name)) throw new Error('This branch does not exist.')
      r.current = body.name
      r.files = { ...head(r) }
    } else if (operation === '/merge/abort') {
      if (!r.merge) throw new Error('There is no merge to abort.')
      r.files = { ...r.merge.files }
      r.index = {}
      r.merge = null
      delete r.mergeOriginalConflicts
    } else if (operation === '/merge') merge(r, body.sourceBranch, db.user, body.message)
    else throw new Error('This operation is not supported in browser mode.')
  }
  if (method !== 'GET' && method !== 'HEAD') save(db, original)
  return structuredClone(result) as T
}
