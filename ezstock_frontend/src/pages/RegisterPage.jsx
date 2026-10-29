import { useState } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router'
import { register } from '../api/authApi'
import { useAuth } from '../hooks/useAuth'

const MIN_PASSWORD = 8

/** 註冊，成功後直接登入 */
function RegisterPage() {
  const { isAuthenticated } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = location.state?.from ?? '/'
  const [form, setForm] = useState({ displayName: '', email: '', password: '', confirm: '' })
  const [state, setState] = useState({ status: 'idle', error: null })

  if (isAuthenticated && state.status !== 'loading') {
    return <Navigate to={from} replace />
  }

  const tooShort = form.password.length > 0 && form.password.length < MIN_PASSWORD
  const mismatch = form.confirm.length > 0 && form.confirm !== form.password
  const canSubmit =
    form.displayName.trim() &&
    form.email.trim() &&
    form.password.length >= MIN_PASSWORD &&
    form.confirm === form.password &&
    state.status !== 'loading'

  function update(field) {
    return (event) => setForm((current) => ({ ...current, [field]: event.target.value }))
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!canSubmit) return
    setState({ status: 'loading', error: null })
    try {
      await register({ email: form.email.trim(), password: form.password, displayName: form.displayName.trim() })
      navigate(from, { replace: true })
    } catch (error) {
      setState({ status: 'error', error })
    }
  }

  return (
    <section className="card auth-card">
      <h2 className="section-title">註冊</h2>
      <form onSubmit={handleSubmit} className="auth-form">
        <label className="form-field">
          <span className="form-label">暱稱</span>
          <input
            className="form-input"
            value={form.displayName}
            onChange={update('displayName')}
            maxLength={50}
            autoComplete="nickname"
            required
            autoFocus
          />
        </label>
        <label className="form-field">
          <span className="form-label">Email</span>
          <input
            type="email"
            className="form-input"
            value={form.email}
            onChange={update('email')}
            autoComplete="email"
            required
          />
        </label>
        <label className="form-field">
          <span className="form-label">密碼</span>
          <input
            type="password"
            className="form-input"
            value={form.password}
            onChange={update('password')}
            minLength={MIN_PASSWORD}
            maxLength={64}
            autoComplete="new-password"
            aria-invalid={tooShort}
            required
          />
          <span className={`form-hint ${tooShort ? 'error' : 'muted'}`}>至少 {MIN_PASSWORD} 個字</span>
        </label>
        <label className="form-field">
          <span className="form-label">確認密碼</span>
          <input
            type="password"
            className="form-input"
            value={form.confirm}
            onChange={update('confirm')}
            autoComplete="new-password"
            aria-invalid={mismatch}
            required
          />
          {mismatch && <span className="form-hint error">兩次輸入的密碼不一樣</span>}
        </label>

        {state.status === 'error' && (
          <p className="error" role="alert">
            {state.error.message}
          </p>
        )}

        <button type="submit" className="primary-button auth-submit" disabled={!canSubmit}>
          {state.status === 'loading' ? '註冊中…' : '註冊'}
        </button>
      </form>
      <p className="muted auth-switch">
        已經有帳號？
        <Link to="/login" state={{ from }}>
          登入
        </Link>
      </p>
    </section>
  )
}

export default RegisterPage
