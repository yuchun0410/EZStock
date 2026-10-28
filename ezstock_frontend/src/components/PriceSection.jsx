import { useEffect, useState } from 'react'
import { getPrices } from '../api/stockApi'
import { formatSigned, minusMonths, trendClass } from '../utils/format'
import PriceChart from './PriceChart'

const RANGES = [
  { key: '1M', label: '1 個月', months: 1 },
  { key: '3M', label: '3 個月', months: 3 },
  { key: '6M', label: '6 個月', months: 6 },
  { key: '1Y', label: '1 年', months: 12 },
  { key: '5Y', label: '5 年', months: 60 },
]

/** 個股頁的走勢區塊：期間切換按鈕 + 期間漲跌 + 走勢圖 */
function PriceSection({ stockId, latestDate }) {
  const [rangeKey, setRangeKey] = useState('1Y')
  const range = RANGES.find((r) => r.key === rangeKey)
  const from = minusMonths(latestDate, range.months)
  // 用「股票 + 期間」當作這份資料的身分，切換期間時就知道目前的資料過期了
  const requestKey = `${stockId}|${from}|${latestDate}`
  const [result, setResult] = useState({ requestKey: null, prices: [], error: null })

  useEffect(() => {
    let ignore = false
    getPrices(stockId, from, latestDate)
      .then((prices) => {
        if (!ignore) setResult({ requestKey, prices, error: null })
      })
      .catch((error) => {
        if (!ignore) setResult({ requestKey, prices: [], error })
      })
    return () => {
      ignore = true
    }
  }, [stockId, from, latestDate, requestKey])

  const loading = result.requestKey !== requestKey
  const { prices } = result
  const periodChange = prices.length >= 2 ? periodReturn(prices) : null

  return (
    <section className="card price-section">
      <div className="price-section-header">
        <div className="range-tabs" role="tablist" aria-label="走勢期間">
          {RANGES.map((r) => (
            <button
              key={r.key}
              type="button"
              role="tab"
              aria-selected={r.key === rangeKey}
              className={`range-tab ${r.key === rangeKey ? 'active' : ''}`}
              onClick={() => setRangeKey(r.key)}
            >
              {r.label}
            </button>
          ))}
        </div>
        {!loading && periodChange != null && (
          <span className={`period-change ${trendClass(periodChange)}`}>
            {range.label}漲跌 {formatSigned(periodChange)}%
          </span>
        )}
      </div>

      {loading && <div className="chart-placeholder muted">載入走勢中…</div>}
      {!loading && result.error && <div className="chart-placeholder error">{result.error.message}</div>}
      {!loading && !result.error && prices.length === 0 && (
        <div className="chart-placeholder muted">這段期間沒有價格資料</div>
      )}
      {!loading && prices.length > 0 && (
        <PriceChart prices={prices} trend={trendClass(periodChange)} longRange={range.months >= 12} />
      )}
    </section>
  )
}

/** 期間報酬率（%）：(最後收盤 - 第一天收盤) / 第一天收盤 */
function periodReturn(prices) {
  const first = Number(prices[0].close)
  const last = Number(prices[prices.length - 1].close)
  return ((last - first) / first) * 100
}

export default PriceSection
