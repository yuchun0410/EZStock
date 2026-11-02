import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { Line, LineChart, ResponsiveContainer, Tooltip, YAxis } from 'recharts'
import { getPrices } from '../api/stockApi'
import { formatPrice, formatSigned, minusMonths, toIsoDate, trendClass } from '../utils/format'
import { CHART_COLORS } from './chartConfig'

/**
 * 文章內頁的迷你走勢圖：最近一個月的收盤價。
 * 線的顏色依「這一個月」是漲是跌（漲紅、跌綠），旁邊一定寫出漲跌幅，不只靠顏色。
 * 沒有座標軸，只看形狀；滑鼠移上去看當天的日期與收盤價。
 */
function StockSparkline({ stock }) {
  const [state, setState] = useState({ status: 'loading', points: [] })

  useEffect(() => {
    let ignore = false
    const today = toIsoDate(new Date())
    getPrices(stock.stockId, minusMonths(today, 1), today)
      .then((prices) => {
        if (!ignore) setState({ status: 'done', points: prices.map((p) => ({ date: p.date, close: Number(p.close) })) })
      })
      .catch((error) => {
        console.warn(`[論壇] ${stock.stockId} 走勢圖載入失敗`, error)
        if (!ignore) setState({ status: 'error', points: [] })
      })
    return () => {
      ignore = true
    }
  }, [stock.stockId])

  const { points } = state
  const first = points[0]?.close
  const last = points[points.length - 1]?.close
  const monthChange = first ? ((last - first) / first) * 100 : null
  const color = monthChange > 0 ? CHART_COLORS.up : monthChange < 0 ? CHART_COLORS.down : CHART_COLORS.flat

  return (
    <div className="sparkline-card">
      <div className="sparkline-header">
        <Link to={`/stocks/${stock.stockId}`}>
          {stock.stockId} {stock.name}
        </Link>
        {last != null && <strong>{formatPrice(last)}</strong>}
      </div>
      {state.status === 'loading' && <div className="sparkline-placeholder muted">載入中…</div>}
      {state.status === 'error' && <div className="sparkline-placeholder muted">走勢圖暫時無法顯示</div>}
      {state.status === 'done' && points.length < 2 && <div className="sparkline-placeholder muted">資料不足</div>}
      {points.length >= 2 && (
        <>
          <div className="sparkline-chart" role="img" aria-label={`${stock.name}近一個月走勢，${formatSigned(monthChange)}%`}>
            <ResponsiveContainer width="100%" height={56}>
              <LineChart data={points} margin={{ top: 4, right: 2, bottom: 4, left: 2 }}>
                <YAxis hide domain={['dataMin', 'dataMax']} />
                <Tooltip
                  cursor={{ stroke: CHART_COLORS.grid }}
                  formatter={(value) => [formatPrice(value), '收盤']}
                  labelFormatter={(_, payload) => payload?.[0]?.payload.date ?? ''}
                />
                <Line type="monotone" dataKey="close" stroke={color} strokeWidth={2} dot={false} isAnimationActive={false} />
              </LineChart>
            </ResponsiveContainer>
          </div>
          <div className="sparkline-footer muted">
            近一個月 <span className={trendClass(monthChange)}>{formatSigned(monthChange)}%</span>
          </div>
        </>
      )}
    </div>
  )
}

export default StockSparkline
