import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import {
  ArrowRight,
  FileCode,
  GitBranch,
  GitCommit,
  MagnifyingGlass,
  Minus,
  Plus,
} from '@phosphor-icons/react'
import { useStore } from './store'
import { basename, relative, short, sortedCommits } from './model'
import type { Repository, Snapshot } from './model'
import { BranchGraph } from './Graph'
import { diff } from './diff'
import { Button, CopyButton, Empty, Tag } from './ui'
import { Syntax } from './Source'

export function DiffViewer({
  before,
  after,
  path,
}: {
  before?: string
  after?: string
  path: string
}) {
  const [split, setSplit] = useState(false)
  const lines = diff(before, after)
  return (
    <div className="diff-viewer">
      <div className="diff-toolbar">
        <span>
          <FileCode size={17} />
          <strong>{basename(path)}</strong>
        </span>
        <span className="diff-stats">
          <b>+{lines.filter((l) => l.kind === 'add').length}</b>
          <em>−{lines.filter((l) => l.kind === 'remove').length}</em>
        </span>
        <div className="segmented small">
          <button
            onClick={() => setSplit(false)}
            aria-pressed={!split}
            className={!split ? 'selected' : ''}
          >
            Unified
          </button>
          <button
            onClick={() => setSplit(true)}
            aria-pressed={split}
            className={split ? 'selected' : ''}
          >
            Split
          </button>
        </div>
      </div>
      <div
        className={`diff-code ${split ? 'split' : ''}`}
        tabIndex={0}
        role="region"
        aria-label={`Changes in ${path}`}
      >
        {split ? (
          <>
            <div>
              <div className="diff-side-label">BEFORE</div>
              {lines
                .filter((l) => l.kind !== 'add')
                .map((l, i) => (
                  <div className={`diff-line ${l.kind}`} key={i}>
                    <span>{l.old}</span>
                    <span>{l.kind === 'remove' ? '−' : ' '}</span>
                    <code>
                      <Syntax line={l.text} />
                    </code>
                  </div>
                ))}
            </div>
            <div>
              <div className="diff-side-label">AFTER</div>
              {lines
                .filter((l) => l.kind !== 'remove')
                .map((l, i) => (
                  <div className={`diff-line ${l.kind}`} key={i}>
                    <span>{l.next}</span>
                    <span>{l.kind === 'add' ? '+' : ' '}</span>
                    <code>
                      <Syntax line={l.text} />
                    </code>
                  </div>
                ))}
            </div>
          </>
        ) : (
          lines.map((l, i) => (
            <div className={`diff-line ${l.kind}`} key={i}>
              <span>{l.old}</span>
              <span>{l.next}</span>
              <span>{l.kind === 'add' ? '+' : l.kind === 'remove' ? '−' : ' '}</span>
              <code>
                <Syntax line={l.text} />
              </code>
            </div>
          ))
        )}
      </div>
      {!lines.length && <div className="diff-no-content">This file is empty.</div>}
    </div>
  )
}
export function Changes({ repository: r }: { repository: Repository }) {
  const { command, busy, user } = useStore()
  const [selection, setSelection] = useState(''),
    [message, setMessage] = useState(''),
    [error, setError] = useState('')
  const staged = r.changes.filter((c) => c.staged),
    unstaged = r.changes.filter((c) => !c.staged)
  const key = (change: (typeof r.changes)[number]) =>
    `${change.staged ? 'index' : 'working'}:${change.path}`
  const selected = r.changes.find((c) => key(c) === selection) ?? r.changes[0]
  const stage = async (paths: string[], value: boolean) => {
    try {
      for (const path of paths)
        await command(
          r.id,
          value ? '/stage' : '/stage/undo',
          { path },
          'POST',
          value ? 'Changes staged.' : 'Changes unstaged.',
        )
      setError('')
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Could not update staging.')
    }
  }
  return (
    <div className="changes-workspace">
      <div className="changes-intro">
        <div>
          <p className="eyebrow">ONE GOOD COMMIT AT A TIME</p>
          <h2>Shape your next chapter.</h2>
          <p>Review what changed. Stage what belongs together.</p>
        </div>
        <Tag tone={r.changes.length ? 'warm' : 'success'}>
          {r.changes.length
            ? `${new Set(r.changes.map((c) => c.path)).size} CHANGED FILES`
            : 'WORKING TREE CLEAN'}
        </Tag>
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {r.merge && (
        <div className="info-box">
          <GitBranch size={22} />
          <p>
            A merge is in progress. Resolve and stage its conflicts before committing.{' '}
            <Link to={`/r/${r.id}/merge`}>Open merge workbench</Link>
          </p>
        </div>
      )}
      {r.changes.length || r.merge ? (
        <div className="review-grid connected-review">
          <aside className="change-files">
            {[
              { title: 'Changes', entries: unstaged, value: true },
              { title: 'Staged', entries: staged, value: false },
            ].map((group) => (
              <section key={group.title}>
                <div className="change-section-heading">
                  <strong>
                    {group.title}
                    <span>{group.entries.length}</span>
                  </strong>
                  <button
                    className="text-button"
                    disabled={busy || !group.entries.length || (!group.value && !!r.merge)}
                    onClick={() =>
                      void stage(
                        group.entries.map((c) => c.path),
                        group.value,
                      )
                    }
                  >
                    {group.value ? 'Stage all' : 'Unstage all'}
                  </button>
                </div>
                {group.entries.map((change) => (
                  <div
                    className={`change-file ${selected && key(selected) === key(change) ? 'selected' : ''}`}
                    key={key(change)}
                  >
                    <button className="change-file-name" onClick={() => setSelection(key(change))}>
                      <span
                        className={`status-letter ${change.before === undefined ? 'added' : change.after === undefined ? 'deleted' : 'modified'}`}
                      >
                        {change.before === undefined ? 'A' : change.after === undefined ? 'D' : 'M'}
                      </span>
                      <span>{change.path}</span>
                    </button>
                    <button
                      className="icon-button"
                      aria-label={`${group.value ? 'Stage' : 'Unstage'} ${change.path}`}
                      disabled={busy || (!group.value && !!r.merge)}
                      onClick={() => void stage([change.path], group.value)}
                    >
                      {group.value ? <Plus size={16} /> : <Minus size={16} />}
                    </button>
                  </div>
                ))}
                {!group.entries.length && (
                  <p className="change-empty">No {group.title.toLowerCase()}.</p>
                )}
              </section>
            ))}
            <form
              className="commit-form"
              noValidate
              onSubmit={async (e) => {
                e.preventDefault()
                if (!message.trim()) {
                  setError('Enter a commit message.')
                  return
                }
                try {
                  await command(
                    r.id,
                    '/commits',
                    { author: user?.name, message: message.trim() },
                    'POST',
                    'Commit saved.',
                  )
                  setMessage('')
                  setError('')
                } catch (error) {
                  setError(error instanceof Error ? error.message : 'Could not create commit.')
                }
              }}
            >
              <label className="field">
                Commit message
                <textarea
                  className="resize-none"
                  style={{ resize: 'none' }}
                  aria-label="Commit message"
                  value={message}
                  maxLength={4000}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder="Describe what changed"
                />
              </label>
              <Button
                type="submit"
                variant="primary"
                disabled={busy || (!staged.length && !r.merge) || !!r.merge?.conflicts.length}
              >
                <GitCommit size={17} />
                {busy ? 'Saving...' : r.merge ? 'Complete merge commit' : 'Commit staged changes'}
              </Button>
              <p className="muted">
                Only the saved index is committed. Later edits stay in your working files.
              </p>
            </form>
          </aside>
          <section className="change-diff">
            {selected ? (
              selected.binary ? (
                <Empty
                  title="Binary file changed"
                  description="The file can be staged, but its bytes cannot be displayed as a text diff."
                />
              ) : (
                <DiffViewer before={selected.before} after={selected.after} path={selected.path} />
              )
            ) : (
              <Empty title="Resolution staged" description="Create the merge commit to finish." />
            )}
          </section>
        </div>
      ) : (
        <Empty
          title="Everything is committed"
          description="Edit or create a file to start your next change."
        >
          <Link className="button primary" to={`/r/${r.id}/code`}>
            Open source
            <ArrowRight size={16} />
          </Link>
        </Empty>
      )}
    </div>
  )
}

export function History({ repository: r }: { repository: Repository }) {
  const { loadSnapshot } = useStore()
  const [params, setParams] = useSearchParams(),
    [query, setQuery] = useState('')
  const [files, setFiles] = useState<Snapshot>({}),
    [before, setBefore] = useState<Snapshot>({})
  const [loading, setLoading] = useState(false),
    [error, setError] = useState(''),
    [retry, setRetry] = useState(0),
    [file, setFile] = useState('')
  const all = sortedCommits(r),
    selected = all.find((c) => c.id === params.get('commit')) ?? all[0]
  useEffect(() => {
    if (!selected) return
    let current = true
    setLoading(true)
    setError('')
    Promise.all([
      loadSnapshot(r.id, selected.id),
      selected.parents[0]
        ? loadSnapshot(r.id, selected.parents[0])
        : Promise.resolve({} as Snapshot),
    ])
      .then(([files, before]) => {
        if (current) {
          setFiles(files)
          setBefore(before)
          setFile('')
        }
      })
      .catch((error) => {
        if (current)
          setError(error instanceof Error ? error.message : 'Could not load this snapshot.')
      })
      .finally(() => {
        if (current) setLoading(false)
      })
    return () => {
      current = false
    }
  }, [r.id, selected?.id, retry])
  const entries = all.filter((c) =>
    `${c.message} ${c.author} ${c.id}`.toLowerCase().includes(query.toLowerCase()),
  )
  const changed = [...new Set([...Object.keys(files), ...Object.keys(before)])].filter(
    (path) => files[path] !== before[path],
  )
  const path = changed.includes(file) ? file : changed[0]
  return (
    <div className="history-workspace">
      <div className="workspace-section-title">
        <div>
          <h2>Every change has a story.</h2>
          <p>Real commit ancestry across branches. Showing up to {r.historyLimit} commits.</p>
        </div>
        <Tag tone="blue">{r.commits.length} commits</Tag>
      </div>
      {r.commits.length ? (
        <>
          <BranchGraph repository={r} large />
          <div className="history-toolbar">
            <label className="search-input">
              <MagnifyingGlass size={16} />
              <input
                aria-label="Search commits"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
                placeholder="Search message, author or ID"
              />
              {query && (
                <button aria-label="Clear commit search" onClick={() => setQuery('')}>
                  Clear
                </button>
              )}
            </label>
          </div>
          <div className="history-layout">
            <div className="commit-list">
              {entries.map((commit) => (
                <button
                  key={commit.id}
                  className={`commit-row ${commit.id === selected?.id ? 'selected' : ''}`}
                  onClick={() => setParams({ commit: commit.id })}
                >
                  <GitCommit size={20} />
                  <div>
                    <strong>{commit.message}</strong>
                    <span>
                      {commit.author} / {relative(commit.date)}
                    </span>
                  </div>
                  <code>{short(commit.id)}</code>
                </button>
              ))}
              {!entries.length && (
                <Empty title="No commits match" description="Try another message, author or ID." />
              )}
            </div>
            <section className="commit-detail">
              {selected && (
                <>
                  <div className="commit-detail-heading">
                    <Tag tone="blue">COMMIT</Tag>
                    <h3>{selected.message}</h3>
                    <p>
                      {selected.author} / {new Date(selected.date).toLocaleString()}
                    </p>
                    <CopyButton value={selected.id} label="Copy commit ID" />
                  </div>
                  <div className="commit-parents">
                    {selected.parents.map((parent) => (
                      <button
                        className="text-button"
                        key={parent}
                        onClick={() => setParams({ commit: parent })}
                      >
                        Parent {short(parent)}
                      </button>
                    ))}
                  </div>
                  {loading ? (
                    <p role="status" className="api-status">
                      Loading saved snapshot...
                    </p>
                  ) : error ? (
                    <div role="alert" className="api-status">
                      {error}
                      <Button onClick={() => setRetry((v) => v + 1)}>Retry</Button>
                    </div>
                  ) : (
                    <>
                      <p className="muted">
                        Text changes compared with the first parent. Binary files are not included
                        in this text view.
                      </p>
                      <div className="snapshot-files">
                        {changed.map((path) => (
                          <button
                            className={`text-button ${path === file ? 'selected' : ''}`}
                            key={path}
                            onClick={() => setFile(path)}
                          >
                            {path}
                          </button>
                        ))}
                      </div>
                      {path ? (
                        <DiffViewer path={path} before={before[path]} after={files[path]} />
                      ) : (
                        <Empty
                          title="No text differences"
                          description="This snapshot has no changed text files relative to its first parent."
                        />
                      )}
                    </>
                  )}
                </>
              )}
            </section>
          </div>
        </>
      ) : (
        <Empty
          title="Your first chapter starts here"
          description="Create a file, stage it, and make your first commit."
        >
          <Link className="button primary" to={`/r/${r.id}/code`}>
            Open source
          </Link>
        </Empty>
      )}
    </div>
  )
}
