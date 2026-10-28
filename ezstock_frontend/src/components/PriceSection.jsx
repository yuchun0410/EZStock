import { useEffect, useState } from 'react'
import { getPrices } from '../api/stockApi'
import { toChartPoints, toWeekly } from '../utils/chartData'
import { formatSigned, minusMonths, trendClass } from '../utils/format'
import PriceChart from './PriceChart'
import VolumeChart from './VolumeChart'

const RANGES = [
  { key: '1M', label: '1 個月', months: 1 },
  { key: '3M', label: '3 個月', months: 3 },
  { key: '6M', label: '6 個月', months: 6 },
  { key: '1Y', label: '1 年', months: 12 },
  { key: '5Y', label: '5 年', months: 60 },
]

const MODES = [
  { key: 'line', label: '折線' },
  { key: 'candle', label: 'K 線' },
]

// 超過這個月數，K 線改用週 K
const WEEKLY_FROM_MONTHS = 60

/** 個股頁的走勢區塊：期間與圖形切換 + 期間漲跌 + 價格圖 + 成交量 */
function PriceSection({ stockId, latestDate }) {
  const [rangeKey, setRangeKey] = useState('1Y')
  const [mode, setMode] = useState('line')
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
  const daily = toChartPoints(result.prices)
  const weekly = mode === 'candle' && range.months >= WEEKLY_FROM_MONTHS
  const points = weekly ? toWeekly(daily) : daily
  const periodChange = daily.length >= 2 ? periodReturn(daily) : null

  return (
    <section className="card price-section">
      <div className="price-section-header">
        <div className="toolbar">
          <SegmentedControl label="走勢期間" options={RANGES} value={rangeKey} onChange={setRangeKey} />
          <SegmentedControl label="圖形" options={MODES} value={mode} onChange={setMode} />
        </div>
        {!loading && periodChange != null && (
          <span className={`period-change ${trendClass(periodChange)}`}>
            {range.label}漲跌 {formatSigned(periodChange)}%
          </span>
        )}
      </div>

      {loading && <div className="chart-placeholder muted">載入走勢中…</div>}
      {!loading && result.error && <div className="chart-placeholder error">{result.error.message}</div>}
      {!loading && !result.error && points.length === 0 && (
        <div className="chart-placeholder muted">這段期間沒有價格資料</div>
      )}
      {!loading && points.length > 0 && (
        <>
          <PriceChart
            points={points}
            mode={mode}
            trend={trendClass(periodChange)}
            longRange={range.months >= 12}
            weekly={weekly}
          />
          <VolumeChart points={points} />
          {weekly && <p className="chart-caption muted">5 年期間的 K 線以週 K 顯示</p>}
        </>
      )}
    </section>
  )
}

function SegmentedControl({ label, options, value, onChange }) {
  return (
    <div className="range-tabs" role="tablist" aria-label={label}>
      {options.map((option) => (
        <button
          key={option.key}
          type="button"
          role="tab"
          aria-selected={option.key === value}
          className={`range-tab ${option.key === value ? 'active' : ''}`}
          onClick={() => onChange(option.key)}
        >
          {option.label}
        </button>
      ))}
    </div>
  )
}

/** 期間報酬率（%）：(最後收盤 - 第一天收盤) / 第一天收盤 */
function periodReturn(points) {
  const first = points[0].close
  const last = points[points.length - 1].close
  return ((last - first) / first) * 100
}

export default PriceSection
