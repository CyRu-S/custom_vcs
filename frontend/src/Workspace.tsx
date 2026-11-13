import { useState } from 'react'
import { Link } from 'react-router-dom'
import {
  DownloadSimple,
  ArrowRight,
  ArrowUpRight,
  BookOpen,
  Check,
  Code,
  FolderSimple,
  GitBranch,
  GitCommit,
  Lightning,
  MagnifyingGlass,
  Plus,
  SquaresFour,
  Star,
  TerminalWindow,
  X,
} from '@phosphor-icons/react'
import { useStore, download } from './store'
import { relative, sortedCommits } from './model'
import type { Repository } from './model'
import { BranchGraph } from './Graph'
import { Button, Empty, RepoIcon, SectionTitle, Tag } from './ui'
import { browserMode } from './runtime'
import { exportBrowserWorkspace, importBrowserWorkspace } from './browser-api'

export function Dashboard({ onCreate }: { onCreate: () => void }) {
  const { workspace } = useStore()
  const r = workspace.repositories.find((r) => r.starred) ?? workspace.repositories[0]
  const date = new Date().toLocaleDateString('en', {
    weekday: 'long',
    month: 'long',
    day: 'numeric',
  })
  return (
    <div className="page dashboard">
      <div className="welcome">
        <div>
          <p className="eyebrow">
            <span className="mini-orbit" />{' '}
            {browserMode ? 'YOUR WORKSPACE, ON THIS BROWSER' : 'YOUR WORKSPACE, IN SYNC'}
          </p>
          <h1>
            Good to see you, <span>{workspace.name.split(' ')[0]}.</span>
          </h1>
          <p>Big ideas start with a small commit. Pick up where you left off.</p>
        </div>
        <div className="welcome-right">
          <span>{date}</span>
          <Button variant="primary" onClick={onCreate}>
            <Plus size={18} />
            New repository
          </Button>
        </div>
      </div>
      {r ? (
        <div className="dashboard-top">
          <section className="panel flow-panel">
            <div className="flow-heading">
              <div className="flow-repo">
                <RepoIcon small color={r.color} />
                <div>
                  <span className="eyebrow">CONTINUE BUILDING</span>
                  <Link to={`/r/${r.id}/code`}>
                    {r.name}
                    <ArrowUpRight size={17} />
                  </Link>
                </div>
              </div>
              <Link className="quiet-link" to={`/r/${r.id}/history`}>
                Explore history
                <ArrowRight size={16} />
              </Link>
            </div>
            <BranchGraph repository={r} />
            <div className="flow-footer">
              <span>
                <GitBranch size={15} />
                {Object.keys(r.branches).length} branches
              </span>
              <span>
                <GitCommit size={17} />
                {r.commits.length} commits
              </span>
              <Link to={`/r/${r.id}/code`}>
                Open repository
                <ArrowRight size={16} />
              </Link>
            </div>
          </section>
          <section className="next-panel">
            <div className="next-header">
              <span className="next-icon">
                <Lightning size={22} weight="fill" />
              </span>
              <Tag tone={r.changes.length ? 'warm' : 'success'}>
                {r.changes.length ? 'IN PROGRESS' : 'ALL CLEAR'}
              </Tag>
            </div>
            <h2>
              {r.changes.length
                ? 'A little progress.\nReady to commit.'
                : 'A clean slate.\nWhat’s next?'}
            </h2>
            <p>
              {r.changes.length
                ? `${r.changes.length} files have changed in ${r.name}. Give your work a place in history.`
                : 'Your working tree is clean. Make a new branch and try something.'}
            </p>
            <div className="next-files">
              {r.changes.slice(0, 3).map((c) => (
                <div key={`${c.path}-${c.staged}`}>
                  <span
                    className={`status-letter ${c.before === undefined ? 'added' : 'modified'}`}
                  >
                    {c.before === undefined ? 'A' : 'M'}
                  </span>
                  <span>{c.path.split('/').at(-1)}</span>
                  <span className="mono">{c.after?.split('\n').length ?? 0} ln</span>
                </div>
              ))}
              {!r.changes.length && (
                <div>
                  <Check size={18} />
                  <span>Everything is committed.</span>
                </div>
              )}
            </div>
            <Link
              className="button primary next-cta"
              to={`/r/${r.id}/${r.changes.length ? 'changes' : 'branches'}`}
            >
              {r.changes.length ? 'Review changes' : 'Explore branches'}
              <ArrowRight size={18} />
            </Link>
            <span className="next-note">
              <i />
              {browserMode
                ? 'Saved on this browser. Export a backup in Settings.'
                : 'Private to your account. Saved on the server.'}
            </span>
          </section>
        </div>
      ) : (
        <Empty
          title="Your next project belongs here"
          headingLevel={2}
          description="Create your first repository to start building."
        >
          <Button variant="primary" onClick={onCreate}>
            Create repository
          </Button>
        </Empty>
      )}
      <section className="projects-section">
        <SectionTitle title="Your repositories">
          <Link className="quiet-link" to="/repositories">
            View all <span className="count">{workspace.repositories.length}</span>
            <ArrowRight size={16} />
          </Link>
        </SectionTitle>
        <div className="project-grid">
          {workspace.repositories.slice(0, 3).map((repo) => (
            <RepositoryCard repository={repo} key={repo.id} />
          ))}
          <button className="new-project-card" onClick={onCreate}>
            <span>
              <Plus size={23} />
            </span>
            <strong>Something new?</strong>
            <p>Give your next idea a home.</p>
            <ArrowUpRight className="new-project-arrow" size={20} />
          </button>
        </div>
      </section>
      <div className="dashboard-bottom">
        <section className="panel activity-panel">
          <SectionTitle title="The latest chapter">
            <Link className="quiet-link" to="/activity">
              All activity
              <ArrowRight size={15} />
            </Link>
          </SectionTitle>
          <ActivityList limit={3} />
        </section>
        <section className="field-notes">
          <span className="eyebrow">
            <BookOpen size={15} /> FIELD NOTES
          </span>
          <h2>
            A branch is just
            <br />a new possibility.
          </h2>
          <p>
            Explore without losing your place. Every branch keeps its own story, connected to where
            it began.
          </p>
          <Link to="/guide">
            Get to know CustomVCS
            <ArrowUpRight size={18} />
          </Link>
          <div className="notes-branches" aria-hidden="true">
            <span />
            <span />
            <span />
            <i />
            <i />
            <i />
          </div>
        </section>
      </div>
    </div>
  )
}
export function RepositoryCard({ repository: r }: { repository: Repository }) {
  const { update } = useStore()
  return (
    <article className="project-card">
      <div className="project-top">
        <RepoIcon color={r.color} />
        <button
          className={`icon-button star-button ${r.starred ? 'starred' : ''}`}
          onClick={() => update(r.id, (r) => ({ ...r, starred: !r.starred }))}
          aria-label={`${r.starred ? 'Unstar' : 'Star'} ${r.name}`}
        >
          <Star size={19} weight={r.starred ? 'fill' : 'regular'} />
        </button>
      </div>
      <Link className="project-name" to={`/r/${r.id}/code`}>
        {r.name}
        <ArrowUpRight size={17} />
      </Link>
      <p>{r.description || 'A new idea, ready to take shape.'}</p>
      <div className="project-meta">
        <span>
          <i className={`language-dot ${r.color}`} />
          {r.language}
        </span>
        <span>
          <GitBranch size={14} />
          {Object.keys(r.branches).length}
        </span>
        <span>{relative(sortedCommits(r)[0]?.date ?? new Date().toISOString())}</span>
      </div>
      {r.changes.length > 0 && (
        <span
          className="project-dirty"
          role="img"
          title={`${r.changes.length} uncommitted changes`}
          aria-label={`${r.changes.length} uncommitted changes`}
        />
      )}
    </article>
  )
}
export function Repositories({ onCreate }: { onCreate: () => void }) {
  const { workspace, total, loadMore, loading } = useStore()
  const [query, setQuery] = useState(''),
    [filter, setFilter] = useState('all'),
    [view, setView] = useState('grid')
  const repos = workspace.repositories.filter(
    (r) =>
      r.name.toLowerCase().includes(query.toLowerCase()) && (filter !== 'starred' || r.starred),
  )
  return (
    <div className="page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">A HOME FOR EVERY IDEA</p>
          <h1>
            Repositories<span className="heading-count">{workspace.repositories.length}</span>
          </h1>
          <p>Your projects. Each with a story of its own.</p>
        </div>
        <Button variant="primary" onClick={onCreate}>
          <Plus size={18} />
          New repository
        </Button>
      </div>
      <div className="collection-toolbar">
        <div className="segmented" aria-label="Filter repositories">
          <button className={filter === 'all' ? 'selected' : ''} onClick={() => setFilter('all')}>
            All repositories
          </button>
          <button
            className={filter === 'starred' ? 'selected' : ''}
            onClick={() => setFilter('starred')}
          >
            <Star size={15} />
            Starred
          </button>
        </div>
        <div className="collection-right">
          <label className="search-input">
            <MagnifyingGlass size={18} />
            <input
              aria-label="Find a repository"
              placeholder="Find a repository..."
              value={query}
              onChange={(e) => setQuery(e.target.value)}
            />
            {query && (
              <button aria-label="Clear repository search" onClick={() => setQuery('')}>
                <X size={15} />
              </button>
            )}
          </label>
          <div className="view-switch">
            <button
              aria-label="Grid view"
              aria-pressed={view === 'grid'}
              onClick={() => setView('grid')}
            >
              <SquaresFour size={19} />
            </button>
            <button
              aria-label="List view"
              aria-pressed={view === 'list'}
              onClick={() => setView('list')}
            >
              <FolderSimple size={19} />
            </button>
          </div>
        </div>
      </div>
      <div className={`repository-collection ${view}`}>
        {repos.map((r) => (
          <RepositoryCard key={r.id} repository={r} />
        ))}
      </div>
      {!repos.length && (
        <Empty
          title="No repositories here yet"
          description={
            query
              ? 'Try a different search or clear the filters.'
              : 'Create a repository, or star one to keep it close.'
          }
        >
          <Button
            onClick={() => {
              setQuery('')
              setFilter('all')
            }}
          >
            Clear filters
          </Button>
        </Empty>
      )}
      <div className="collection-caption">
        <span>
          <Check size={15} />
          Your workspace is up to date.
        </span>
        <span>{repos.length} loaded repositories</span>
      </div>
      {workspace.repositories.length < total && (
        <Button disabled={loading} onClick={() => void loadMore().catch(() => {})}>
          {loading ? 'Loading...' : 'Load more repositories'}
        </Button>
      )}
    </div>
  )
}
function ActivityList({ limit }: { limit?: number }) {
  const { workspace } = useStore()
  const entries = workspace.repositories
    .flatMap((r) => r.commits.map((c) => ({ r, c })))
    .sort((a, b) => b.c.date.localeCompare(a.c.date))
  return (
    <div className="activity-list">
      {entries.slice(0, limit).map(({ r, c }) => (
        <Link key={c.id} to={`/r/${r.id}/history?commit=${c.id}`} className="activity-entry">
          <span className={`activity-symbol ${r.color}`}>
            <GitCommit size={21} />
          </span>
          <div>
            <strong>{c.message}</strong>
            <span>
              <b>{r.name}</b>
              <span className="activity-dot">·</span>
              {c.author} on <span className="mono">{c.branch}</span>
            </span>
          </div>
          <span className="activity-time">{relative(c.date)}</span>
          <ArrowUpRight size={16} />
        </Link>
      ))}
      {!entries.length && (
        <Empty title="A story waiting to start" description="Your commits will appear here." />
      )}
    </div>
  )
}
export function Activity() {
  return (
    <div className="page narrow">
      <div className="page-heading">
        <div>
          <p className="eyebrow">EVERY STEP FORWARD</p>
          <h1>Activity</h1>
          <p>A connected view of what you’ve been building.</p>
        </div>
        <Tag tone="blue">Across all repositories</Tag>
      </div>
      <section className="panel">
        <div className="list-heading">
          <span>Commit</span>
          <span>Most recent first</span>
        </div>
        <ActivityList />
      </section>
    </div>
  )
}
export function Settings() {
  const { workspace, user, set, profile, busy } = useStore()
  const [name, setName] = useState(workspace.name),
    [error, setError] = useState(''),
    [saving, setSaving] = useState(false)
  const [backupError, setBackupError] = useState('')
  const { reload, notice } = useStore()
  return (
    <div className="page settings-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">MAKE YOURSELF AT HOME</p>
          <h1>Workspace settings</h1>
          <p>Your account, your preferences, your work.</p>
        </div>
      </div>
      <div className="settings-layout">
        <nav aria-label="Settings sections">
          <a href="#profile" className="selected">
            Your profile
          </a>
          <a href="#appearance">Appearance</a>
          <a href="#data">Workspace data</a>
        </nav>
        <div>
          <section className="settings-block" id="profile">
            <div>
              <h2>Your profile</h2>
              <p>
                {browserMode
                  ? 'Your display name is used for new browser commits.'
                  : 'Your verified account identity is used for new commits.'}
              </p>
            </div>
            <form
              noValidate
              onSubmit={async (e) => {
                e.preventDefault()
                if (!name.trim()) {
                  setError('Enter a display name.')
                  return
                }
                setSaving(true)
                try {
                  await profile(name.trim())
                  setError('')
                } catch (error) {
                  setError(error instanceof Error ? error.message : 'Could not save profile.')
                } finally {
                  setSaving(false)
                }
              }}
            >
              <div className="profile-preview">
                <span>{name.slice(0, 2).toUpperCase()}</span>
                <div>
                  <strong>{name || 'Developer'}</strong>
                  <p>{user?.email}</p>
                </div>
              </div>
              <label className="field">
                Display name
                <input
                  value={name}
                  maxLength={80}
                  onChange={(e) => setName(e.target.value)}
                  aria-invalid={!!error}
                />
              </label>
              {error && (
                <p className="form-error" role="alert">
                  {error}
                </p>
              )}
              <Button variant="primary" type="submit" disabled={saving || busy}>
                {saving ? 'Saving...' : 'Save profile'}
              </Button>
            </form>
          </section>
          <section className="settings-block" id="appearance">
            <div>
              <h2>A different light</h2>
              <p>Choose the theme that feels right for your workflow.</p>
            </div>
            <div className="theme-options">
              {(['light', 'dark'] as const).map((theme) => (
                <button
                  className={`theme-card ${theme} ${workspace.theme === theme ? 'selected' : ''}`}
                  key={theme}
                  aria-pressed={workspace.theme === theme}
                  onClick={() => set((w) => ({ ...w, theme }))}
                >
                  <span className="theme-drawing">
                    <i />
                    <b />
                    <b />
                    <b />
                  </span>
                  <span>
                    {theme === 'light' ? 'Daylight' : 'After hours'}
                    {workspace.theme === theme && <Check size={17} />}
                  </span>
                </button>
              ))}
            </div>
          </section>
          <section className="settings-block" id="data">
            <div>
              <h2>Keep your work close</h2>
              <p>
                {browserMode
                  ? 'Files, staged changes and commit history are saved in this browser. Clearing site data removes them. Keep an exported backup.'
                  : 'Repositories are stored by the Java server. Theme and starred preferences stay in this browser.'}
              </p>
            </div>
            <div className="data-actions">
              <div>
                <h3>{browserMode ? 'Export workspace backup' : 'Export current view'}</h3>
                <p>
                  {browserMode
                    ? 'Download all browser repositories, files, staging and history as JSON.'
                    : 'Download the loaded text view as JSON. This is not a complete repository backup.'}
                </p>
              </div>
              <Button
                onClick={() => {
                  try {
                    download(
                      browserMode ? exportBrowserWorkspace() : JSON.stringify(workspace, null, 2),
                      browserMode ? 'customvcs-browser-backup.json' : 'customvcs-view.json',
                    )
                    setBackupError('')
                  } catch (error) {
                    setBackupError(
                      error instanceof Error ? error.message : 'Could not export workspace.',
                    )
                  }
                }}
              >
                <DownloadSimple size={17} />
                {browserMode ? 'Export backup' : 'Export view'}
              </Button>
            </div>
            {browserMode && (
              <div className="data-actions">
                <div>
                  <h3>Restore repositories</h3>
                  <p>
                    Import a browser backup. Existing repositories are kept; duplicate IDs are
                    refused.
                  </p>
                  <label className="field">
                    Import browser workspace backup
                    <input
                      type="file"
                      accept="application/json,.json"
                      onChange={async (event) => {
                        const file = event.target.files?.[0]
                        event.target.value = ''
                        if (!file) return
                        try {
                          if (file.size > 10 * 1024 * 1024)
                            throw new Error('Choose a backup smaller than 10 MiB.')
                          importBrowserWorkspace(await file.text())
                          await reload()
                          setBackupError('')
                          notice('Browser repositories restored.')
                        } catch (error) {
                          setBackupError(
                            error instanceof Error ? error.message : 'Could not import workspace.',
                          )
                        }
                      }}
                    />
                  </label>
                </div>
              </div>
            )}
            {backupError && (
              <p className="form-error" role="alert">
                {backupError}
              </p>
            )}
            <p className="muted">
              {browserMode
                ? 'Browser storage is limited and belongs to this site and browser profile. Data is not synced, encrypted by this app, or sent to a database.'
                : "For a complete backup, copy the server's repository data directory together with a PostgreSQL database backup while the server is stopped."}
            </p>
          </section>
        </div>
      </div>
    </div>
  )
}
export function Guide() {
  const { workspace } = useStore()
  const base = workspace.repositories[0] ? `/r/${workspace.repositories[0].id}` : null
  return (
    <div className="page guide-page">
      <div className="page-heading">
        <div>
          <p className="eyebrow">A FEW GOOD THINGS TO KNOW</p>
          <h1>Your code, connected.</h1>
          <p>A small guide to the ideas behind CustomVCS.</p>
        </div>
        <span className="guide-symbol">
          <GitBranch size={44} weight="light" />
        </span>
      </div>
      <div className="guide-grid">
        {[
          {
            icon: FolderSimple,
            title: 'Start with a repository',
            body: 'A repository gives your project a home. Its working tree holds the files you are editing; its metadata keeps the history.',
            link: '/repositories',
            cta: 'Explore repositories',
          },
          {
            icon: GitCommit,
            title: 'Make a meaningful commit',
            body: 'Stage the changes that belong together. A commit saves that snapshot with a message, so you can find your way back later.',
            link: base ? `${base}/changes` : '/repositories',
            cta: 'Review working changes',
          },
          {
            icon: GitBranch,
            title: 'Follow a new branch',
            body: 'Branches are pointers to commits. Create one to explore a new idea. Checkout restores the snapshot at its tip.',
            link: base ? `${base}/branches` : '/repositories',
            cta: 'Explore branches',
          },
          {
            icon: Code,
            title: 'Bring stories together',
            body: 'A three-way merge compares two branches with their common ancestor. When both change the same file, you choose the result.',
            link: base ? `${base}/merge` : '/repositories',
            cta: 'Open the merge workbench',
          },
        ].map((item) => (
          <article className="guide-card" key={item.title}>
            <item.icon size={29} />
            <h2>{item.title}</h2>
            <p>{item.body}</p>
            <Link to={item.link}>
              {item.cta}
              <ArrowUpRight size={17} />
            </Link>
          </article>
        ))}
      </div>
      <section className="architecture">
        <span className="eyebrow">BUILT FROM FIRST PRINCIPLES</span>
        <h2>One engine. Every interface.</h2>
        <div>
          <span>
            <TerminalWindow size={24} />
            <strong>vcs-cli</strong>
            <small>Picocli · local commands</small>
          </span>
          <ArrowRight size={24} />
          <span className="core">
            <GitBranch size={26} />
            <strong>vcs-core</strong>
            <small>Pure Java · SHA-256 objects</small>
          </span>
          <ArrowLeftIcon />
          <span>
            <Code size={24} />
            <strong>vcs-server</strong>
            <small>Spring Boot · REST API</small>
          </span>
        </div>
        <p>
          {browserMode
            ? 'Browser mode uses local snapshots and does not connect to the Java server. The optional server mode sends authenticated requests'
            : 'The CLI and server both reuse the Java engine. This browser sends authenticated requests'}
          to Spring Boot; no VCS operation is simulated in the browser.
        </p>
      </section>
    </div>
  )
}
function ArrowLeftIcon() {
  return <ArrowRight className="architecture-back" size={24} />
}
