import { apiDelete, apiGet, apiPost, apiPut } from './client'

const BASE = '/api/members/me/watchlist'

/** 登入後的自選股（存在資料庫）。每個函式都回傳最新的完整清單 */
export function getWatchlist() {
  return apiGet(BASE)
}

export function addToWatchlist(stockId) {
  return apiPut(`${BASE}/${encodeURIComponent(stockId)}`)
}

export function removeFromWatchlist(stockId) {
  return apiDelete(`${BASE}/${encodeURIComponent(stockId)}`)
}

/** 把瀏覽器裡的自選股匯入帳號 */
export function importWatchlist(stockIds) {
  return apiPost(`${BASE}/import`, { stockIds })
}
