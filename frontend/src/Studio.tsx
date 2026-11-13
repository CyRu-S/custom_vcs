import { useState } from 'react'
import { Link, NavLink, Navigate, Route, Routes, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  Code,
  GearSix,
  GitBranch,
  GitCommit,
  GitMerge,
  Plus,
  Star,
} from '@phosphor-icons/react'
import { useStore } from './store'
import type { Repository } from './model'
import { Button, Empty, Modal, RepoIcon, Tag } from './ui'
import { CodeWorkspace } from './Source'
import { Changes, History } from './Review'
import { Branches, MergeWorkbench } from './Branches'

export function Studio() {
  const { id } = useParams(),
    { workspace, loading, refresh, update, busy } = useStore()
  const repository = workspace.repositories.find((r) => r.id === id)
  const [create, setCreate] = useState(false)
  if (!repository)
    return (
      <div className="page">
        <Empty
          title={loading ? 'Opening repository...' : 'Repository not found'}
          description="Choose a repository from your workspace."
        >
          <Link className="button primary" to="/repositories">
            Back to repositories
          </Link>
        </Empty>
      </div>
    )
  const r = repository,
    base = `/r/${r.id}`
  const tabs = [
    { name: 'Source', path: 'code', icon: Code },
    { name: 'Changes', path: 'changes', icon: GitCommit, count: r.changes.length },
    { name: 'History', path: 'history', icon: GitCommit },
    { name: 'Branches', path: 'branches', icon: GitBranch },
    { name: 'Merge', path: 'merge', icon: GitMerge },
    { name: 'Settings', path: 'settings', icon: GearSix },
  ]
  return (
    <div className="studio">
      <div className="studio-breadcrumb">
        <Link to="/repositories">
          <ArrowLeft size={14} />
          Repositories
        </Link>
        <span>/</span>
        <span>{r.name}</span>
        <span className="studio-private">
          <i />
          Private repository
        </span>
      </div>
      <div className="studio-heading">
        <div className="studio-identity">
          <RepoIcon color={r.color} />
          <div>
            <h1>
              {r.name}
              <Tag>PERSONAL</Tag>
            </h1>
            <p>{r.description || 'Your code. Its entire story.'}</p>
          </div>
        </div>
        <div className="studio-heading-actions">
          <Button disabled={busy} onClick={() => void refresh(r.id).catch(() => {})}>
            Refresh
          </Button>
          <Button disabled={busy} onClick={() => setCreate(true)}>
            <Plus size={16} />
            New branch
          </Button>
          <button
            className="icon-button"
            aria-label={`${r.starred ? 'Unstar' : 'Star'} ${r.name}`}
            onClick={() => update(r.id, (r) => ({ ...r, starred: !r.starred }))}
          >
            <Star size={20} weight={r.starred ? 'fill' : 'regular'} />
          </button>
        </div>
      </div>
      <div className="studio-navigation">
        <nav aria-label="Repository sections">
          {tabs.map((tab) => (
            <NavLink key={tab.path} to={`${base}/${tab.path}`}>
              <tab.icon size={17} />
              {tab.name}
              {tab.count !== undefined && <span className="count">{tab.count}</span>}
            </NavLink>
          ))}
        </nav>
      </div>
      <Routes>
        <Route path="code" element={<CodeWorkspace repository={r} />} />
        <Route path="changes" element={<Changes repository={r} />} />
        <Route path="history" element={<History repository={r} />} />
        <Route path="branches" element={<Branches repository={r} />} />
        <Route path="merge" element={<MergeWorkbench repository={r} />} />
        <Route path="settings" element={<RepositorySettings repository={r} />} />
        <Route path="*" element={<Navigate to="code" replace />} />
      </Routes>
      {create && <NewBranch repository={r} onClose={() => setCreate(false)} />}
    </div>
  )
}
export function NewBranch({
  repository: r,
  onClose,
}: {
  repository: Repository
  onClose: () => void
}) {
  const { command, busy } = useStore()
  const [name, setName] = useState(''),
    [error, setError] = useState('')
  return (
    <Modal
      title="Make room for an idea"
      onClose={() => {
        if (!busy) onClose()
      }}
    >
      <p className="muted">
        The new branch starts at {r.current}'s latest commit. Create your first commit before
        branching.
      </p>
      <form
        noValidate
        onSubmit={async (event) => {
          event.preventDefault()
          if (busy) return
          if (!/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(name.trim())) {
            setError(
              'Use a flat name such as feature-search, with letters, numbers, dots, hyphens or underscores.',
            )
            return
          }
          try {
            await command(
              r.id,
              '/branches',
              { name: name.trim() },
              'POST',
              `Created ${name.trim()}.`,
            )
            onClose()
          } catch (error) {
            setError(error instanceof Error ? error.message : 'Could not create branch.')
          }
        }}
      >
        <label className="field">
          Branch name
          <input
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="feature-search"
            aria-invalid={!!error}
            aria-describedby={error ? 'branch-error' : undefined}
          />
        </label>
        {error && (
          <p className="form-error" role="alert" id="branch-error">
            {error}
          </p>
        )}
        <div className="modal-footer">
          <Button type="button" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" disabled={busy}>
            {busy ? 'Creating...' : 'Create branch'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
function RepositorySettings({ repository: r }: { repository: Repository }) {
  const { command, busy, removeRepository } = useStore(),
    navigate = useNavigate()
  const [name, setName] = useState(r.name),
    [description, setDescription] = useState(r.description),
    [remove, setRemove] = useState(false),
    [confirmation, setConfirmation] = useState(''),
    [error, setError] = useState('')
  return (
    <div className="repository-settings">
      <section className="settings-block">
        <div>
          <p className="eyebrow">THE BASICS</p>
          <h2>About this repository</h2>
          <p>A clear name and description go a long way.</p>
        </div>
        <form
          noValidate
          onSubmit={async (e) => {
            e.preventDefault()
            try {
              await command(
                r.id,
                '',
                { name: name.trim(), description },
                'PATCH',
                'Repository details saved.',
              )
              setError('')
            } catch (error) {
              setError(error instanceof Error ? error.message : 'Could not save details.')
            }
          }}
        >
          <label className="field">
            Repository name
            <input value={name} maxLength={64} onChange={(e) => setName(e.target.value)} />
          </label>
          <label className="field">
            Description
            <textarea
              className="resize-none"
              style={{ resize: 'none' }}
              value={description}
              maxLength={300}
              onChange={(e) => setDescription(e.target.value)}
            />
          </label>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <Button variant="primary" type="submit" disabled={busy}>
            {busy ? 'Saving...' : 'Save changes'}
          </Button>
        </form>
      </section>
      <section className="settings-block">
        <div>
          <h2>Repository information</h2>
          <p>Metadata lives in PostgreSQL. Files and VCS objects live on the server.</p>
        </div>
        <dl className="info-list">
          <div>
            <dt>Current branch</dt>
            <dd>{r.current}</dd>
          </div>
          <div>
            <dt>Repository ID</dt>
            <dd className="mono">{r.id}</dd>
          </div>
          <div>
            <dt>History view</dt>
            <dd>Up to {r.historyLimit} commits across branches</dd>
          </div>
        </dl>
      </section>
      <section className="settings-block danger-block">
        <div>
          <h2>Remove repository</h2>
          <p>
            Permanently delete this repository's working files, objects, and history from the
            server.
          </p>
        </div>
        <Button variant="danger" onClick={() => setRemove(true)}>
          Remove repository
        </Button>
      </section>
      {remove && (
        <Modal
          title={`Remove ${r.name}?`}
          onClose={() => {
            if (!busy) setRemove(false)
          }}
        >
          <p className="muted">
            This permanently deletes the server repository and its history. This cannot be undone.
          </p>
          <form
            noValidate
            onSubmit={async (e) => {
              e.preventDefault()
              if (confirmation !== r.name) return
              try {
                await removeRepository(r.id)
                navigate('/repositories')
              } catch (error) {
                setError(error instanceof Error ? error.message : 'Could not remove repository.')
              }
            }}
          >
            <label className="field">
              Type {r.name} to confirm
              <input
                autoFocus
                value={confirmation}
                onChange={(e) => setConfirmation(e.target.value)}
              />
            </label>
            {error && (
              <p role="alert" className="form-error">
                {error}
              </p>
            )}
            <div className="modal-footer">
              <Button type="button" disabled={busy} onClick={() => setRemove(false)}>
                Keep repository
              </Button>
              <Button variant="danger" type="submit" disabled={busy || confirmation !== r.name}>
                {busy ? 'Removing...' : 'Remove repository'}
              </Button>
            </div>
          </form>
        </Modal>
      )}
    </div>
  )
}
