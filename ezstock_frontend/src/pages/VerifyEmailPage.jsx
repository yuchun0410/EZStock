import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { verifyEmail } from '../api/authApi'
import { useAuth } from '../hooks/useAuth'

/**
 * 驗證信的連結會打開這頁：/verify-email?token=…
 * 一打開就自動把 token 送給後端；不用登入，在手機上打開信也能驗證。
 *
 * token 只能用一次：開發模式的 StrictMode 會讓 effect 跑兩次，
 * 所以跟 LINE 登入一樣，把進行中的請求記在模組層級，第二次直接沿用，不會送兩次。
 */
const inflight = new Map()

function VerifyEmailPage() {
  const [params] = useSearchParams()
  const token = params.get('token')
  const { isAuthenticated } = useAuth()
  const [result, setResult] = useState({ status: token ? 'loading' : 'error', message: null })

  useEffect(() => {
    if (!token) return
    let ignore = false
    if (!inflight.has(token)) {
      inflight.set(token, verifyEmail(token))
    }
    inflight
      .get(token)
      .then(() => {
        if (!ignore) setResult({ status: 'success', message: null })
      })
      .catch((error) => {
        if (!ignore) setResult({ status: 'error', message: error.message })
      })
    return () => {
      ignore = true
    }
  }, [token])

  return (
    <section className="card auth-card">
      <h2 className="section-title">Email 驗證</h2>
      {result.status === 'loading' && <p className="muted">驗證中，請稍候…</p>}

      {result.status === 'success' && (
        <>
          <p>Email 驗證完成，謝謝！</p>
          <p className="auth-switch">
            {isAuthenticated ? <Link to="/assets">前往我的資產</Link> : <Link to="/login">前往登入</Link>}
          </p>
        </>
      )}

      {result.status === 'error' && (
        <>
          <p className="error" role="alert">
            {result.message ?? '缺少驗證資料，請直接點信裡的連結'}
          </p>
          <p className="muted">連結 24 小時內有效，而且只能用一次。登入後可以在頁面上方重寄驗證信。</p>
          <p className="auth-switch">
            <Link to={isAuthenticated ? '/' : '/login'}>{isAuthenticated ? '回到首頁' : '前往登入'}</Link>
          </p>
        </>
      )}
    </section>
  )
}

export default VerifyEmailPage
