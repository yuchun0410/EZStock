import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatMoney, formatTenThousands } from '../utils/format'
import { CHART_COLORS, SERIES_COLORS } from './chartConfig'

const SERIES = [
  { key: 'strategy', label: '條件策略', color: SERIES_COLORS[0] },
  { key: 'benchmark', label: '一直持有', color: SERIES_COLORS[1] },
]

/** 條件策略 vs 一直持有的資產曲線（兩條線，同一個 Y 軸） */
function StrategyChart({ strategyCurve, benchmarkCurve, initialCapital }) {
  const data = merge(strategyCurve, benchmarkCurve)

  return (
    <div className="chart" role="img" aria-label="條件策略與一直持有的資產曲線">
      <ResponsiveContainer width="100%" height={340}>
        <LineChart data={data} margin={{ top: 8, right: 8, bottom: 0, left: 0 }}>
          <CartesianGrid vertical={false} stroke={CHART_COLORS.grid} />
          <XAxis
            dataKey="date"
            tickFormatter={(date) => date.slice(0, 7).replace('-', '/')}
            minTickGap={48}
            tick={{ fill: CHART_COLORS.axis, fontSize: 12 }}
            tickLine={false}
            axisLine={{ stroke: CHART_COLORS.grid }}
          />
          <YAxis
            tickFormatter={formatTenThousands}
            width={72}
            tick={{ fill: CHART_COLORS.axis, fontSize: 12 }}
            tickLine={false}
            axisLine={false}
            domain={['auto', 'auto']}
          />
          <Tooltip
            content={<StrategyTooltip initialCapital={initialCapital} />}
            cursor={{ stroke: CHART_COLORS.axis, strokeDasharray: '4 4' }}
            isAnimationActive={false}
          />
          <Legend
            formatter={(value) => (
              <span className="legend-text">{SERIES.find((s) => s.key === value)?.label ?? value}</span>
            )}
          />
          {SERIES.map((s) => (
            <Line
              key={s.key}
              type="monotone"
              dataKey={s.key}
              stroke={s.color}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 5, stroke: CHART_COLORS.surface, strokeWidth: 2 }}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

function StrategyTooltip({ active, payload, label, initialCapital }) {
  if (!active || !payload?.length) return null
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-date">{label}</div>
      {payload.map((p) => {
        const series = SERIES.find((s) => s.key === p.dataKey)
        const percent = ((p.value - initialCapital) / initialCapital) * 100
        return (
          <div key={p.dataKey} className="chart-tooltip-ma">
            <span className="legend-dot" style={{ background: series?.color }} />
            {series?.label}：{formatMoney(p.value)} 元（{percent >= 0 ? '+' : ''}
            {percent.toFixed(1)}%）
          </div>
        )
      })}
    </div>
  )
}

function merge(strategyCurve, benchmarkCurve) {
  const benchmarkByDate = new Map(benchmarkCurve.map((p) => [p.date, Number(p.value)]))
  return strategyCurve.map((p) => ({
    date: p.date,
    strategy: Number(p.value),
    benchmark: benchmarkByDate.get(p.date),
  }))
}

export default StrategyChart
