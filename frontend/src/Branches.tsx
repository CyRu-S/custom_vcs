import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Check,
  GitBranch,
  GitMerge,
  MagnifyingGlass,
  Plus,
  Trash,
} from '@phosphor-icons/react'
import { useStore } from './store'
import { browserMode } from './runtime'
import { relative, short, tip } from './model'
import type { Repository } from './model'
import { NewBranch } from './Studio'
import { Button, Empty, Modal, Tag } from './ui'

export function Branches({ repository: r }: { repository: Repository }) {
  const { command, busy } = useStore()
  const [create, setCreate] = useState(false),
    [query, setQuery] = useState(''),
    [deleting, setDeleting] = useState(''),
    [error, setError] = useState('')
  const branches = Object.entries(r.branches).filter(([name]) =>
    name.toLowerCase().includes(query.toLowerCase()),
  )
  return (
    <div className="branches-workspace">
      <div className="workspace-section-title">
        <div>
          <h2>Ideas need room to grow.</h2>
          <p>Create an experiment. Commit it. Bring it back when it is ready.</p>
        </div>
        <Button variant="primary" onClick={() => setCreate(true)}>
          <Plus size={17} />
          Create branch
        </Button>
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      <div className="branch-summary">
        <div className="branch-summary-icon">
          <GitBranch size={30} />
        </div>
        <div>
          <span className="eyebrow">YOU ARE HERE</span>
          <h3>{r.current}</h3>
          <span>{tip(r)?.message ?? 'Create your first commit to begin branching.'}</span>
        </div>
        <code>{short(r.branches[r.current])}</code>
        <Tag tone="blue">CURRENT BRANCH</Tag>
      </div>
      <div className="branch-table-toolbar">
        <h3>
          All branches<span className="count">{Object.keys(r.branches).length}</span>
        </h3>
        <label className="search-input">
          <MagnifyingGlass size={16} />
          <input
            aria-label="Search branches"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder="Find a branch"
          />
          {query && (
            <button aria-label="Clear branch search" onClick={() => setQuery('')}>
              Clear
            </button>
          )}
        </label>
      </div>
      <div className="branch-table">
        {branches.map(([name, id]) => {
          const current = name === r.current,
            commit = r.commits.find((c) => c.id === id)
          return (
            <div className={`branch-row ${current ? 'current' : ''}`} key={name}>
              <span className="branch-row-symbol">
                <GitBranch size={22} />
              </span>
              <div className="branch-row-name">
                <strong>
                  {name}
                  {current && (
                    <Tag tone="blue">
                      <Check size={12} />
                      CURRENT
                    </Tag>
                  )}
                </strong>
                <span>
                  {commit?.message ?? 'No commits yet'}
                  {commit && ` ? ${relative(commit.date)}`}
                </span>
              </div>
              <code>{short(id)}</code>
              <div className="branch-row-actions">
                {!current && (
                  <>
                    <Button
                      disabled={busy || !!r.merge}
                      onClick={async () => {
                        try {
                          await command(r.id, '/checkout', { name }, 'POST', `Switched to ${name}.`)
                          setError('')
                        } catch (error) {
                          setError(
                            error instanceof Error ? error.message : 'Could not switch branches.',
                          )
                        }
                      }}
                    >
                      Checkout
                      <ArrowRight size={14} />
                    </Button>
                    <button
                      className="icon-button"
                      disabled={busy || !!r.merge}
                      aria-label={`Delete branch ${name}`}
                      onClick={() => setDeleting(name)}
                    >
                      <Trash size={16} />
                    </button>
                  </>
                )}
              </div>
            </div>
          )
        })}
        {!branches.length && (
          <Empty title="No branches match" description="Try a different name." />
        )}
      </div>
      <div className="branch-bottom-note">
        <GitMerge size={21} />
        <div>
          <strong>Ready to bring two ideas together?</strong>
          <p>
            Checkout requires a clean working tree and index. Unmerged branches cannot be deleted.
          </p>
        </div>
        <Link to={`/r/${r.id}/merge`}>
          Open merge workbench
          <ArrowRight size={17} />
        </Link>
      </div>
      {create && <NewBranch repository={r} onClose={() => setCreate(false)} />}
      {deleting && (
        <Modal
          title={`Delete ${deleting}?`}
          onClose={() => {
            if (!busy) setDeleting('')
          }}
        >
          <p className="muted">
            Remove this branch pointer. Deletion is refused if its work has not been merged into
            your current branch.
          </p>
          {error && (
            <p className="form-error" role="alert">
              {error}
            </p>
          )}
          <div className="modal-footer">
            <Button disabled={busy} onClick={() => setDeleting('')}>
              Keep branch
            </Button>
            <Button
              variant="danger"
              disabled={busy}
              onClick={async () => {
                try {
                  await command(
                    r.id,
                    '/branches?name=' + encodeURIComponent(deleting),
                    undefined,
                    'DELETE',
                    'Branch deleted.',
                  )
                  setDeleting('')
                  setError('')
                } catch (error) {
                  setError(error instanceof Error ? error.message : 'Could not delete branch.')
                }
              }}
            >
              Delete branch
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}

export function MergeWorkbench({ repository: r }: { repository: Repository }) {
  const { command, busy, user } = useStore()
  const [source, setSource] = useState(''),
    [message, setMessage] = useState(''),
    [error, setError] = useState(''),
    [abort, setAbort] = useState(false)
  const [selected, setSelected] = useState(''),
    [draft, setDraft] = useState(''),
    [choice, setChoice] = useState('edit')
  const conflict = r.merge?.conflicts.find((c) => c.path === selected) ?? r.merge?.conflicts[0]
  useEffect(() => {
    if (conflict) {
      setDraft(r.files[conflict.path] ?? '')
      setChoice('edit')
    }
  }, [conflict?.path])
  const alternatives = Object.keys(r.branches).filter((name) => name !== r.current)
  const selectedSource = alternatives.includes(source) ? source : (alternatives[0] ?? '')
  return (
    <div className="merge-workspace">
      <div className="workspace-section-title">
        <div>
          <p className="eyebrow">BRING YOUR WORK TOGETHER</p>
          <h2>{r.merge ? 'A decision worth making.' : 'Two branches. One next chapter.'}</h2>
          <p>
            {r.merge
              ? `Merging ${r.merge.source} into ${r.current}. Resolve each file, then create the merge commit.`
              : browserMode
                ? 'Compare saved file snapshots with their shared ancestor. Files changed on both sides can be resolved here.'
                : 'The Java engine compares both branches with their shared ancestor.'}
          </p>
        </div>
        <GitMerge size={35} />
      </div>
      {error && (
        <p className="form-error" role="alert">
          {error}
        </p>
      )}
      {!r.merge ? (
        <section className="panel merge-setup">
          <form
            noValidate
            onSubmit={async (e) => {
              e.preventDefault()
              if (!selectedSource) {
                setError('Create another branch before merging.')
                return
              }
              try {
                await command(
                  r.id,
                  '/merge',
                  {
                    sourceBranch: selectedSource,
                    author: user?.name,
                    message: message.trim() || null,
                  },
                  'POST',
                  'Merge checked. Review the result below or in history.',
                )
                setError('')
              } catch (error) {
                setError(error instanceof Error ? error.message : 'Could not merge branches.')
              }
            }}
          >
            <label className="field">
              Merge from
              <select
                aria-label="Source branch"
                value={selectedSource}
                onChange={(e) => setSource(e.target.value)}
              >
                <option value="" disabled>
                  Select a branch
                </option>
                {alternatives.map((name) => (
                  <option key={name}>{name}</option>
                ))}
              </select>
            </label>
            <div className="info-box">
              <GitBranch size={22} />
              <p>
                Into <strong>{r.current}</strong>. Commit or restore pending changes before
                starting.
              </p>
            </div>
            <label className="field">
              Merge message <span className="optional">optional</span>
              <input
                value={message}
                maxLength={4000}
                onChange={(e) => setMessage(e.target.value)}
                placeholder={`Merge ${selectedSource || 'branch'} into ${r.current}`}
              />
            </label>
            <Button variant="primary" type="submit" disabled={busy || !selectedSource}>
              <GitMerge size={18} />
              {busy ? 'Merging...' : 'Merge branches'}
            </Button>
          </form>
          <p className="muted">
            Fast-forward and conflict-free merges finish automatically. Conflicting files stay
            available for manual resolution.
          </p>
        </section>
      ) : (
        <>
          <div className="merge-progress">
            <Tag tone={r.merge.conflicts.length ? 'warm' : 'success'}>
              {r.merge.conflicts.length} unresolved conflicts
            </Tag>
            <Button variant="danger" disabled={busy} onClick={() => setAbort(true)}>
              Abort merge
            </Button>
          </div>
          {conflict ? (
            <div className="conflict-layout">
              <aside className="conflict-files">
                {r.merge.conflicts.map((c) => (
                  <button
                    className={`change-file ${conflict.path === c.path ? 'selected' : ''}`}
                    key={c.path}
                    onClick={() => setSelected(c.path)}
                  >
                    {c.path}
                  </button>
                ))}
              </aside>
              <section className="conflict-editor">
                <h3>{conflict.path}</h3>
                <div className="conflict-sides">
                  <section>
                    <h4>Current branch</h4>
                    <pre>{conflict.ours ?? '(deleted or binary)'}</pre>
                  </section>
                  <section>
                    <h4>Incoming branch</h4>
                    <pre>{conflict.theirs ?? '(deleted or binary)'}</pre>
                  </section>
                </div>
                {(conflict as typeof conflict & { binary?: boolean }).binary ? (
                  <p role="alert">
                    This is a binary conflict. Resolve it with the CLI after stopping the server.
                  </p>
                ) : (
                  <>
                    <div className="conflict-choice">
                      <Button
                        onClick={() => {
                          setChoice('ours')
                          setDraft(conflict.ours ?? '')
                        }}
                      >
                        Use current
                      </Button>
                      <Button
                        onClick={() => {
                          setChoice('theirs')
                          setDraft(conflict.theirs ?? '')
                        }}
                      >
                        Use incoming
                      </Button>
                      <Button onClick={() => setChoice('edit')}>Edit result</Button>
                    </div>
                    <label className="field">
                      Resolved content
                      <textarea
                        style={{ resize: 'none' }}
                        className="resolution-textarea resize-none"
                        aria-label="Resolved content"
                        spellCheck={false}
                        value={draft}
                        onChange={(e) => {
                          setChoice('edit')
                          setDraft(e.target.value)
                        }}
                      />
                    </label>
                    <Button
                      variant="primary"
                      disabled={busy}
                      onClick={async () => {
                        if (/^(<<<<<<<|=======|>>>>>>>)/m.test(draft)) {
                          setError('Remove the conflict markers before saving the resolution.')
                          return
                        }
                        try {
                          const deletion =
                            (choice === 'ours' && conflict.ours === undefined) ||
                            (choice === 'theirs' && conflict.theirs === undefined)
                          if (deletion) {
                            if (Object.hasOwn(r.files, conflict.path))
                              await command(
                                r.id,
                                '/files?path=' + encodeURIComponent(conflict.path),
                                undefined,
                                'DELETE',
                                'Deletion selected.',
                              )
                          } else
                            await command(
                              r.id,
                              '/files/content',
                              { path: conflict.path, content: draft },
                              'PUT',
                              'Resolution saved.',
                            )
                          await command(
                            r.id,
                            '/stage',
                            { path: conflict.path },
                            'POST',
                            'Resolution staged.',
                          )
                          setError('')
                        } catch (error) {
                          setError(
                            error instanceof Error ? error.message : 'Could not stage resolution.',
                          )
                        }
                      }}
                    >
                      Save and stage resolution
                    </Button>
                  </>
                )}
              </section>
            </div>
          ) : (
            <section className="panel merge-ready">
              <Check size={36} />
              <h3>All resolutions are staged.</h3>
              <p>Create the merge commit to connect both parents.</p>
              <label className="field">
                Merge message
                <input
                  aria-label="Merge commit message"
                  value={message}
                  onChange={(e) => setMessage(e.target.value)}
                  placeholder={`Merge ${r.merge.source} into ${r.current}`}
                  maxLength={4000}
                />
              </label>
              <Button
                variant="primary"
                disabled={busy}
                onClick={async () => {
                  try {
                    await command(
                      r.id,
                      '/commits',
                      {
                        author: user?.name,
                        message: message.trim() || `Merge ${r.merge!.source} into ${r.current}`,
                      },
                      'POST',
                      'Merge committed.',
                    )
                    setError('')
                  } catch (error) {
                    setError(error instanceof Error ? error.message : 'Could not finish merge.')
                  }
                }}
              >
                Complete merge commit
              </Button>
            </section>
          )}
        </>
      )}
      {abort && (
        <Modal
          title="Abort this merge?"
          onClose={() => {
            if (!busy) setAbort(false)
          }}
        >
          <p className="muted">
            Restore the files and staging area from before this merge. Any resolution edits made
            during it will be discarded.
          </p>
          <div className="modal-footer">
            <Button disabled={busy} onClick={() => setAbort(false)}>
              Continue resolving
            </Button>
            <Button
              variant="danger"
              disabled={busy}
              onClick={async () => {
                try {
                  await command(r.id, '/merge/abort', undefined, 'POST', 'Merge aborted.')
                  setAbort(false)
                  setError('')
                } catch (error) {
                  setError(error instanceof Error ? error.message : 'Could not abort merge.')
                }
              }}
            >
              Abort merge
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
