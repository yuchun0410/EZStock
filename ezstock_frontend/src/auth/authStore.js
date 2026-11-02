import { useSyncExternalStore } from 'react'

/**
 * 登入狀態，所有元件共用同一份（跟自選股一樣用 useSyncExternalStore）。
 *
 * access token 只放在這個變數裡（記憶體），不放 localStorage：
 * localStorage 任何 JavaScript 都讀得到，網站一旦被 XSS 就會被偷走。
 * 重新整理頁面後記憶體會清空，這時候用 HttpOnly cookie 裡的 refresh token 換一個新的就好。
 *
 * status：
 * - checking：剛打開網頁，正在用 cookie 確認是不是已經登入
 * - authenticated：已登入
 * - anonymous：沒登入
 */

const listeners = new Set()
let accessToken = null
let state = { status: 'checking', member: null }

function emit() {
  listeners.forEach((listener) => listener())
}

function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot() {
  return state
}

/** 登入、註冊、換 token 成功後呼叫，傳入後端的 TokenResponse */
export function setSession(tokenResponse) {
  accessToken = tokenResponse.accessToken
  state = { status: 'authenticated', member: tokenResponse.member }
  emit()
}

export function clearSession() {
  accessToken = null
  if (state.status !== 'anonymous' || state.member !== null) {
    state = { status: 'anonymous', member: null }
    emit()
  }
}

export function getAccessToken() {
  return accessToken
}

export function useAuthState() {
  return useSyncExternalStore(subscribe, getSnapshot)
}
