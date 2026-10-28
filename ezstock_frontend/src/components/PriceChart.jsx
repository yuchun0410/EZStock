import {
  CartesianGrid,
  Line,
  LineChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatLots, formatPrice } from '../utils/format'

// SVG 的屬性不一定吃 CSS 變數，這裡直接寫色碼（跟 index.css 的變數同色）
const COLORS = {
  up: '#d63a3a',
  down: '#1f9d55',
  neutral: '#1f3a5f',
  grid: '#e1e4ea',
  axis: '#6b7385',
  surface: '#ffffff',
}

/**
 * 收盤價走勢圖（單一折線）。
 * 線的顏色跟著「這段期間」的漲跌：期間上漲紅色、下跌綠色。
 */
function PriceChart({ prices, trend, longRange }) {
  const data = prices.map((p) => ({ ...p, close: Number(p.close) }))
  const lineColor = COLORS[trend] ?? COLORS.neutral

  return (
    <div className="chart" role="img" aria-label="收盤價走勢圖">
      <ResponsiveContainer width="100%" height={320}>
        <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke={COLORS.grid} />
          <XAxis
            dataKey="date"
            tickFormatter={(date) => (longRange ? date.slice(0, 7).replace('-', '/') : date.slice(5).replace('-', '/'))}
            minTickGap={40}
            tick={{ fill: COLORS.axis, fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: COLORS.grid }}
          />
          <YAxis
            domain={['auto', 'auto']}
            tickFormatter={(value) => formatPrice(value).replace(/\.00$/, '')}
            width={64}
            tick={{ fill: COLORS.axis, fontSize: 12 }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            content={<PriceTooltip />}
            cursor={{ stroke: COLORS.axis, strokeDasharray: '4 4' }}
            isAnimationActive={false}
          />
          <Line
            type="monotone"
            dataKey="close"
            stroke={lineColor}
            strokeWidth={2}
            dot={false}
            activeDot={{ r: 5, stroke: COLORS.surface, strokeWidth: 2 }}
            isAnimationActive={false}
          />
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

/** 滑鼠移到某一天時顯示的資訊框 */
function PriceTooltip({ active, payload }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-date">{p.date}</div>
      <div>
        收盤 <strong>{formatPrice(p.close)}</strong>
      </div>
      <div className="muted">
        開 {formatPrice(p.open)}　高 {formatPrice(p.high)}　低 {formatPrice(p.low)}
      </div>
      <div className="muted">成交量 {formatLots(p.volume)} 張</div>
    </div>
  )
}

export default PriceChart
