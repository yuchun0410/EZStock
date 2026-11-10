import { useEffect, useState } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router'
import { loginWithLine, takeLineLoginReturnTo } from '../api/authApi'

/**
 * LINE 授權完會導回這裡：/login/line/callback?code=…&state=…
 * 使用者在 LINE 按取消時，網址會帶 error=access_denied。
 *
 * 同一個 code 只能換一次 token：開發模式的 StrictMode 會讓 effect 跑兩次，
 * 所以把進行中的請求記在模組層級，第二次直接沿用，不會送兩次。
 */
const inflight = new Map()

function LineCallbackPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const code = params.get('code')
  const state = params.get('state')
  const lineError = params.get('error')
  const [error, setError] = useState(null)

  useEffect(() => {
    if (lineError || !code || !state) return
    let ignore = false
    if (!inflight.has(code)) {
      inflight.set(code, loginWithLine(code, state))
    }
    inflight
      .get(code)
      .then(() => {
        if (!ignore) navigate(takeLineLoginReturnTo(), { replace: true })
      })
      .catch((err) => {
        if (!ignore) setError(err)
      })
    return () => {
      ignore = true
    }
  }, [code, state, lineError, navigate])

  const message = lineError
    ? lineError === 'access_denied'
      ? '你取消了 LINE 登入'
      : 'LINE 登入失敗，請再試一次'
    : !code || !state
      ? '缺少 LINE 登入資料，請重新登入'
      : error?.message

  return (
    <section className="card auth-card">
      <h2 className="section-title">LINE 登入</h2>
      {message ? (
        <>
          <p className="error" role="alert">
            {message}
          </p>
          <p className="auth-switch">
            <Link to="/login">回到登入頁</Link>
          </p>
        </>
      ) : (
        <p className="muted">登入中，請稍候…</p>
      )}
    </section>
  )
}

export default LineCallbackPage
