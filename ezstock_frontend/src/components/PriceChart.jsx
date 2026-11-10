import {
  Area,
  Bar,
  CartesianGrid,
  ComposedChart,
  Line,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import { formatPrice } from '../utils/format'
import { CHART_COLORS, CHART_SYNC_ID, MOVING_AVERAGES, Y_AXIS_WIDTH, candleColor } from './chartConfig'
import PriceTooltip from './PriceTooltip'

/**
 * 價格走勢圖：mode = 'line' 畫收盤價折線，mode = 'candle' 畫 K 線。
 * 折線的顏色跟著「這段期間」的漲跌：期間上漲紅色、下跌綠色。
 */
function PriceChart({ points, mode, trend, longRange, weekly, maPeriods, stockId }) {
  const lineColor = CHART_COLORS[trend] ?? CHART_COLORS.neutral
  // 每種顏色一個漸層 id；同一頁只有一張價格圖，不會撞名
  const gradientId = `price-fill-${trend || 'flat'}`

  return (
    <div className="chart" role="img" aria-label={mode === 'candle' ? 'K 線圖' : '收盤價走勢圖'}>
      <ResponsiveContainer width="100%" height={320}>
        <ComposedChart
          data={points}
          syncId={CHART_SYNC_ID}
          barCategoryGap="20%"
          margin={{ top: 8, right: 8, bottom: 0, left: 0 }}
        >
          {/* 折線下方的漸層：上面帶一點漲跌的顏色，往下淡到透明 */}
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor={lineColor} stopOpacity={0.22} />
              <stop offset="100%" stopColor={lineColor} stopOpacity={0} />
            </linearGradient>
          </defs>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis
            dataKey="date"
            tickFormatter={(date) => (longRange ? date.slice(0, 7).replace('-', '/') : date.slice(5).replace('-', '/'))}
            minTickGap={40}
            tick={{ fill: CHART_COLORS.axis, fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: CHART_COLORS.grid }}
          />
          <YAxis
            domain={['auto', 'auto']}
            tickFormatter={(value) => formatPrice(value).replace(/\.00$/, '')}
            width={Y_AXIS_WIDTH}
            tick={{ fill: CHART_COLORS.axis, fontSize: 12 }}
            tickLine={false}
            axisLine={false}
          />
          <Tooltip
            content={<PriceTooltip weekly={weekly} maPeriods={maPeriods} stockId={stockId} />}
            cursor={{ stroke: CHART_COLORS.axis, strokeDasharray: '4 4' }}
            isAnimationActive={false}
          />
          {mode === 'candle' ? (
            <Bar dataKey="range" shape={<Candle />} isAnimationActive={false} />
          ) : (
            <Area
              type="monotone"
              dataKey="close"
              stroke={lineColor}
              strokeWidth={2}
              fill={`url(#${gradientId})`}
              dot={false}
              activeDot={{ r: 5, stroke: CHART_COLORS.surface, strokeWidth: 2 }}
              isAnimationActive={false}
            />
          )}
          {MOVING_AVERAGES.filter((ma) => maPeriods.includes(ma.period)).map((ma) => (
            <Line
              key={ma.period}
              type="monotone"
              dataKey={`ma${ma.period}`}
              stroke={ma.color}
              strokeWidth={1.5}
              dot={false}
              activeDot={false}
              connectNulls={false}
              isAnimationActive={false}
            />
          ))}
        </ComposedChart>
      </ResponsiveContainer>
    </div>
  )
}

/**
 * 一根 K 棒。Recharts 把 [最低, 最高] 換算成畫面上的 y（最高點）和 height（最高到最低的高度），
 * 我們用同樣的比例算出開盤、收盤的位置：細線是最高到最低（影線），粗的矩形是開盤到收盤（實體）。
 */
function Candle({ x, y, width, height, payload }) {
  const { open, close, high, low } = payload
  const color = candleColor(payload)
  const pixelsPerPoint = high === low ? 0 : height / (high - low)
  const bodyTop = y + (high - Math.max(open, close)) * pixelsPerPoint
  const bodyHeight = Math.max(1, Math.abs(close - open) * pixelsPerPoint)
  const centerX = x + width / 2
  const bodyWidth = Math.max(1, width)

  return (
    <g>
      <line x1={centerX} x2={centerX} y1={y} y2={y + height} stroke={color} strokeWidth={1} />
      <rect x={centerX - bodyWidth / 2} y={bodyTop} width={bodyWidth} height={bodyHeight} fill={color} />
    </g>
  )
}

export default PriceChart
