import { useCallback, useEffect, useRef, useState } from 'react'
import type { ReactNode, FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import {
  ArrowRight,
  Check,
  Eye,
  EyeSlash,
  GitBranch,
  GitCommit,
  LockSimple,
  Moon,
  Sun,
} from '@phosphor-icons/react'
import { useStore } from './store'
import { Button } from './ui'
import { ApiError } from './api'
import './entry.css'

const INTRO_KEY = 'customvcs.intro.v1'
function readSession(key: string) {
  return sessionStorage.getItem(key) === 'active'
}

export function EntryGate({ children }: { children: ReactNode }) {
  const { user, authLoading, error, reload } = useStore()
  const [introduced, setIntroduced] = useState(() => readSession(INTRO_KEY))
  const location = useLocation(),
    navigate = useNavigate()
  const finishIntro = useCallback(() => {
    sessionStorage.setItem(INTRO_KEY, 'active')
    setIntroduced(true)
  }, [])
  const requested = (location.state as { from?: string } | null)?.from
  const destination =
    requested?.startsWith('/') &&
    !requested.startsWith('//') &&
    !/^\/(login|register)(\/|\?|$)/.test(requested)
      ? requested
      : '/'
  if (!introduced) return <Startup onFinish={finishIntro} />
  if (authLoading)
    return (
      <main className="recovery" role="status">
        <h1>Opening your workspace...</h1>
      </main>
    )
  const publicPage = location.pathname === '/login' || location.pathname === '/register'
  if (publicPage)
    return user ? (
      <Navigate to={destination} replace />
    ) : (
      <AuthPage
        key={location.pathname}
        registering={location.pathname === '/register'}
        onEnter={() => navigate(destination, { replace: true })}
        destination={destination}
      />
    )
  if (!user)
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />
  return (
    <>
      {error && (
        <div className="api-banner" role="alert">
          {error}
          <Button onClick={() => void reload().catch(() => {})}>Refresh</Button>
        </div>
      )}
      {children}
    </>
  )
}
export const useSession = () => {
  const { signOut } = useStore()
  return { signOut }
}

function Startup({ onFinish }: { onFinish: () => void }) {
  useEffect(() => {
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches
    const timer = window.setTimeout(onFinish, reduced ? 100 : 1750)
    return () => window.clearTimeout(timer)
  }, [onFinish])
  return (
    <main className="startup" aria-label="Starting CustomVCS Studio">
      <div className="startup-center">
        <div className="startup-mark" aria-hidden="true">
          <svg viewBox="0 0 80 80">
            <path className="startup-stem" d="M27 23V59" />
            <path className="startup-fork" d="M27 47C27 32 54 47 54 22" />
            <circle className="startup-node n1" cx="27" cy="20" r="6" />
            <circle className="startup-node n2" cx="27" cy="60" r="6" />
            <circle className="startup-node n3" cx="54" cy="20" r="6" />
          </svg>
        </div>
        <div className="startup-wordmark">
          custom<span>vcs</span>
          <small>STUDIO</small>
        </div>
        <p>Your code. Its entire story.</p>
        <div className="startup-track" aria-hidden="true">
          <span />
        </div>
      </div>
      <button className="startup-skip" onClick={onFinish}>
        Skip intro
        <ArrowRight size={14} />
      </button>
      <span className="startup-footnote">A little closer to the code.</span>
    </main>
  )
}

function AuthPage({
  registering,
  onEnter,
  destination,
}: {
  registering: boolean
  onEnter: () => void
  destination: string
}) {
  const { workspace, set, authenticate, error: sessionError } = useStore()
  const [submitting, setSubmitting] = useState(false)
  const [name, setName] = useState(''),
    [email, setEmail] = useState(''),
    [password, setPassword] = useState(''),
    [confirmation, setConfirmation] = useState('')
  const [showPassword, setShowPassword] = useState(false),
    [showConfirmation, setShowConfirmation] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const nameRef = useRef<HTMLInputElement>(null),
    emailRef = useRef<HTMLInputElement>(null),
    passwordRef = useRef<HTMLInputElement>(null),
    confirmationRef = useRef<HTMLInputElement>(null),
    heading = useRef<HTMLHeadingElement>(null)
  useEffect(() => {
    document.title = `${registering ? 'Create your account' : 'Welcome back'} · CustomVCS Studio`
    heading.current?.focus()
  }, [registering])
  const clearError = (field: string) =>
    setErrors((current) => {
      const next = { ...current }
      delete next[field]
      return next
    })
  const submit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    if (submitting) return
    const next: Record<string, string> = {}
    if (registering && !name.trim()) next.name = 'Enter your name.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()))
      next.email = 'Enter a valid email address.'
    if (!password) next.password = 'Enter your password.'
    else if (registering && password.length < 8) next.password = 'Use at least 8 characters.'
    if (registering && confirmation !== password) next.confirmation = 'Your passwords do not match.'
    setErrors(next)
    const fields = {
      name: nameRef,
      email: emailRef,
      password: passwordRef,
      confirmation: confirmationRef,
    }
    const first = (Object.keys(fields) as Array<keyof typeof fields>).find((key) => next[key])
    if (first) {
      fields[first].current?.focus()
      return
    }
    setSubmitting(true)
    try {
      await authenticate(registering, { name: name.trim(), email: email.trim(), password })
      setPassword('')
      setConfirmation('')
      onEnter()
    } catch (error) {
      const fields = error instanceof ApiError ? error.fields : {}
      setErrors({
        ...fields,
        form: error instanceof Error ? error.message : 'Could not sign in. Try again.',
      })
      if (error instanceof ApiError && error.status === 401) setPassword('')
    } finally {
      setSubmitting(false)
    }
  }
  return (
    <main className="entry-page">
      <aside className="entry-story" aria-label="About CustomVCS Studio">
        <Brand light />
        <div className="entry-story-content">
          <p className="entry-eyebrow">
            <span />A PLACE FOR WHAT’S NEXT
          </p>
          <h2>
            Every great idea
            <br />
            has a <em>first commit.</em>
          </h2>
          <p>
            Room to experiment. A history to come back to.
            <br />
            One connected story, entirely yours.
          </p>
          <StoryGraph />
        </div>
        <div className="entry-story-footer">
          <span>
            <GitCommit size={16} />
            Built from first principles.
          </span>
          <span>Java at the core.</span>
        </div>
      </aside>
      <section className="entry-main">
        <header className="entry-top">
          <div className="entry-mobile-brand">
            <Brand />
          </div>
          <Link to={registering ? '/login' : '/register'} state={{ from: destination }}>
            {registering ? 'Already have an account?' : 'New around here?'}
            <strong>
              {registering ? 'Sign in' : 'Create an account'}
              <ArrowRight size={14} />
            </strong>
          </Link>
          <button
            className="icon-button entry-theme"
            aria-label={`Switch to ${workspace.theme === 'light' ? 'dark' : 'light'} theme`}
            onClick={() => set((w) => ({ ...w, theme: w.theme === 'light' ? 'dark' : 'light' }))}
          >
            {workspace.theme === 'light' ? <Moon size={18} /> : <Sun size={18} />}
          </button>
        </header>
        <div className={`entry-form-wrap ${registering ? 'registering' : ''}`}>
          <div className="entry-welcome-icon">
            <GitBranch size={25} weight="bold" />
          </div>
          <p className="eyebrow">
            {registering ? 'YOUR NEXT CHAPTER STARTS HERE' : 'PICK UP WHERE YOU LEFT OFF'}
          </p>
          <h1 ref={heading} tabIndex={-1}>
            {registering ? 'Make room for your ideas.' : 'Welcome back.'}
          </h1>
          <p className="entry-subtitle">
            {registering
              ? 'Create your account. Build something worth committing.'
              : 'Your projects, your progress, your next possibility.'}
          </p>
          <form className="entry-form" onSubmit={submit} noValidate>
            {(errors.form || sessionError) && (
              <p role="alert" className="form-error">
                {errors.form || sessionError}
              </p>
            )}
            {registering && (
              <label className="entry-field" htmlFor="entry-name">
                Your name
                <div className={`entry-input ${errors.name ? 'invalid' : ''}`}>
                  <input
                    ref={nameRef}
                    id="entry-name"
                    name="name"
                    autoComplete="name"
                    maxLength={40}
                    value={name}
                    placeholder="Alex Morgan"
                    onChange={(e) => {
                      setName(e.target.value)
                      clearError('name')
                    }}
                    aria-invalid={!!errors.name}
                    aria-describedby={errors.name ? 'name-error' : undefined}
                  />
                </div>
                {errors.name && (
                  <span id="name-error" className="entry-error" role="alert">
                    {errors.name}
                  </span>
                )}
              </label>
            )}
            <label className="entry-field" htmlFor="entry-email">
              Email address
              <div className={`entry-input ${errors.email ? 'invalid' : ''}`}>
                <input
                  ref={emailRef}
                  id="entry-email"
                  name="email"
                  type="email"
                  autoComplete="email"
                  inputMode="email"
                  value={email}
                  placeholder="you@example.com"
                  onChange={(e) => {
                    setEmail(e.target.value)
                    clearError('email')
                  }}
                  aria-invalid={!!errors.email}
                  aria-describedby={errors.email ? 'email-error' : undefined}
                />
              </div>
              {errors.email && (
                <span id="email-error" className="entry-error" role="alert">
                  {errors.email}
                </span>
              )}
            </label>
            <label className="entry-field" htmlFor="entry-password">
              <span>Password{registering && <small>At least 8 characters</small>}</span>
              <div className={`entry-input ${errors.password ? 'invalid' : ''}`}>
                <input
                  ref={passwordRef}
                  id="entry-password"
                  name="password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete={registering ? 'new-password' : 'current-password'}
                  value={password}
                  placeholder={registering ? 'Create a sample password' : 'Enter a sample password'}
                  onChange={(e) => {
                    setPassword(e.target.value)
                    clearError('password')
                  }}
                  aria-invalid={!!errors.password}
                  aria-describedby={errors.password ? 'password-error' : undefined}
                />
                <button
                  type="button"
                  aria-label={showPassword ? 'Hide password' : 'Show password'}
                  aria-pressed={showPassword}
                  onClick={() => setShowPassword((v) => !v)}
                >
                  {showPassword ? <EyeSlash size={19} /> : <Eye size={19} />}
                </button>
              </div>
              {errors.password && (
                <span id="password-error" className="entry-error" role="alert">
                  {errors.password}
                </span>
              )}
            </label>
            {registering && (
              <label className="entry-field" htmlFor="entry-confirmation">
                Confirm password
                <div className={`entry-input ${errors.confirmation ? 'invalid' : ''}`}>
                  <input
                    ref={confirmationRef}
                    id="entry-confirmation"
                    name="confirmPassword"
                    type={showConfirmation ? 'text' : 'password'}
                    autoComplete="new-password"
                    value={confirmation}
                    placeholder="One more time"
                    onChange={(e) => {
                      setConfirmation(e.target.value)
                      clearError('confirmation')
                    }}
                    aria-invalid={!!errors.confirmation}
                    aria-describedby={errors.confirmation ? 'confirmation-error' : undefined}
                  />
                  <button
                    type="button"
                    aria-label={
                      showConfirmation ? 'Hide confirmation password' : 'Show confirmation password'
                    }
                    aria-pressed={showConfirmation}
                    onClick={() => setShowConfirmation((v) => !v)}
                  >
                    {showConfirmation ? <EyeSlash size={19} /> : <Eye size={19} />}
                  </button>
                </div>
                {errors.confirmation && (
                  <span id="confirmation-error" className="entry-error" role="alert">
                    {errors.confirmation}
                  </span>
                )}
              </label>
            )}
            <Button
              type="submit"
              variant="primary"
              className="entry-submit"
              disabled={submitting}
              aria-busy={submitting}
            >
              {submitting
                ? registering
                  ? 'Creating account...'
                  : 'Signing in...'
                : registering
                  ? 'Create account'
                  : 'Sign in'}
              <ArrowRight size={18} />
            </Button>
          </form>
          <div className="entry-preview-note">
            <LockSimple size={14} />
            <p>
              Your session is protected by a secure server-side login. Use 8 or more characters for
              your password.
            </p>
          </div>
          <p className="entry-form-switch">
            {registering ? 'Already part of the story?' : 'Have an idea worth building?'}{' '}
            <Link to={registering ? '/login' : '/register'} state={{ from: destination }}>
              {registering ? 'Sign in' : 'Create an account'}
            </Link>
          </p>
        </div>
        <footer className="entry-footer">
          <span>CustomVCS Studio</span>
          <span>
            Your code. Its entire story.
            <i />
          </span>
        </footer>
      </section>
    </main>
  )
}
function Brand({ light = false }: { light?: boolean }) {
  return (
    <Link
      to="/login"
      className={`entry-brand ${light ? 'light' : ''}`}
      title="CustomVCS Studio sign in"
    >
      <span>
        <GitBranch weight="bold" size={24} />
      </span>
      <strong>
        custom<b>vcs</b>
      </strong>
      <small>STUDIO</small>
    </Link>
  )
}
function StoryGraph() {
  return (
    <div className="entry-graph" aria-hidden="true">
      <div className="story-file">
        <div>
          <span>
            <span className="story-file-dot" />
            README.md
          </span>
          <span>your-next-idea</span>
        </div>
        <code>
          <i>01</i>
          <span># Hello, possibility.</span>
          <br />
          <i>02</i>
          <br />
          <i>03</i> A small start. An open road.
        </code>
      </div>
      <svg viewBox="0 0 480 175">
        <defs>
          <pattern id="entry-dots" width="19" height="19" patternUnits="userSpaceOnUse">
            <circle cx="1" cy="1" r=".65" fill="#a4baff" opacity=".3" />
          </pattern>
        </defs>
        <rect width="480" height="175" fill="url(#entry-dots)" />
        <path className="story-main-path" d="M34 65H446" />
        <path className="story-feature-path" d="M154 65C211 65 204 127 268 127H385" />
        <circle cx="34" cy="65" r="6" />
        <circle cx="154" cy="65" r="6" />
        <circle cx="299" cy="65" r="6" />
        <circle cx="446" cy="65" r="7" />
        <circle className="story-feature-node" cx="268" cy="127" r="6" />
        <circle className="story-feature-node" cx="385" cy="127" r="6" />
        <text x="31" y="32">
          main
        </text>
        <text x="292" y="157">
          feature/your-next-idea
        </text>
        <rect className="story-head" x="423" y="20" width="47" height="22" rx="5" />
        <text className="story-head-text" x="446" y="35" textAnchor="middle">
          HEAD
        </text>
      </svg>
      <div className="story-commit">
        <span>
          <Check size={14} />
        </span>
        <div>
          <strong>Make something worth committing.</strong>
          <p>One small step, a new possibility.</p>
        </div>
        <GitCommit size={20} />
      </div>
    </div>
  )
}
