import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Area, AreaChart, ResponsiveContainer, YAxis } from 'recharts'
import { getPrices } from '../api/stockApi'
import { currencyPrefix, formatPrice, formatSigned, minusMonths, toIsoDate, trendClass } from '../utils/format'
import { CHART_COLORS } from './chartConfig'

/**
 * 首頁上方的市場概況：台股、美股各兩個代表性標的，最新收盤、當天漲跌與近三個月走勢。
 * 用 ETF 代表大盤（0050 ≈ 台股大盤、SPY ≈ S&P 500、QQQ ≈ 那斯達克 100），資料來源跟個股一樣，不用另外的 API。
 */
const MARKET_TILES = [
  { stockId: '0050', name: '台灣 50', note: '台股大盤' },
  { stockId: '2330', name: '台積電', note: '權值股' },
  { stockId: 'SPY', name: 'S&P 500', note: 'SPY' },
  { stockId: 'QQQ', name: '那斯達克 100', note: 'QQQ' },
]

function MarketOverview() {
  return (
    <section className="market-overview" aria-label="市場概況">
      {MARKET_TILES.map((tile) => (
        <MarketTile key={tile.stockId} tile={tile} />
      ))}
    </section>
  )
}

function MarketTile({ tile }) {
  const [state, setState] = useState({ status: 'loading', points: [] })

  useEffect(() => {
    let ignore = false
    const today = toIsoDate(new Date())
    getPrices(tile.stockId, minusMonths(today, 3), today)
      .then((prices) => {
        if (!ignore) setState({ status: 'done', points: prices.map((p) => ({ date: p.date, close: Number(p.close) })) })
      })
      .catch((error) => {
        // 市場概況只是輔助資訊，一格抓不到不影響其他格
        console.warn(`[首頁] 市場概況 ${tile.stockId} 載入失敗：${error.message}`)
        if (!ignore) setState({ status: 'error', points: [] })
      })
    return () => {
      ignore = true
    }
  }, [tile.stockId])

  const { points } = state
  const last = points.at(-1)?.close
  const previous = points.at(-2)?.close
  const changePercent = previous ? ((last - previous) / previous) * 100 : null
  const trend = trendClass(changePercent)
  const color = trend === 'up' ? CHART_COLORS.up : trend === 'down' ? CHART_COLORS.down : CHART_COLORS.flat
  const gradientId = `market-${tile.stockId}`

  return (
    <Link to={`/stocks/${tile.stockId}`} className={`market-tile ${trend}`} aria-busy={state.status === 'loading'}>
      <div className="market-tile-head">
        <span className="market-tile-name">{tile.name}</span>
        <span className="muted">{tile.note}</span>
      </div>
      {state.status === 'error' && <div className="market-tile-empty muted">暫時無法顯示</div>}
      {state.status !== 'error' && (
        <>
          <div className="market-tile-price">
            <span className="market-tile-close">{last == null ? '—' : currencyPrefix(tile.stockId) + formatPrice(last)}</span>
            <span className={`market-tile-change ${trend}`}>
              {changePercent == null ? '' : `${formatSigned(changePercent)}%`}
            </span>
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
        </>
      )}
    </Link>
  )
}

export default MarketOverview
