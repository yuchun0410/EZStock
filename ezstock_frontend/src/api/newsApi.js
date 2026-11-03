import { apiGet } from './client'

/** 個股相關新聞：stockIds 是代號陣列（最多 20 檔），由新到舊 */
export function getNews(stockIds, limit = 20) {
  return apiGet('/api/news', { stockIds: stockIds.join(','), limit })
}
