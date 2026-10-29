import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { login } from '../api/authApi'
import { useAuth } from '../hooks/useAuth'

/** 登入。成功後回到原本要去的頁面（從哪裡被導來登入，就回哪裡） */
function LoginPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from ?? '/'
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [state, setState] = useState({ status: 'idle', error: null })

  if (isAuthenticated && state.status !== 'loading') {
    return <Navigate to={from} replace />
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setState({ status: 'loading', error: null })
    try {
      await login({ email: email.trim(), password })
      navigate(from, { replace: true })
    } catch (error) {
      setState({ status: 'error', error })
    }
  }

  return (
    <section className="card auth-card">
      <h2 className="section-title">登入</h2>
      <form onSubmit={handleSubmit} className="auth-form">
        <label className="form-field">
          <span className="form-label">Email</span>
          <input
            type="email"
            className="form-input"
            value={email}
            onChange={(event) => setEmail(event.target.value)}
            autoComplete="email"
            required
            autoFocus
          />
        </label>
        <label className="form-field">
          <span className="form-label">密碼</span>
          <input
            type="password"
            className="form-input"
            value={password}
            onChange={(event) => setPassword(event.target.value)}
            autoComplete="current-password"
            required
          />
        </label>

        {state.status === 'error' && (
          <p className="error" role="alert">
            {state.error.message}
          </p>
        )}

        <button type="submit" className="primary-button auth-submit" disabled={state.status === 'loading'}>
          {state.status === 'loading' ? '登入中…' : '登入'}
        </button>
      </form>
      <p className="muted auth-switch">
        還沒有帳號？
        <Link to="/register" state={{ from }}>
          註冊
        </Link>
      </p>
    </section>
  )
}

export default LoginPage
