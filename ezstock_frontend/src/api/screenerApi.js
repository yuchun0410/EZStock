import { apiPost } from './client'

/**
 * 條件選股：用最新一天的收盤，列出符合條件的股票。
 * @param request { stockIds?, conditions }，stockIds 不給就是資料庫全部
 */
export function screenStocks(request) {
  return apiPost('/api/screener', request)
}
