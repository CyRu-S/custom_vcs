import { useEffect, useRef, useState } from 'react'
import type { ReactNode, ButtonHTMLAttributes } from 'react'
import { ArrowUpRight, Check, Copy, GitBranch, Plus, X } from '@phosphor-icons/react'
import { useNavigate } from 'react-router-dom'
import { useStore } from './store'

export function Button({
  children,
  variant = '',
  className = '',
  onClick,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: string }) {
  return (
    <button className={`button ${variant} ${className}`} onClick={onClick} {...props}>
      {children}
    </button>
  )
}
export function Tag({ children, tone = '' }: { children: ReactNode; tone?: string }) {
  return <span className={`tag ${tone}`}>{children}</span>
}
export function RepoIcon({ color = 'blue', small = false }: { color?: string; small?: boolean }) {
  return (
    <span className={`repo-icon ${color} ${small ? 'small' : ''}`}>
      <GitBranch size={small ? 19 : 26} weight="bold" />
    </span>
  )
}
export function Empty({
  title,
  description,
  children,
}: {
  title: string
  description: string
  children?: ReactNode
}) {
  return (
    <div className="empty">
      <GitBranch size={36} weight="light" />
      <h3>{title}</h3>
      <p>{description}</p>
      {children}
    </div>
  )
}
export function Modal({
  title,
  children,
  onClose,
  wide = false,
}: {
  title: string
  children: ReactNode
  onClose: () => void
  wide?: boolean
}) {
  const ref = useRef<HTMLDialogElement>(null)
  const close = useRef(onClose)
  close.current = onClose
  useEffect(() => {
    const active = document.activeElement as HTMLElement
    const dialog = ref.current!
    dialog.showModal()
    return () => {
      dialog.close()
      active?.focus()
    }
  }, [])
  return (
    <dialog
      ref={ref}
      className={`modal ${wide ? 'wide' : ''}`}
      onCancel={(e) => {
        e.preventDefault()
        close.current()
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose()
      }}
      aria-labelledby="modal-heading"
    >
      <div className="modal-inner">
        <div className="modal-title">
          <h2 id="modal-heading">{title}</h2>
          <button className="icon-button" aria-label="Close dialog" onClick={onClose}>
            <X size={20} />
          </button>
        </div>
        {children}
      </div>
    </dialog>
  )
}
export function CopyButton({ value, label = 'Copy' }: { value: string; label?: string }) {
  const [done, setDone] = useState(false)
  const { notice } = useStore()
  return (
    <button
      className="icon-button"
      aria-label={done ? 'Copied' : label}
      onClick={async () => {
        try {
          await navigator.clipboard.writeText(value)
          setDone(true)
          setTimeout(() => setDone(false), 1800)
        } catch {
          notice('Copy is unavailable. Select the text and copy it manually.')
        }
      }}
    >
      {done ? <Check size={16} /> : <Copy size={16} />}
    </button>
  )
}
export function CreateRepository({ onClose }: { onClose: () => void }) {
  const { createRepository, busy } = useStore()
  const navigate = useNavigate()
  const [name, setName] = useState(''),
    [description, setDescription] = useState(''),
    [error, setError] = useState('')
  return (
    <Modal
      title="A new place for your code"
      onClose={() => {
        if (!busy) onClose()
      }}
    >
      <div className="modal-symbol">
        <GitBranch size={30} />
      </div>
      <p className="muted">
        Create a private repository. Its main branch starts empty, ready for your first file.
      </p>
      <form
        noValidate
        onSubmit={async (event) => {
          event.preventDefault()
          if (busy) return
          if (!/^[A-Za-z0-9][A-Za-z0-9_-]{0,63}$/.test(name.trim())) {
            setError(
              'Use 1-64 letters, numbers, hyphens or underscores. Start with a letter or number.',
            )
            return
          }
          try {
            const id = await createRepository(name.trim(), description.trim())
            onClose()
            navigate(`/r/${id}/code`)
          } catch (error) {
            setError(error instanceof Error ? error.message : 'Could not create the repository.')
          }
        }}
      >
        <label className="field">
          Repository name
          <input
            autoFocus
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="my-next-big-idea"
            aria-invalid={!!error}
            aria-describedby={error ? 'create-error' : undefined}
          />
        </label>
        <label className="field">
          Description <span className="optional">optional</span>
          <textarea
            className="resize-none"
            style={{ resize: 'none' }}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={300}
          />
        </label>
        {error && (
          <p className="form-error" id="create-error" role="alert">
            {error}
          </p>
        )}
        <div className="form-note">
          <Check size={16} />
          Only your signed-in account can access this repository.
        </div>
        <div className="modal-footer">
          <Button type="button" disabled={busy} onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" disabled={busy} aria-busy={busy}>
            <Plus size={17} />
            {busy ? 'Creating...' : 'Create repository'}
          </Button>
        </div>
      </form>
    </Modal>
  )
}
export function SectionTitle({
  label,
  title,
  children,
}: {
  label?: string
  title: string
  children?: ReactNode
}) {
  return (
    <div className="section-title">
      <div>
        {label && <p className="eyebrow">{label}</p>}
        <h2>{title}</h2>
      </div>
      {children}
    </div>
  )
}
export function ArrowLink() {
  return <ArrowUpRight size={18} />
}
