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
