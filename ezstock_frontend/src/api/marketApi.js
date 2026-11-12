import { apiGet } from './client'

/** 市場概況：加權指數、道瓊、S&P 500、那斯達克的最新行情與近三個月走勢 */
export function getMarketIndices() {
  return apiGet('/api/market/indices')
}

/** 最近一個交易日的三大法人買賣金額：{ date, groups: [{ name, buy, sell, net }], total } */
export function getInstitutional() {
  return apiGet('/api/market/institutional')
}
