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

/** Google 登入：把 Google 按鈕給的 ID Token 交給後端驗證，後端發我們自己的 JWT */
export async function loginWithGoogle(credential) {
  const tokenResponse = await apiPost('/api/auth/google', { credential })
  setSession(tokenResponse)
  console.info(`[登入] Google 登入成功，會員 id=${tokenResponse.member.id}`)
  return tokenResponse.member
}

/**
 * 有開放哪些登入方式：{ googleClientId, lineLoginEnabled }，googleClientId 是 null 代表沒開放 Google 登入。
 * 登入頁的 Google、LINE 按鈕都要用，只抓一次；失敗的話下次再重抓。
 */
let providersPromise = null
export function getAuthProviders() {
  if (!providersPromise) {
    providersPromise = apiGet('/api/auth/providers').catch((error) => {
      providersPromise = null
      throw error
    })
  }
  return providersPromise
}

/**
 * LINE 登入第一步：跟後端要 LINE 的登入網址（後端同時把 state 放進 HttpOnly cookie），再整頁導過去。
 * 登入完要回到哪一頁，先記在 sessionStorage；LINE 導回來時用。
 */
export async function startLineLogin(returnTo) {
  const { url } = await apiGet('/api/auth/line/authorize')
  try {
    window.sessionStorage.setItem(LINE_RETURN_KEY, returnTo ?? '/')
  } catch {
    // 無痕模式或封鎖網站資料時存不了，登入完就回首頁
  }
  console.info('[登入] 導向 LINE 登入')
  window.location.assign(url)
}

/** LINE 登入第二步：LINE 導回 /login/line/callback 後，把網址上的 code、state 交給後端 */
export async function loginWithLine(code, state) {
  const tokenResponse = await apiPost('/api/auth/line', { code, state })
  setSession(tokenResponse)
  console.info(`[登入] LINE 登入成功，會員 id=${tokenResponse.member.id}`)
  return tokenResponse.member
}

const LINE_RETURN_KEY = 'ezstock.lineLoginReturnTo'

/** 取出並清掉 LINE 登入前記下的頁面；只接受站內路徑，避免被導到別的網站（open redirect） */
export function takeLineLoginReturnTo() {
  try {
    const value = window.sessionStorage.getItem(LINE_RETURN_KEY)
    window.sessionStorage.removeItem(LINE_RETURN_KEY)
    return value && value.startsWith('/') && !value.startsWith('//') ? value : '/'
  } catch {
    return '/'
  }
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
