import { useEffect, useState } from 'react'
import { useLocation } from 'react-router'
import { resendVerification } from '../api/authApi'
import { useAuth } from '../hooks/useAuth'

const COOLDOWN_SECONDS = 60

/**
 * 已登入、有 Email 但還沒驗證時，在每一頁上方提醒，並可以重寄驗證信。
 * 重寄後 60 秒內按鈕不能按（後端也會擋，這裡只是讓使用者看得到還要等多久）。
 */
function EmailVerifyBanner() {
  const { isAuthenticated, member } = useAuth()
  const { pathname } = useLocation()
  const [status, setStatus] = useState('idle')
  const [message, setMessage] = useState(null)
  const [secondsLeft, setSecondsLeft] = useState(0)

  useEffect(() => {
    if (secondsLeft <= 0) return
    const timer = setTimeout(() => setSecondsLeft((s) => s - 1), 1000)
    return () => clearTimeout(timer)
  }, [secondsLeft])

  if (!isAuthenticated || !member?.email || member.emailVerified || pathname === '/verify-email') {
    return null
  }

  async function handleResend() {
    setStatus('sending')
    setMessage(null)
    try {
      await resendVerification()
      setStatus('sent')
      setSecondsLeft(COOLDOWN_SECONDS)
    } catch (error) {
      setStatus('error')
      setMessage(error.message)
    }
  }

  const disabled = status === 'sending' || secondsLeft > 0

  return (
    <div className="notice verify-banner" role="status">
      <span>
        請到 <strong>{member.email}</strong> 收信，點信裡的連結完成 Email 驗證。
        {status === 'sent' && ' 已重新寄出，沒看到的話也找找垃圾郵件。'}
        {status === 'error' && message && <span className="error"> {message}</span>}
      </span>
      <button type="button" className="link-button verify-resend" onClick={handleResend} disabled={disabled}>
        {status === 'sending' ? '寄送中…' : secondsLeft > 0 ? `${secondsLeft} 秒後可重寄` : '重寄驗證信'}
      </button>
    </div>
  )
}

export default EmailVerifyBanner
