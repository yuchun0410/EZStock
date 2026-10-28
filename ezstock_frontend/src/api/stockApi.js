import { apiGet } from './client'

/** 依代號或名稱搜尋股票，最多 20 筆 */
export function searchStocks(keyword) {
  return apiGet('/api/stocks', { keyword })
}

/** 個股最新行情（收盤、漲跌、漲跌幅） */
export function getStock(stockId) {
  return apiGet(`/api/stocks/${encodeURIComponent(stockId)}`)
}

/** 個股日線，from / to 是 yyyy-MM-dd，不帶就由後端預設最近一年 */
export function getPrices(stockId, from, to) {
  return apiGet(`/api/stocks/${encodeURIComponent(stockId)}/prices`, { from, to })
}
