import { useEffect } from 'react'
import { refreshSession } from '../api/client'
import { useAuthState } from '../auth/authStore'

/**
 * 登入狀態。第一次使用時（剛打開網頁），會用 cookie 試著恢復登入，
 * 所以重新整理頁面不用重新登入（refresh token 有效 7 天）。
 */
export function useAuth() {
  const state = useAuthState()

  useEffect(() => {
    if (state.status === 'checking') {
      refreshSession()
    }
  }, [state.status])

  return {
    status: state.status,
    member: state.member,
    isAuthenticated: state.status === 'authenticated',
  }
}
