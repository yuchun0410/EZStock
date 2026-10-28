import { apiPost } from './client'

/**
 * 標的比較：同一筆錢、同一天買進每一檔，持有到結束日。
 * @param request { stockIds, startDate, endDate?, initialCapital, reinvestDividends, includeCosts, feeDiscount? }
 */
export function compareStocks(request) {
  return apiPost('/api/backtests/compare', request)
}
