import { useEffect, useState } from 'react'
import { Area, AreaChart, ResponsiveContainer, YAxis } from 'recharts'
import { getMarketIndices } from '../api/marketApi'
import { formatPrice, formatSigned, trendClass } from '../utils/format'
import { CHART_COLORS } from './chartConfig'

/**
 * 首頁上方的市場概況：台股加權指數，加上美股三大指數（道瓊、S&P 500、那斯達克）。
 * 後端一次回傳四個指數（記憶體快取 30 分鐘），每格顯示最新收盤、跟前一天比的漲跌、近三個月走勢。
 * 指數不是股票，沒有個股頁，所以卡片不能點。
 */
function MarketOverview() {
  const [state, setState] = useState({ status: 'loading', quotes: [] })

  useEffect(() => {
    let ignore = false
    getMarketIndices()
      .then((quotes) => {
        if (!ignore) setState({ status: 'done', quotes })
      })
      .catch((error) => {
        // 市場概況只是輔助資訊，抓不到不影響首頁其他區塊
        console.warn(`[首頁] 市場概況載入失敗：${error.message}`)
        if (!ignore) setState({ status: 'error', quotes: [] })
      })
    return () => {
      ignore = true
    }
  }, [])

  if (state.status === 'error') return null

  return (
    <section className="market-overview" aria-label="市場概況" aria-busy={state.status === 'loading'}>
      {state.status === 'loading'
        ? [0, 1, 2, 3].map((i) => <div key={i} className="market-tile is-loading" aria-hidden="true" />)
        : state.quotes.map((quote) => <MarketTile key={quote.code} quote={quote} />)}
    </section>
  )
}

function MarketTile({ quote }) {
  const trend = trendClass(quote.change)
  const color = trend === 'up' ? CHART_COLORS.up : trend === 'down' ? CHART_COLORS.down : CHART_COLORS.flat
  const gradientId = `market-${quote.code.replace(/\W/g, '')}`
  const points = quote.points.map((p) => ({ date: p.date, close: Number(p.close) }))

  return (
    <div className={`market-tile ${trend}`}>
      <div className="market-tile-head">
        <span className="market-tile-name">{quote.name}</span>
        <span className="muted">{quote.note}</span>
      </div>
      {quote.close == null ? (
        <div className="market-tile-empty muted">暫時無法顯示</div>
      ) : (
        <>
          <div className="market-tile-price">
            <span className="market-tile-close">{formatPrice(quote.close)}</span>
            {quote.change != null && (
              <span className={`market-tile-change ${trend}`}>
                {formatSigned(quote.change)}（{formatSigned(quote.changePercent)}%）
              </span>
            )}
          </div>
          <div className="market-tile-chart" aria-hidden="true">
            {points.length >= 2 && (
              <ResponsiveContainer width="100%" height={44}>
                <AreaChart data={points} margin={{ top: 2, right: 0, bottom: 0, left: 0 }}>
                  <defs>
                    <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
                      <stop offset="0%" stopColor={color} stopOpacity={0.35} />
                      <stop offset="100%" stopColor={color} stopOpacity={0} />
                    </linearGradient>
                  </defs>
                  <YAxis hide domain={['dataMin', 'dataMax']} />
                  <Area
                    type="monotone"
                    dataKey="close"
                    stroke={color}
                    strokeWidth={1.5}
                    fill={`url(#${gradientId})`}
                    isAnimationActive={false}
                  />
                </AreaChart>
              </ResponsiveContainer>
            )}
          </div>
          <div className="market-tile-date muted">{quote.date} 收盤</div>
        </>
      )}
    </div>
  )
}

export default MarketOverview
