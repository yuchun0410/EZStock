import { clearSession, setSession } from '../auth/authStore'
import { apiGet, apiPost } from './client'

/** 註冊，成功會直接登入。request：{ email, password, displayName } */
export async function register(request) {
  const tokenResponse = await apiPost('/api/auth/register', request)
  setSession(tokenResponse)
  console.info(`[登入] 註冊成功，會員 id=${tokenResponse.member.id}`)
  return tokenResponse.member
}

/** 登入。request：{ email, password } */
export async function login(request) {
  const tokenResponse = await apiPost('/api/auth/login', request)
  setSession(tokenResponse)
  console.info(`[登入] 登入成功，會員 id=${tokenResponse.member.id}`)
  return tokenResponse.member
}

/** 登出：後端作廢 refresh token、把 access token 加入黑名單；就算後端失敗，前端也一律清掉登入狀態 */
export async function logout() {
  try {
    await apiPost('/api/auth/logout')
  } catch (error) {
    console.warn('[登入] 登出請求失敗，仍清除本機登入狀態', error)
  } finally {
    clearSession()
    console.info('[登入] 已登出')
  }
}

/** 自己的會員資料（需要登入） */
export function getMe() {
  return apiGet('/api/members/me')
}
