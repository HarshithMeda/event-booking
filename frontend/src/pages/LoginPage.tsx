import { useState, type FormEvent } from 'react'
import { api, ApiError } from '../api'

interface Props {
  notice: string | null
  onLoggedIn: (token: string) => void
}

type Mode = 'login' | 'register'

export default function LoginPage({ notice, onLoggedIn }: Props) {
  const [mode, setMode] = useState<Mode>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [busy, setBusy] = useState(false)

  function switchMode(next: Mode) {
    setMode(next)
    setError(null)
    setFieldErrors({})
  }

  async function submit(e: FormEvent) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    if (mode === 'register' && password.length < 8) {
      setFieldErrors({ password: 'Use at least 8 characters.' })
      return
    }
    setBusy(true)
    try {
      if (mode === 'register') {
        await api.register(email, password)
      }
      const { accessToken } = await api.login(email, password)
      onLoggedIn(accessToken)
    } catch (err) {
      if (!(err instanceof ApiError)) {
        setError('Something went wrong. Please try again.')
      } else if (err.status === 409) {
        setError('An account with this email already exists. Try logging in instead.')
      } else if (err.status === 401) {
        setError('Wrong email or password.')
      } else if (err.status === 400 && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(friendlyFieldErrors(err.fieldErrors))
      } else {
        setError(err.message)
      }
    } finally {
      setBusy(false)
    }
  }

  const registering = mode === 'register'
  return (
    <main className="container narrow">
      <h1 className="login-title">🎟️ Event Booking</h1>
      <div className="card">
        <div className="tabs" role="tablist">
          <button type="button" role="tab" aria-selected={!registering} className={!registering ? 'active' : ''}
                  onClick={() => switchMode('login')}>Log in</button>
          <button type="button" role="tab" aria-selected={registering} className={registering ? 'active' : ''}
                  onClick={() => switchMode('register')}>Register</button>
        </div>

        {notice && <p className="alert info" role="status">{notice}</p>}
        {error && <p className="alert error" role="alert">{error}</p>}

        <form onSubmit={submit} noValidate>
          <label htmlFor="email">Email</label>
          <input id="email" type="email" autoComplete="email" required value={email}
                 onChange={(e) => setEmail(e.target.value)} aria-invalid={!!fieldErrors.email} />
          {fieldErrors.email && <p className="field-error">{fieldErrors.email}</p>}

          <label htmlFor="password">Password</label>
          <input id="password" type="password" required value={password}
                 autoComplete={registering ? 'new-password' : 'current-password'}
                 onChange={(e) => setPassword(e.target.value)} aria-invalid={!!fieldErrors.password} />
          {fieldErrors.password
            ? <p className="field-error">{fieldErrors.password}</p>
            : registering && <p className="hint">At least 8 characters.</p>}

          <button type="submit" className="button primary full" disabled={busy || !email || !password}>
            {busy ? 'Please wait…' : registering ? 'Create account' : 'Log in'}
          </button>
        </form>
      </div>
    </main>
  )
}

function friendlyFieldErrors(errors: Record<string, string>): Record<string, string> {
  const friendly: Record<string, string> = {}
  if (errors.email) friendly.email = 'Enter a valid email address.'
  if (errors.password) friendly.password = 'Use 8 to 72 characters.'
  return friendly
}
