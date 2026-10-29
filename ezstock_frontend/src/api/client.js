/**
 * 所有呼叫後端的請求都經過這裡，統一處理：
 * 1. 組網址與查詢參數
 * 2. 錯誤轉成 ApiError（畫面只需要顯示 message）
 * 3. 在瀏覽器 Console 印 log，並帶上後端回傳的 traceId，
 *    出問題時可以拿 traceId 去後端 log 找同一個請求。
 * 4. 有登入就自動帶 Authorization: Bearer <access token>；
 *    收到 401 時，先用 cookie 裡的 refresh token 換一個新的，再重送一次。
 *
 * token 只會出現在 header，Console 的 log 不印 token。
 */

import { clearSession, getAccessToken, setSession } from '../auth/authStore'

const TRACE_ID_HEADER = 'X-Trace-Id'
const AUTH_PATH = '/api/auth/'

export class ApiError extends Error {
  constructor(message, status, traceId) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.traceId = traceId
  }
}

export function apiGet(path, params = {}) {
  return request('GET', buildUrl(path, params))
}

/** 送出 JSON。body 會轉成 JSON 字串，Content-Type 設成 application/json */
export function apiPost(path, payload) {
  return request('POST', path, payload)
}

// 同一時間只換一次 token：好幾個請求同時收到 401，或 React StrictMode 讓 effect 跑兩次時，
// 如果各自去換，第二次會拿已經作廢的舊 token，後端會以為 token 被盜用，把所有裝置都登出
let refreshing = null

/**
 * 用 HttpOnly cookie 裡的 refresh token 換新的 access token。
 * 成功回傳 true；失敗（沒登入過、過期、被登出）回傳 false 並清掉登入狀態。
 */
export function refreshSession() {
  if (!refreshing) {
    refreshing = request('POST', `${AUTH_PATH}refresh`, undefined, { retry: false })
      .then((tokenResponse) => {
        setSession(tokenResponse)
        console.info(`[登入] 換發 token 成功，會員 id=${tokenResponse.member.id}`)
        return true
      })
      .catch((error) => {
        console.info(`[登入] 沒有有效的登入狀態（${error.status}）`)
        clearSession()
        return false
      })
      .finally(() => {
        refreshing = null
      })
  }
  return refreshing
}

async function request(method, url, payload, { retry = true } = {}) {
  // 網址裡的中文會被編碼成 %E5%8F%B0...，log 顯示解碼後的版本比較好讀
  const label = `${method} ${decodeURIComponent(url)}`
  const start = performance.now()
  console.info(`[API] --> ${label}`)

  const options = { method, headers: { Accept: 'application/json' } }
  const token = getAccessToken()
  if (token) {
    options.headers.Authorization = `Bearer ${token}`
  }
  if (payload !== undefined) {
    options.headers['Content-Type'] = 'application/json'
    options.body = JSON.stringify(payload)
  }

  let response
  try {
    response = await fetch(url, options)
  } catch (error) {
    // 網路斷線、伺服器沒開，fetch 本身就會失敗，拿不到狀態碼
    console.error(`[API] 連線失敗 ${label}`, error)
    throw new ApiError('無法連線到伺服器，請稍後再試', 0, null)
  }

  const elapsed = Math.round(performance.now() - start)
  const traceId = response.headers.get(TRACE_ID_HEADER)
  const body = await readJson(response)

  // access token 過期（15 分鐘）：換一個新的再重送一次。登入、註冊這些 /api/auth/ 的 401 是帳密錯誤，不用換
  if (response.status === 401 && retry && !url.startsWith(AUTH_PATH)) {
    console.info(`[API] <-- 401 ${label}，嘗試換發 token 後重送`)
    if (await refreshSession()) {
      return request(method, url, payload, { retry: false })
    }
  }

  if (!response.ok) {
    // 後端的 GlobalExceptionHandler 會回 { status, error, message, traceId }
    // 沒有 body 通常代表後端沒開（開發時 Vite 代理會直接回 500）
    const message = body?.message ?? '無法連線到伺服器，請稍後再試'
    console.warn(`[API] <-- ${response.status} ${label} ${elapsed} ms traceId=${traceId}：${message}`)
    throw new ApiError(message, response.status, traceId ?? body?.traceId ?? null)
  }

  console.info(`[API] <-- ${response.status} ${label} ${elapsed} ms traceId=${traceId}`)
  return body
}

function buildUrl(path, params) {
  const query = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      query.append(key, value)
    }
  }
  const queryString = query.toString()
  return queryString ? `${path}?${queryString}` : path
}

async function readJson(response) {
  const text = await response.text()
  if (!text) {
    return null
  }
  try {
    return JSON.parse(text)
  } catch {
    return null
  }
}
