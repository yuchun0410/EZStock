import { apiGet, apiPost } from './client'

/**
 * 標的比較：同一筆錢、同一天買進每一檔，持有到結束日。
 * @param request { stockIds, startDate, endDate?, initialCapital, reinvestDividends, includeCosts, feeDiscount? }
 */
export function compareStocks(request) {
  return apiPost('/api/backtests/compare', request)
}

/** 條件回測可以選的條件種類（名稱、單位、範圍、預設值、能不能當買進條件） */
export function getConditionTypes() {
  return apiGet('/api/backtests/conditions')
}

/**
 * 條件回測：照條件買賣，並跟「一直持有」比較。
 * @param request { stockId, startDate, initialCapital, entryConditions, exitConditions, includeCosts, feeDiscount }
 */
export function runStrategy(request) {
  return apiPost('/api/backtests/strategy', request)
}
