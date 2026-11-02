import { useEffect, useRef, useState } from 'react'
import { getAuthProviders, loginWithGoogle } from '../api/authApi'

/**
 * Google 官方的登入按鈕（Google Identity Services）。
 *
 * 流程：使用者按按鈕 → Google 跳出選帳號的視窗 → Google 回傳一個 ID Token（credential）
 * → 送到後端 POST /api/auth/google → 後端用 Google 的公鑰驗證後，發我們自己的 JWT。
 *
 * 前端只負責拿 ID Token，不自己解析、不信任它的內容，驗證全部在後端。
 */

const SCRIPT_URL = 'https://accounts.google.com/gsi/client'

// 這些只需要做一次：抓設定、載入 Google 的程式、initialize
let providersPromise = null
let scriptPromise = null
let initializedClientId = null
// Google 的 callback 只能在 initialize 時設定一次，所以透過這個變數轉給目前畫面上的按鈕
let currentHandler = null

function loadProviders() {
  if (!providersPromise) {
    providersPromise = getAuthProviders().catch((error) => {
      providersPromise = null
      throw error
    })
  }
  return providersPromise
}

function loadScript() {
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const script = document.createElement('script')
      script.src = SCRIPT_URL
      script.async = true
      script.onload = () => resolve(window.google)
      script.onerror = () => {
        scriptPromise = null
        reject(new Error('無法載入 Google 登入，請檢查網路或擋廣告的外掛'))
      }
      document.head.appendChild(script)
    })
  }
  return scriptPromise
}

function GoogleLoginButton({ onSuccess }) {
  const containerRef = useRef(null)
  const [state, setState] = useState({ status: 'loading', error: null })

  useEffect(() => {
    let ignore = false

    currentHandler = async (response) => {
      setState({ status: 'submitting', error: null })
      try {
        const member = await loginWithGoogle(response.credential)
        onSuccess?.(member)
      } catch (error) {
        setState({ status: 'ready', error })
      }
    }

    Promise.all([loadProviders(), loadScript()])
      .then(([providers, google]) => {
        if (ignore) return
        if (!providers.googleClientId) {
          console.info('[登入] 後端沒有開放 Google 登入，不顯示按鈕')
          setState({ status: 'disabled', error: null })
          return
        }
        if (initializedClientId !== providers.googleClientId) {
          google.accounts.id.initialize({
            client_id: providers.googleClientId,
            callback: (response) => currentHandler?.(response),
          })
          initializedClientId = providers.googleClientId
        }
        const width = Math.min(containerRef.current.offsetWidth || 320, 400)
        google.accounts.id.renderButton(containerRef.current, {
          theme: 'outline',
          size: 'large',
          text: 'continue_with',
          shape: 'rectangular',
          locale: 'zh-TW',
          width,
        })
        setState({ status: 'ready', error: null })
      })
      .catch((error) => {
        if (ignore) return
        console.warn('[登入] Google 登入按鈕載入失敗', error)
        setState({ status: 'failed', error })
      })

    return () => {
      ignore = true
    }
  }, [onSuccess])

  if (state.status === 'disabled') {
    return null
  }

  return (
    <div className="google-login">
      <div className="auth-divider">
        <span>或</span>
      </div>
      <div ref={containerRef} className="google-button" aria-busy={state.status !== 'ready'} />
      {state.status === 'loading' && <p className="muted form-hint">載入 Google 登入…</p>}
      {state.status === 'submitting' && <p className="muted form-hint">Google 登入中…</p>}
      {state.error && (
        <p className="error form-hint" role="alert">
          {state.error.message}
        </p>
      )}
    </div>
  )
}

export default GoogleLoginButton
