import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import Markdown from 'react-markdown'
import {
  DownloadSimple,
  CaretDown,
  CaretRight,
  Code,
  FileCode,
  FilePlus,
  FileText,
  Folder,
  FolderOpen,
  MagnifyingGlass,
  PencilSimple,
  X,
} from '@phosphor-icons/react'
import { useStore, download } from './store'
import { basename } from './model'
import type { Repository } from './model'
import { Button, CopyButton, Empty, Modal } from './ui'

type Tree = { name: string; path: string; children: Tree[]; file: boolean }
function tree(paths: string[]): Tree[] {
  const root: Tree[] = []
  for (const path of paths.sort()) {
    let list = root
    const parts = path.split('/')
    parts.forEach((name, i) => {
      const current = parts.slice(0, i + 1).join('/')
      let node = list.find((n) => n.name === name)
      if (!node) {
        node = { name, path: current, children: [], file: i === parts.length - 1 }
        list.push(node)
      }
      list = node.children
    })
  }
  const sort = (nodes: Tree[]): Tree[] =>
    nodes
      .sort((a, b) => Number(a.file) - Number(b.file) || a.name.localeCompare(b.name))
      .map((n) => ({ ...n, children: sort(n.children) }))
  return sort(root)
}
function TreeNode({
  node,
  selected,
  onSelect,
  depth = 0,
  force = false,
}: {
  node: Tree
  selected: string
  onSelect: (p: string) => void
  depth?: number
  force?: boolean
}) {
  const [open, setOpen] = useState(selected.startsWith(node.path + '/'))
  useEffect(() => {
    if (selected.startsWith(node.path + '/')) setOpen(true)
  }, [selected, node.path])
  const expanded = open || force
  return (
    <div>
      <button
        className={`tree-item ${selected === node.path ? 'selected' : ''} ${node.file ? '' : 'directory'}`}
        title={node.path}
        style={{ paddingLeft: 14 + depth * 15 }}
        onClick={() => (node.file ? onSelect(node.path) : setOpen((v) => !v))}
        aria-expanded={node.file ? undefined : expanded}
      >
        {node.file ? (
          <span className="tree-spacer" />
        ) : expanded ? (
          <CaretDown size={12} />
        ) : (
          <CaretRight size={12} />
        )}{' '}
        {node.file ? (
          node.name.endsWith('.md') ? (
            <FileText size={16} />
          ) : (
            <FileCode size={16} />
          )
        ) : expanded ? (
          <FolderOpen size={17} weight="fill" />
        ) : (
          <Folder size={17} weight="fill" />
        )}
        <span>{node.name}</span>
      </button>
      {!node.file &&
        expanded &&
        node.children.map((child) => (
          <TreeNode
            key={child.path}
            node={child}
            depth={depth + 1}
            selected={selected}
            onSelect={onSelect}
            force={force}
          />
        ))}
    </div>
  )
}
// Keep unsaved text through a sign-in redirect, without putting source code in browser storage.
const editorDrafts = new Map<string, string>()
export function CodeWorkspace({ repository: r }: { repository: Repository }) {
  const { command, busy, user } = useStore()
  const [params, setParams] = useSearchParams()
  const files = r.files
  const [filter, setFilter] = useState(''),
    [raw, setRaw] = useState(false),
    [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(''),
    [newFile, setNewFile] = useState(false),
    [mobileFiles, setMobileFiles] = useState(false)
  const [error, setError] = useState(''),
    [deleting, setDeleting] = useState(false)
  const [leave, setLeave] = useState<(() => void) | null>(null)
  const path =
    params.get('file') ??
    (files['README.md'] !== undefined ? 'README.md' : (Object.keys(files)[0] ?? ''))
  const content = files[path]
  const draftKey = `${user?.id}:${r.id}:${path}`
  const dirty = editing && draft !== (content ?? '')
  const navigate = useNavigate()
  useEffect(() => {
    const saved = editorDrafts.get(draftKey)
    if (saved !== undefined) {
      setDraft(saved)
      setEditing(true)
    }
  }, [draftKey])
  useEffect(() => {
    if (!dirty) return
    const unload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    const click = (event: MouseEvent) => {
      const link = (event.target as Element).closest('a[href]') as HTMLAnchorElement | null
      if (
        !link ||
        link.origin !== location.origin ||
        link.target === '_blank' ||
        event.ctrlKey ||
        event.metaKey ||
        event.button !== 0
      )
        return
      event.preventDefault()
      event.stopPropagation()
      const destination = link.pathname + link.search + link.hash
      setLeave(() => () => navigate(destination))
    }
    window.addEventListener('beforeunload', unload)
    document.addEventListener('click', click, true)
    return () => {
      window.removeEventListener('beforeunload', unload)
      document.removeEventListener('click', click, true)
    }
  }, [dirty, navigate])
  const changeFile = (file: string) => {
    const action = () => {
      setEditing(false)
      setRaw(false)
      setError('')
      setParams({ file })
      setMobileFiles(false)
    }
    if (dirty) setLeave(() => action)
    else action()
  }
  const save = async () => {
    if (busy) return
    try {
      await command(
        r.id,
        '/files/content',
        { path, content: draft },
        'PUT',
        'File saved. Stage it when ready.',
      )
      editorDrafts.delete(draftKey)
      setEditing(false)
      setError('')
    } catch (error) {
      setError(error instanceof Error ? error.message : 'Could not save file.')
    }
  }
  const paths = [...new Set([...Object.keys(files), ...r.unreadableFiles])]
  const nodes = tree(paths.filter((p) => p.toLowerCase().includes(filter.toLowerCase())))
  return (
    <div className={`source-workspace ${mobileFiles ? 'show-files' : ''}`}>
      <aside className="file-explorer">
        <div className="explorer-heading">
          <strong>EXPLORER</strong>
          <span>{paths.length}</span>
          <button
            className="icon-button"
            aria-label="New file"
            onClick={() => setNewFile(true)}
            disabled={busy || dirty}
          >
            <FilePlus size={17} />
          </button>
        </div>
        <label className="explorer-search">
          <MagnifyingGlass size={15} />
          <input
            aria-label="Find a file"
            placeholder="Find a file..."
            value={filter}
            onChange={(e) => setFilter(e.target.value)}
          />
          {filter && (
            <button aria-label="Clear file search" onClick={() => setFilter('')}>
              <X size={14} />
            </button>
          )}
        </label>
        <div className="tree-root">
          <CaretDown size={13} />
          <strong>{r.name.toUpperCase()}</strong>
        </div>
        <nav className="file-tree" aria-label="Repository files">
          {nodes.map((node) => (
            <TreeNode
              key={node.path}
              node={node}
              selected={path}
              onSelect={changeFile}
              force={!!filter}
            />
          ))}
          {!nodes.length && (
            <p className="tree-empty">
              {filter ? 'No matching files.' : 'No files yet. Create your first file.'}
            </p>
          )}
        </nav>
        <div className="explorer-bottom">
          <span>
            <i />
            {r.current}
          </span>
        </div>
      </aside>
      <section className="editor-pane">
        <div className="editor-tabbar">
          <button
            className="mobile-files-toggle"
            aria-label="Toggle file explorer"
            onClick={() => setMobileFiles((v) => !v)}
          >
            <Folder size={18} />
          </button>
          <div className="file-tab">
            <FileText size={16} />
            {basename(path) || 'No file selected'}
            {r.changes.some((c) => c.path === path) && <i title="Uncommitted changes" />}
          </div>
          <div className="editor-tab-filler" />
          <span className="editor-branch">
            <Code size={14} />
            {r.current}
          </span>
        </div>
        {error && (
          <p className="form-error api-status" role="alert">
            {error}
          </p>
        )}
        {content !== undefined ? (
          <>
            <div className="editor-filebar">
              <strong>{path}</strong>
              <div>
                {editing ? (
                  <>
                    <Button
                      disabled={busy}
                      onClick={() => {
                        if (dirty)
                          setLeave(() => () => {
                            editorDrafts.delete(draftKey)
                            setEditing(false)
                          })
                        else setEditing(false)
                      }}
                    >
                      Cancel
                    </Button>
                    <Button variant="primary" disabled={busy} onClick={() => void save()}>
                      {busy ? 'Saving...' : 'Save changes'}
                    </Button>
                  </>
                ) : (
                  <>
                    <button className="text-button" onClick={() => setRaw((v) => !v)}>
                      {raw ? 'Preview' : 'Raw'}
                    </button>
                    <CopyButton value={content} label="Copy file contents" />
                    <button
                      className="icon-button"
                      aria-label="Download file"
                      onClick={() => download(content, basename(path))}
                    >
                      <DownloadSimple size={16} />
                    </button>
                    <button
                      className="icon-button"
                      aria-label="Edit file"
                      disabled={busy}
                      onClick={() => {
                        setDraft(editorDrafts.get(draftKey) ?? content)
                        setEditing(true)
                      }}
                    >
                      <PencilSimple size={17} />
                    </button>
                    <Button variant="danger" disabled={busy} onClick={() => setDeleting(true)}>
                      Delete file
                    </Button>
                  </>
                )}
              </div>
            </div>
            {editing ? (
              <div className="edit-area">
                <span className="edit-hint">Save to the working tree, then stage and commit.</span>
                <textarea
                  className="resize-none"
                  style={{ resize: 'none' }}
                  autoFocus
                  spellCheck={false}
                  aria-label="Edit file contents"
                  value={draft}
                  onChange={(e) => {
                    setDraft(e.target.value)
                    editorDrafts.set(draftKey, e.target.value)
                  }}
                  onKeyDown={(e) => {
                    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                      e.preventDefault()
                      void save()
                    }
                  }}
                />
              </div>
            ) : path.endsWith('.md') && !raw ? (
              <div className="markdown document">
                <Markdown>{content}</Markdown>
              </div>
            ) : (
              <SourceLines content={content} />
            )}
          </>
        ) : (
          <Empty
            title={
              r.unreadableFiles.includes(path)
                ? 'Text preview unavailable'
                : 'A place for your first file'
            }
            description={
              r.unreadableFiles.includes(path)
                ? 'This file is binary, linked, or larger than the 1 MiB editor limit. Use the CLI to inspect it.'
                : 'Create a file, add some code, then make your first commit.'
            }
          >
            <Button variant="primary" onClick={() => setNewFile(true)}>
              Create file
            </Button>
          </Empty>
        )}
      </section>
      {newFile && (
        <NewFile
          repository={r}
          onClose={() => setNewFile(false)}
          onCreate={(file) => {
            setNewFile(false)
            setParams({ file })
          }}
        />
      )}
      {leave && (
        <Modal title="Leave unsaved changes?" onClose={() => setLeave(null)}>
          <p className="muted">
            Your unsaved editor text will be discarded. Save it to keep this version.
          </p>
          <div className="modal-footer">
            <Button onClick={() => setLeave(null)}>Keep editing</Button>
            <Button
              variant="danger"
              onClick={() => {
                const next = leave
                editorDrafts.delete(draftKey)
                setLeave(null)
                setEditing(false)
                next()
              }}
            >
              Discard edits
            </Button>
          </div>
        </Modal>
      )}
      {deleting && (
        <Modal title={`Delete ${basename(path)}?`} onClose={() => setDeleting(false)}>
          <p className="muted">
            Delete this working file. Existing commits keep their saved versions; stage the deletion
            to include it in the next commit.
          </p>
          <div className="modal-footer">
            <Button disabled={busy} onClick={() => setDeleting(false)}>
              Keep file
            </Button>
            <Button
              variant="danger"
              disabled={busy}
              onClick={async () => {
                try {
                  await command(
                    r.id,
                    '/files?path=' + encodeURIComponent(path),
                    undefined,
                    'DELETE',
                    'Working file deleted.',
                  )
                  setDeleting(false)
                  setParams({})
                } catch (error) {
                  setError(error instanceof Error ? error.message : 'Could not delete file.')
                }
              }}
            >
              Delete file
            </Button>
          </div>
        </Modal>
      )}
    </div>
  )
}
export function SourceLines({ content }: { content: string }) {
  return (
    <div className="source-lines" tabIndex={0} role="region" aria-label="Source code">
      {content.split('\n').map((line, i) => (
        <div key={i}>
          <span className="line-number">{i + 1}</span>
          <code>
            <Syntax line={line} />
          </code>
        </div>
      ))}
    </div>
  )
}
export function Syntax({ line }: { line: string }) {
  const parts = line.split(
    /("[^"\n]*"|'[^'\n]*'|\/\/.*|\b(?:public|private|final|class|return|new|import|package|void|static|if|else|const|function|export|interface|type|extends|throws|record)\b)/g,
  )
  return (
    <>
      {parts.map((p, i) => (
        <span
          key={i}
          className={
            p.startsWith('//')
              ? 'syntax-comment'
              : /^['"]/.test(p)
                ? 'syntax-string'
                : /^(public|private|final|class|return|new|import|package|void|static|if|else|const|function|export|interface|type|extends|throws|record)$/.test(
                      p,
                    )
                  ? 'syntax-keyword'
                  : undefined
          }
        >
          {p || ' '}
        </span>
      ))}
    </>
  )
}
function NewFile({
  repository: r,
  onClose,
  onCreate,
}: {
  repository: Repository
  onClose: () => void
  onCreate: (path: string) => void
}) {
  const { command, busy } = useStore()
  const [path, setPath] = useState(''),
    [error, setError] = useState('')
  return (
    <Modal
      title="Start a new file"
      onClose={() => {
        if (!busy) onClose()
      }}
    >
      <p className="muted">Use a relative path such as docs/IDEAS.md.</p>
      <form
        noValidate
        onSubmit={async (e) => {
          e.preventDefault()
          const value = path.trim()
          if (!value) {
            setError('Enter a file path.')
            return
          }
          if (Object.hasOwn(r.files, value) || r.unreadableFiles.includes(value)) {
            setError('A file already exists at this path.')
            return
          }
          try {
            await command(
              r.id,
              '/files/content',
              { path: value, content: '' },
              'PUT',
              'File created.',
            )
            onCreate(value)
          } catch (error) {
            setError(error instanceof Error ? error.message : 'Could not create file.')
          }
        }}
      >
        <label className="field">
          File path
          <input
            autoFocus
            value={path}
            onChange={(e) => setPath(e.target.value)}
            placeholder="docs/IDEAS.md"
            aria-invalid={!!error}
          />
        </label>
        {error && (
          <p className="form-error" role="alert">
            {error}
          </p>
        )}
        <div className="modal-footer">
          <Button type="button" disabled={busy} onClick={onClose}>
            Cancel
          </Button>
          <Button type="submit" variant="primary" disabled={busy}>
            {busy ? 'Creating...' : 'Create file'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
