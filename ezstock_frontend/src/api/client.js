/**
 * 所有呼叫後端的請求都經過這裡，統一處理：
 * 1. 組網址與查詢參數
 * 2. 錯誤轉成 ApiError（畫面只需要顯示 message）
 * 3. 在瀏覽器 Console 印 log，並帶上後端回傳的 traceId，
 *    出問題時可以拿 traceId 去後端 log 找同一個請求。
 */

const TRACE_ID_HEADER = 'X-Trace-Id'

export class ApiError extends Error {
  constructor(message, status, traceId) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.traceId = traceId
  }
}

export async function apiGet(path, params = {}) {
  const url = buildUrl(path, params)
  const start = performance.now()
  console.info(`[API] --> GET ${url}`)

  let response
  try {
    response = await fetch(url, { headers: { Accept: 'application/json' } })
  } catch (error) {
    // 網路斷線、伺服器沒開，fetch 本身就會失敗，拿不到狀態碼
    console.error(`[API] 連線失敗 GET ${url}`, error)
    throw new ApiError('無法連線到伺服器，請稍後再試', 0, null)
  }

  const elapsed = Math.round(performance.now() - start)
  const traceId = response.headers.get(TRACE_ID_HEADER)
  const body = await readJson(response)

  if (!response.ok) {
    // 後端的 GlobalExceptionHandler 會回 { status, error, message, traceId }
    // 沒有 body 通常代表後端沒開（開發時 Vite 代理會直接回 500）
    const message = body?.message ?? '無法連線到伺服器，請稍後再試'
    console.warn(`[API] <-- ${response.status} GET ${url} ${elapsed} ms traceId=${traceId}：${message}`)
    throw new ApiError(message, response.status, traceId ?? body?.traceId ?? null)
  }

  console.info(`[API] <-- ${response.status} GET ${url} ${elapsed} ms traceId=${traceId}`)
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
