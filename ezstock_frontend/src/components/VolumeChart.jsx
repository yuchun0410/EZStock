import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { CHART_COLORS, CHART_SYNC_ID, Y_AXIS_WIDTH, candleColor } from './chartConfig'

const compact = new Intl.NumberFormat('zh-TW', { notation: 'compact', maximumFractionDigits: 1 })

/**
 * 成交量柱狀圖，放在價格圖正下方。
 * 價格和成交量單位差太多，不放在同一張圖用兩個 Y 軸，而是分成上下兩張，
 * 用同一個 syncId 讓滑鼠游標和提示框同步。
 */
function VolumeChart({ points }) {
  return (
    <div className="chart chart-volume" role="img" aria-label="成交量（張）">
      <ResponsiveContainer width="100%" height={96}>
        <BarChart data={points} syncId={CHART_SYNC_ID} barCategoryGap="20%" margin={{ top: 4, right: 8, bottom: 0, left: 0 }}>
          <XAxis dataKey="date" hide />
          <YAxis
            width={Y_AXIS_WIDTH}
            tickFormatter={(value) => compact.format(value)}
            tick={{ fill: CHART_COLORS.axis, fontSize: 11 }}
            tickLine={false}
            axisLine={false}
            tickCount={3}
          />
          {/* 只要同步的游標，資訊框已經在價格圖上顯示了 */}
          <Tooltip content={() => null} cursor={{ fill: 'rgba(0, 0, 0, 0.06)' }} isAnimationActive={false} />
          <Bar dataKey="lots" isAnimationActive={false}>
            {points.map((p) => (
              <Cell key={p.date} fill={candleColor(p)} fillOpacity={0.6} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <div className="chart-caption muted">成交量（張）</div>
    </div>
  )
}

export default VolumeChart
