import { DISCOUNT_PRESETS, isValidDiscount, MAX_DISCOUNT, MIN_DISCOUNT } from '../utils/fee'

/** 「計算手續費」勾選框 + 折數輸入。標的比較和條件回測共用 */
function FeeSettings({ includeCosts, onIncludeCostsChange, feeDiscount, onFeeDiscountChange }) {
  const valid = isValidDiscount(includeCosts, feeDiscount)

  return (
    <>
      <label className="form-check">
        <input type="checkbox" checked={includeCosts} onChange={(event) => onIncludeCostsChange(event.target.checked)} />
        計算手續費
      </label>
      {includeCosts && (
        <div className="form-field">
          <label className="form-check">
            手續費
            <input
              type="number"
              className="form-input form-input-small"
              value={feeDiscount}
              min={MIN_DISCOUNT}
              max={MAX_DISCOUNT}
              step={0.1}
              inputMode="decimal"
              aria-invalid={!valid}
              onChange={(event) => onFeeDiscountChange(event.target.value)}
            />
            折
          </label>
          <span className="form-presets">
            {DISCOUNT_PRESETS.map((d) => (
              <button key={d.value} type="button" className="chip" onClick={() => onFeeDiscountChange(d.value)}>
                {d.label}
              </button>
            ))}
          </span>
          {!valid && (
            <span className="error form-hint">
              請輸入 {MIN_DISCOUNT}～{MAX_DISCOUNT} 之間的數字（10 代表不打折）
            </span>
          )}
        </div>
      )}
    </>
  )
}

export default FeeSettings
