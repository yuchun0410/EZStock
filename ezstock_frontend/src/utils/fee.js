/**
 * 手續費折扣的共用規則。畫面上用「折」（6 = 6 折），跟券商的說法一樣；
 * 送給後端時再除以 10 變成比例（0.6）。
 */
export const MIN_DISCOUNT = 1
export const MAX_DISCOUNT = 10

export const DISCOUNT_PRESETS = [
  { value: '10', label: '不打折' },
  { value: '6', label: '6 折' },
  { value: '2.8', label: '2.8 折' },
]

/** 不計手續費時一律有效；計算時必須是 1～10 之間的數字 */
export function isValidDiscount(includeCosts, feeDiscount) {
  if (!includeCosts) return true
  const value = Number(feeDiscount)
  return feeDiscount !== '' && value >= MIN_DISCOUNT && value <= MAX_DISCOUNT
}

/** 6 折 → 0.6；四捨五入到小數 3 位，避免 2.8 / 10 變成 0.27999999999999997。不計手續費時回傳 null */
export function toDiscountRatio(includeCosts, feeDiscount) {
  if (!includeCosts) return null
  return Math.round((Number(feeDiscount) / 10) * 1000) / 1000
}
