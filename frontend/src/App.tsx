import { Component, Suspense, lazy, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { Link, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowDown,
  ArrowUp,
  ArrowUpRight,
  BookOpen,
  CaretDown,
  FolderSimple,
  GearSix,
  GitBranch,
  MagnifyingGlass,
  Moon,
  Plus,
  SquaresFour,
  Sun,
  TerminalWindow,
  Waveform,
  SignOut,
  UserCircle,
} from '@phosphor-icons/react'
import { useStore } from './store'
import { Button, CreateRepository, Modal } from './ui'
import { Dashboard, Repositories, Activity, Settings, Guide } from './Workspace'
import { EntryGate, useSession } from './Entry'
const Studio = lazy(() => import('./Studio').then((module) => ({ default: module.Studio })))

class Boundary extends Component<{ children: ReactNode }, { failed: boolean }> {
  state = { failed: false }
  static getDerivedStateFromError() {
    return { failed: true }
  }
  render() {
    return this.state.failed ? (
      <div className="recovery">
        <h1>This view could not be opened.</h1>
        <p>Your saved work remains on the server.</p>
        <Button onClick={() => location.assign('/')}>Return to workspace</Button>
      </div>
    ) : (
      this.props.children
    )
  }
}
export default function App() {
  return (
    <Boundary>
      <EntryGate>
        <Shell />
      </EntryGate>
    </Boundary>
  )
}
function Shell() {
  const { workspace, set, loading, error: loadError, reload, notice } = useStore(),
    location = useLocation()
  const [create, setCreate] = useState(false),
    [search, setSearch] = useState(false),
    [help, setHelp] = useState(false)
  const [account, setAccount] = useState(false)
  const { signOut } = useSession()
  const active = workspace.repositories.find((r) => location.pathname.startsWith(`/r/${r.id}/`))
  useEffect(() => {
    const onKey = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setSearch((v) => !v)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])
  useEffect(() => {
    document.title = `${active ? active.name + ' · ' : ''}CustomVCS Studio`
    window.scrollTo(0, 0)
  }, [location.pathname, active?.name])
  return (
    <div className="app-shell">
      <a className="skip" href="#main">
        Skip to content
      </a>
      <aside className="rail">
        <Link to="/" className="brand-mark" aria-label="CustomVCS home">
          <GitBranch weight="bold" size={29} />
        </Link>
        <div className="rail-divider" />
        <nav aria-label="Primary navigation">
          <NavLink to="/" end title="Workspace" aria-label="Home">
            <SquaresFour size={24} />
            <span>Home</span>
          </NavLink>
          <NavLink to="/repositories" title="Repositories" aria-label="Repos">
            <FolderSimple size={24} />
            <span>Repos</span>
          </NavLink>
          <NavLink to="/activity" title="Activity" aria-label="Activity">
            <Waveform size={24} />
            <span>Activity</span>
          </NavLink>
        </nav>
        <div className="rail-repos">
          {workspace.repositories.slice(0, 3).map((r) => (
            <Link
              to={`/r/${r.id}/code`}
              key={r.id}
              title={r.name}
              aria-label={`Open ${r.name}`}
              className={`rail-repo ${r.color} ${active?.id === r.id ? 'active' : ''}`}
            >
              {r.name.slice(0, 1).toUpperCase()}
            </Link>
          ))}
          <button className="rail-add" onClick={() => setCreate(true)} aria-label="New repository">
            <Plus size={20} />
          </button>
        </div>
        <div className="rail-bottom">
          <Link to="/guide" aria-label="Documentation" title="Documentation">
            <BookOpen size={22} />
          </Link>
          <NavLink to="/settings" aria-label="Settings" title="Settings">
            <GearSix size={23} />
          </NavLink>
          <button
            className="rail-avatar"
            onClick={() => setHelp(true)}
            aria-label={`${workspace.name.slice(0, 2).toUpperCase()} Workspace information`}
          >
            <span aria-hidden="true">{workspace.name.slice(0, 2).toUpperCase()}</span>
          </button>
        </div>
      </aside>
      <div className="app-body">
        <header className="topbar">
          <div className="wordmark">
            custom<span>vcs</span>
            <span className="studio-label">STUDIO</span>
          </div>
          <span className="topbar-divider" />
          <Link className="workspace-switch" to="/">
            Personal workspace
            <CaretDown size={12} />
          </Link>
          <div className="topbar-actions">
            <button
              className="search-trigger"
              aria-label="Search anything... Ctrl K"
              onClick={() => setSearch(true)}
            >
              <MagnifyingGlass size={18} />
              <span>Search anything...</span>
              <kbd aria-hidden="true">Ctrl K</kbd>
            </button>
            <button
              className="icon-button theme-toggle"
              aria-label={`Switch to ${workspace.theme === 'light' ? 'dark' : 'light'} theme`}
              onClick={() => set((w) => ({ ...w, theme: w.theme === 'light' ? 'dark' : 'light' }))}
            >
              {workspace.theme === 'light' ? <Moon size={19} /> : <Sun size={19} />}
            </button>
            <span className="demo-indicator">
              <i />
              Connected
            </span>
            <button
              className="topbar-plus"
              onClick={() => setCreate(true)}
              aria-label="Create repository"
            >
              <Plus size={19} />
            </button>
            <button
              className="account-trigger"
              aria-label={`${workspace.name.slice(0, 2).toUpperCase()} Your profile and session`}
              onClick={() => setAccount(true)}
            >
              <span aria-hidden="true">{workspace.name.slice(0, 2).toUpperCase()}</span>
            </button>
          </div>
        </header>
        <main id="main" tabIndex={-1}>
          {loading && (
            <p className="api-status" role="status">
              Refreshing repositories...
            </p>
          )}
          {loadError && (
            <div className="api-status">
              <Button onClick={() => void reload().catch(() => {})}>Retry loading</Button>
            </div>
          )}
          <Routes>
            <Route path="/" element={<Dashboard onCreate={() => setCreate(true)} />} />
            <Route
              path="/repositories"
              element={<Repositories onCreate={() => setCreate(true)} />}
            />
            <Route path="/activity" element={<Activity />} />
            <Route path="/settings" element={<Settings />} />
            <Route path="/guide" element={<Guide />} />
            <Route
              path="/r/:id/*"
              element={
                <Suspense fallback={<div className="empty">Opening repository…</div>}>
                  <Studio />
                </Suspense>
              }
            />
            <Route
              path="*"
              element={
                <div className="recovery">
                  <h1>This path is untracked.</h1>
                  <p>We couldn’t find that page in your workspace.</p>
                  <Link className="button primary" to="/">
                    Back to workspace
                    <ArrowUpRight />
                  </Link>
                </div>
              }
            />
          </Routes>
        </main>
        <footer className="app-footer">
          <span>
            <GitBranch size={13} /> Your code. Its entire story.
          </span>
          <span>
            CustomVCS Studio <b>v2.0</b>
            <i /> Powered by your Java VCS engine
          </span>
        </footer>
      </div>
      {create && <CreateRepository onClose={() => setCreate(false)} />}{' '}
      {search && <Search onClose={() => setSearch(false)} />}{' '}
      {help && (
        <Modal title="Your personal workspace" onClose={() => setHelp(false)}>
          <p className="muted">
            Your private repositories are stored by the Spring Boot server. Only you can access them
            after signing in.
          </p>
          <div className="info-box">
            <TerminalWindow size={24} />
            <p>
              Files, staging, commits, branches and merges are handled by the same Java engine used
              by the CLI.
            </p>
          </div>
          <Link to="/guide" className="button primary" onClick={() => setHelp(false)}>
            Explore the guide
            <ArrowUpRight size={17} />
          </Link>
        </Modal>
      )}
      {account && (
        <Modal title="Your workspace" onClose={() => setAccount(false)}>
          <div className="account-card">
            <span>{workspace.name.slice(0, 2).toUpperCase()}</span>
            <div>
              <strong>{workspace.name}</strong>
              <p>Personal workspace · Demo session</p>
            </div>
          </div>
          <div className="account-actions">
            <Link to="/settings" onClick={() => setAccount(false)}>
              Profile and preferences
              <UserCircle size={19} />
            </Link>
            <Link to="/guide" onClick={() => setAccount(false)}>
              Getting started
              <BookOpen size={18} />
            </Link>
            <button
              className="sign-out"
              onClick={() => void signOut().catch((error) => notice(error.message))}
            >
              Sign out
              <SignOut size={19} />
            </button>
          </div>
          <p className="account-note">
            Signing out ends this session. Your repositories and history stay on the server.
          </p>
        </Modal>
      )}
    </div>
  )
}
function Search({ onClose }: { onClose: () => void }) {
  const { workspace } = useStore(),
    navigate = useNavigate()
  const [query, setQuery] = useState(''),
    [selected, setSelected] = useState(0)
  const pages = [
    { name: 'Workspace overview', path: '/' },
    { name: 'All repositories', path: '/repositories' },
    { name: 'Workspace settings', path: '/settings' },
    { name: 'Documentation', path: '/guide' },
  ]
  const results = [
    ...workspace.repositories.map((r) => ({ name: r.name, path: `/r/${r.id}/code` })),
    ...pages,
    ...workspace.repositories.flatMap((r) =>
      Object.keys(r.files).map((p) => ({
        name: `${r.name} / ${p}`,
        path: `/r/${r.id}/code?file=${encodeURIComponent(p)}`,
      })),
    ),
  ]
    .filter((r) => r.name.toLowerCase().includes(query.toLowerCase()))
    .slice(0, 8)
  const open = (path: string) => {
    navigate(path)
    onClose()
  }
  return (
    <Modal title="Find your next step" onClose={onClose}>
      <div className="command-input">
        <MagnifyingGlass size={22} />
        <input
          autoFocus
          aria-label="Search workspace"
          placeholder="Repositories, files, pages..."
          value={query}
          onChange={(e) => {
            setQuery(e.target.value)
            setSelected(0)
          }}
          onKeyDown={(e) => {
            if (e.key === 'ArrowDown') {
              e.preventDefault()
              setSelected((v) => Math.min(v + 1, results.length - 1))
            }
            if (e.key === 'ArrowUp') {
              e.preventDefault()
              setSelected((v) => Math.max(0, v - 1))
            }
            if (e.key === 'Enter' && results[selected]) open(results[selected].path)
          }}
        />
      </div>
      <p className="eyebrow command-label">{query ? 'SEARCH RESULTS' : 'QUICK ACCESS'}</p>
      <div className="command-results">
        {results.map((r, i) => (
          <button
            className={selected === i ? 'selected' : ''}
            key={r.path}
            onClick={() => open(r.path)}
          >
            <FolderSimple size={18} />
            <span>{r.name}</span>
            <ArrowUpRight size={16} />
          </button>
        ))}
        {!results.length && <p className="muted">No matches. Try a repository name or filename.</p>}
      </div>
      <div className="command-hint">
        <span>
          <ArrowUp />
          <ArrowDown /> to navigate
        </span>
        <span>↵ to open</span>
        <span>esc to close</span>
      </div>
    </Modal>
  )
}
