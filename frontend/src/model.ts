export type Snapshot = Record<string, string>
export type Commit = {
  id: string
  message: string
  date: string
  author: string
  parents: string[]
  files: Snapshot
  branch: string
}
export type Change = {
  path: string
  before?: string
  after?: string
  staged: boolean
  binary?: boolean
}
export type Conflict = {
  path: string
  ours?: string
  theirs?: string
  resolution?: string
  draft?: string
  resolved: boolean
}
export type Merge = { source: string; tip: string; files: Snapshot; conflicts: Conflict[] }
export type Repository = {
  id: string
  name: string
  description: string
  language: string
  color: string
  starred: boolean
  current: string
  branches: Record<string, string>
  commits: Commit[]
  changes: Change[]
  merge: Merge | null
  files: Snapshot
  unreadableFiles: string[]
  historyLimit: number
}
export type Workspace = {
  version: 2
  name: string
  theme: 'light' | 'dark'
  repositories: Repository[]
}

export const uid = () =>
  [...crypto.getRandomValues(new Uint8Array(16))]
    .map((v) => v.toString(16).padStart(2, '0'))
    .join('')
export const tip = (r: Repository) => r.commits.find((c) => c.id === r.branches[r.current])
export const snapshot = (r: Repository): Snapshot => r.files
export const sortedCommits = (r: Repository) =>
  [...r.commits].sort((a, b) => b.date.localeCompare(a.date))
export const short = (id: string) => (id ? id.slice(0, 7) : 'No commit yet')
export const basename = (path: string) => path.split('/').at(-1) ?? path
export function relative(date: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - new Date(date).getTime()) / 60000))
  return minutes < 1
    ? 'just now'
    : minutes < 60
      ? `${minutes}m ago`
      : minutes < 1440
        ? `${Math.floor(minutes / 60)}h ago`
        : `${Math.floor(minutes / 1440)}d ago`
}
export function reachable(r: Repository, start = r.branches[r.current]) {
  const visited = new Set<string>()
  const queue = [start]
  while (queue.length) {
    const id = queue.pop()!
    if (visited.has(id)) continue
    visited.add(id)
    queue.push(...(r.commits.find((c) => c.id === id)?.parents ?? []))
  }
  return visited
}
