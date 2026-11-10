import { useEffect, useState } from 'react'
import { getAuthProviders, startLineLogin } from '../api/authApi'

/**
 * 「用 LINE 登入」按鈕。後端沒開放 LINE 登入時不顯示。
 * 按下去會整頁導到 LINE；登入完 LINE 會導回 /login/line/callback（LineCallbackPage）。
 */
function LineLoginButton({ returnTo }) {
  const [providers, setProviders] = useState(null)
  const [state, setState] = useState({ status: 'idle', error: null })

  useEffect(() => {
    let ignore = false
    getAuthProviders()
      .then((result) => {
        if (!ignore) setProviders(result)
      })
      .catch((error) => console.warn('[登入] 讀取登入方式失敗', error))
    return () => {
      ignore = true
    }
  }, [])

  if (!providers?.lineLoginEnabled) {
    return null
  }

  async function handleClick() {
    setState({ status: 'redirecting', error: null })
    try {
      await startLineLogin(returnTo)
    } catch (error) {
      setState({ status: 'idle', error })
    }
  }

  return (
    <div className="line-login">
      {/* Google 登入沒開放時，Google 按鈕那裡不會有「或」的分隔線，由這裡補上 */}
      {!providers.googleClientId && (
        <div className="auth-divider">
          <span>或</span>
        </div>
      )}
      <button
        type="button"
        className="line-button"
        onClick={handleClick}
        disabled={state.status === 'redirecting'}
      >
        <svg className="line-button-icon" viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 3C6.5 3 2 6.6 2 11.1c0 4 3.6 7.4 8.4 8 .3.1.8.2.9.5.1.3.1.7 0 1l-.1.9c0 .3-.2 1 .9.5 1.1-.5 5.9-3.5 8-5.9C21.5 14.4 22 12.8 22 11.1 22 6.6 17.5 3 12 3Z" />
        </svg>
        {state.status === 'redirecting' ? '前往 LINE…' : '用 LINE 登入'}
      </button>
      {state.error && (
        <p className="error form-hint" role="alert">
          {state.error.message}
        </p>
      )}
    </div>
  )
}

export default LineLoginButton
