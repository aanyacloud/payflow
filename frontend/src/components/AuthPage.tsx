
import { useState } from 'react'
import { Activity, ArrowRight, Loader2 } from 'lucide-react'
import { useAuth } from '../context/AuthContext'

export default function AuthPage() {
  const { login, register } = useAuth()

  const [mode, setMode] = useState<'login' | 'register'>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    setLoading(true)

    try {
      if (mode === 'login') {
        await login(email, password)
      } else {
        await register(email, password)
      }
    } catch (err: any) {
      setError(
        err.response?.data?.message ||
        'Unable to authenticate. Check your details and try again.',
      )
    } finally {
      setLoading(false)
    }
  }

  return (
    <main className="auth-page">
      <section className="auth-card">
        <div className="auth-brand">
          <div className="brand-icon"><Activity size={24} /></div>
          <span>payflow<span className="brand-dot">.</span></span>
        </div>

        <div className="auth-heading">
          <h1>{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
          <p>
            {mode === 'login'
              ? 'Sign in to manage your payments.'
              : 'Get started with your PayFlow workspace.'}
          </p>
        </div>

        <form onSubmit={handleSubmit}>
          <label htmlFor="email">Email address</label>
          <input
            id="email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="you@example.com"
            required
          />

          <label htmlFor="password">Password</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Enter your password"
            minLength={8}
            maxLength={72}
            required
          />

          {error && <div className="auth-error">{error}</div>}

          <button className="primary-button auth-submit" disabled={loading}>
            {loading
              ? <Loader2 className="spin" size={17} />
              : <>{mode === 'login' ? 'Sign in' : 'Create account'} <ArrowRight size={17} /></>}
          </button>
        </form>

        <p className="auth-switch">
          {mode === 'login' ? "Don't have an account?" : 'Already registered?'}
          <button onClick={() => {
            setMode(mode === 'login' ? 'register' : 'login')
            setError('')
          }}>
            {mode === 'login' ? 'Create account' : 'Sign in'}
          </button>
        </p>

        <p className="auth-footer">
          <span>Secured with JWT authentication</span>
        </p>
      </section>
    </main>
  )
}
