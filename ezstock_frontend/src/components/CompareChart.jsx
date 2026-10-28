import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'
import { formatMoney, formatTenThousands } from '../utils/format'
import { CHART_COLORS } from './chartConfig'

/**
 * 資產曲線比較：每一檔一條線，同一個 Y 軸（都是「元」）。
 * 每一檔的交易日可能不一樣（例如較晚上市），先依日期合併成一張表，缺的那天不畫。
 */
function CompareChart({ results, colors, initialCapital }) {
  const data = mergeCurves(results)

  return (
    <div className="chart" role="img" aria-label="各標的資產曲線比較">
      <ResponsiveContainer width="100%" height={360}>
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
            content={<CompareTooltip results={results} colors={colors} initialCapital={initialCapital} />}
            cursor={{ stroke: CHART_COLORS.axis, strokeDasharray: '4 4' }}
            isAnimationActive={false}
          />
          <Legend
            formatter={(value) => {
              const result = results.find((r) => r.stockId === value)
              return <span className="legend-text">{result ? `${result.stockId} ${result.name}` : value}</span>
            }}
          />
          {results.map((result) => (
            <Line
              key={result.stockId}
              type="monotone"
              dataKey={result.stockId}
              stroke={colors[result.stockId]}
              strokeWidth={2}
              dot={false}
              activeDot={{ r: 5, stroke: CHART_COLORS.surface, strokeWidth: 2 }}
              connectNulls={false}
              isAnimationActive={false}
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
    </div>
  )
}

/** 滑鼠移到某一天：列出每一檔當天的資產，由高到低，並顯示相對本金的報酬 */
function CompareTooltip({ active, payload, label, results, colors, initialCapital }) {
  if (!active || !payload?.length) return null
  const rows = payload
    .filter((p) => p.value != null)
    .sort((a, b) => b.value - a.value)
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-date">{label}</div>
      {rows.map((p) => {
        const result = results.find((r) => r.stockId === p.dataKey)
        const percent = ((p.value - initialCapital) / initialCapital) * 100
        return (
          <div key={p.dataKey} className="chart-tooltip-ma">
            <span className="legend-dot" style={{ background: colors[p.dataKey] }} />
            {p.dataKey} {result?.name}：{formatMoney(p.value)} 元（{percent >= 0 ? '+' : ''}
            {percent.toFixed(1)}%）
          </div>
        )
      })}
    </div>
  )
}

function mergeCurves(results) {
  const byDate = new Map()
  for (const result of results) {
    for (const point of result.equityCurve) {
      const row = byDate.get(point.date) ?? { date: point.date }
      row[result.stockId] = Number(point.value)
      byDate.set(point.date, row)
    }
  }
  return [...byDate.values()].sort((a, b) => a.date.localeCompare(b.date))
}

export default CompareChart
