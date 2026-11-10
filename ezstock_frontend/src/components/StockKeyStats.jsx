import { useEffect, useState } from 'react'
import { getPrices } from '../api/stockApi'
import { toChartPoints } from '../utils/chartData'
import { formatPrice, formatVolume, minusMonths } from '../utils/format'

/**
 * 個股頁上方的重點數據：當日開高低、振幅、成交量，以及 52 週高低區間條。
 * 抓最近一年的日線自己算，不用另外開後端 API。
 */
function StockKeyStats({ stockId, latestDate, latestClose, change }) {
  const from = minusMonths(latestDate, 12)
  const requestKey = `${stockId}|${from}|${latestDate}`
  const [result, setResult] = useState({ requestKey: null, points: [] })

  useEffect(() => {
    let ignore = false
    getPrices(stockId, from, latestDate)
      .then((prices) => {
        if (!ignore) setResult({ requestKey, points: toChartPoints(prices) })
      })
      .catch((error) => {
        // 重點數據只是輔助資訊，抓不到就不顯示，不影響整頁
        console.warn(`[個股] 重點數據載入失敗：${error.message}`)
        if (!ignore) setResult({ requestKey, points: [] })
      })
    return () => {
      ignore = true
    }
  }, [stockId, from, latestDate, requestKey])

  if (result.requestKey !== requestKey) {
    return <div className="key-stats is-loading" aria-hidden="true" />
  }
  if (result.points.length === 0) {
    return null
  }

  const points = result.points
  const today = points[points.length - 1]
  const previousClose = Number(latestClose) - Number(change ?? 0)
  const yearHigh = Math.max(...points.map((p) => p.high))
  const yearLow = Math.min(...points.map((p) => p.low))
  const amplitude = previousClose > 0 ? ((today.high - today.low) / previousClose) * 100 : null

  const stats = [
    { label: '開盤', value: formatPrice(today.open), trend: compare(today.open, previousClose) },
    { label: '最高', value: formatPrice(today.high), trend: compare(today.high, previousClose) },
    { label: '最低', value: formatPrice(today.low), trend: compare(today.low, previousClose) },
    { label: '昨收', value: formatPrice(previousClose) },
    { label: '振幅', value: amplitude == null ? '-' : `${formatPrice(amplitude)}%` },
    { label: '成交量', value: formatVolume(today.volume, stockId) },
  ]

  return (
    <div className="key-stats">
      <dl className="key-stats-grid">
        {stats.map((stat) => (
          <div key={stat.label} className="key-stat">
            <dt className="muted">{stat.label}</dt>
            <dd className={stat.trend ?? ''}>{stat.value}</dd>
          </div>
        ))}
      </dl>
      <YearRange low={yearLow} high={yearHigh} close={Number(latestClose)} />
    </div>
  )
}

/** 52 週區間條：左端最低、右端最高，圓點標出現在價格的位置 */
function YearRange({ low, high, close }) {
  const position = high > low ? ((close - low) / (high - low)) * 100 : 50
  const clamped = Math.min(100, Math.max(0, position))
  const fromHigh = high > 0 ? ((close - high) / high) * 100 : null

  return (
    <div className="year-range">
      <div className="year-range-labels">
        <span className="muted">52 週區間</span>
        {fromHigh != null && <span className="muted">距高點 {formatPrice(fromHigh)}%</span>}
      </div>
      <div
        className="year-range-track"
        role="img"
        aria-label={`52 週最低 ${formatPrice(low)}、最高 ${formatPrice(high)}，現價位於區間的 ${Math.round(clamped)}%`}
      >
        <span className="year-range-fill" style={{ width: `${clamped}%` }} />
        <span className="year-range-marker" style={{ left: `${clamped}%` }} />
      </div>
      <div className="year-range-labels year-range-values">
        <span>{formatPrice(low)}</span>
        <span>{formatPrice(high)}</span>
      </div>
    </div>
  )
}

/** 跟昨收比：高於昨收紅、低於昨收綠（看盤軟體的慣例） */
function compare(value, previousClose) {
  if (value > previousClose) return 'up'
  if (value < previousClose) return 'down'
  return null
}

export default StockKeyStats
