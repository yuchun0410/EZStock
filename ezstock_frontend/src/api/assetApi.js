import { apiDelete, apiGet, apiPost, apiPut } from './client'

const BASE = '/api/members/me/assets'

/** 資產儀表板。每個修改都回傳最新的儀表板 */
export function getAssetSummary() {
  return apiGet(BASE)
}

/** account：{ kind: 'CASH' | 'LIABILITY', name, balance } */
export function createAccount(account) {
  return apiPost(`${BASE}/accounts`, account)
}

/** 只能改名稱和金額，種類不能改 */
export function updateAccount(id, { name, balance }) {
  return apiPut(`${BASE}/accounts/${id}`, { name, balance })
}

export function deleteAccount(id) {
  return apiDelete(`${BASE}/accounts/${id}`)
}

/** 新增或修改一檔持股：{ shares, averageCost } */
export function saveHolding(stockId, holding) {
  return apiPut(`${BASE}/holdings/${encodeURIComponent(stockId)}`, holding)
}

export function deleteHolding(stockId) {
  return apiDelete(`${BASE}/holdings/${encodeURIComponent(stockId)}`)
}
