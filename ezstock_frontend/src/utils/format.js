/** 數字與日期的顯示格式，各頁面共用 */

const priceFormat = new Intl.NumberFormat('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
const integerFormat = new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 })

/** 2400 → "2,400.00" */
export function formatPrice(value) {
  if (value == null) return '-'
  return priceFormat.format(Number(value))
}

/** 15 → "+15.00"、-3.2 → "-3.20" */
export function formatSigned(value) {
  if (value == null) return '-'
  const number = Number(value)
  return (number > 0 ? '+' : '') + priceFormat.format(number)
}

/** 成交量：FinMind 給的是「股」，台股習慣看「張」（1 張 = 1000 股） */
export function formatLots(shares) {
  if (shares == null) return '-'
  return integerFormat.format(Math.round(Number(shares) / 1000))
}

/**
 * 美股或台股：台股代號一定是數字開頭（2330、0050），美股是英文字母（AAPL、BRK-B）。
 * 自選股、搜尋結果這些地方只有代號，用這個判斷就不用每個 API 都多回傳市場別。
 */
export function isUsStock(stockId) {
  return /^[A-Za-z]/.test(stockId ?? '')
}

/** 價格前面的幣別符號：美股 "US$"，台股不加 */
export function currencyPrefix(stockId) {
  return isUsStock(stockId) ? 'US$' : ''
}

/** 金額單位：美股「美元」、台股「元」 */
export function moneyUnit(stockIdOrCurrency) {
  return stockIdOrCurrency === 'USD' || (stockIdOrCurrency !== 'TWD' && isUsStock(stockIdOrCurrency)) ? '美元' : '元'
}

/** 成交量含單位：台股 "12,345 張"；美股看「股」，數字大用萬、億（"7,569 萬股"） */
export function formatVolume(shares, stockId) {
  if (shares == null) return '-'
  if (!isUsStock(stockId)) return `${formatLots(shares)} 張`
  const n = Number(shares)
  if (n >= 1e8) return `${priceFormat.format(n / 1e8)} 億股`
  if (n >= 1e4) return `${integerFormat.format(Math.round(n / 1e4))} 萬股`
  return `${integerFormat.format(n)} 股`
}

/** 漲用紅、跌用綠（對應 index.css 的 .up / .down） */
export function trendClass(change) {
  const number = Number(change)
  if (number > 0) return 'up'
  if (number < 0) return 'down'
  return ''
}

/** "2026-10-27" 往前推 months 個月，回傳同樣格式的字串 */
export function minusMonths(isoDate, months) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1 - months, day)
  return toIsoDate(date)
}

export function toIsoDate(date) {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

const moneyFormat = new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 })

/** 1234567 → "1,234,567" */
export function formatMoney(value) {
  if (value == null) return '-'
  return moneyFormat.format(Number(value))
}

/** 圖表座標軸用：1234567 → "123 萬" */
export function formatTenThousands(value) {
  return `${moneyFormat.format(Number(value) / 10000)} 萬`
}

/** 金額的漲跌（不顯示小數）：1420000 → "+1,420,000"、-500 → "-500" */
export function formatSignedMoney(value) {
  if (value == null) return '-'
  const number = Number(value)
  return (number > 0 ? '+' : '') + moneyFormat.format(number)
}

/** 後端的 "2026-11-02T14:10:32.035" → "2026/11/02 14:10"（後端已經是台灣時間，直接切字串，不經過時區轉換） */
export function formatDateTime(value) {
  if (!value) return '-'
  const [date, time = ''] = String(value).split('T')
  return `${date.replaceAll('-', '/')} ${time.slice(0, 5)}`.trim()
}
