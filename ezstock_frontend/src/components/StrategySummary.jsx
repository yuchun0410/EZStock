import { formatMoney, formatPrice, formatSigned, trendClass } from '../utils/format'

/** 條件策略 vs 一直持有：績效對照表 */
function StrategySummary({ strategy, benchmark }) {
  const rows = [
    {
      label: '期末資產',
      strategy: `${formatMoney(strategy.finalValue)} 元`,
      benchmark: `${formatMoney(benchmark.finalValue)} 元`,
      better: compare(strategy.finalValue, benchmark.finalValue),
    },
    percentRow('總報酬', strategy.metrics.totalReturnPercent, benchmark.metrics.totalReturnPercent),
    percentRow('年化報酬', strategy.metrics.annualizedReturnPercent, benchmark.metrics.annualizedReturnPercent),
    {
      label: '最大回落',
      strategy: `${formatPrice(strategy.metrics.maxDrawdownPercent)}%`,
      benchmark: `${formatPrice(benchmark.metrics.maxDrawdownPercent)}%`,
      // 回落是負數，越接近 0 越好
      better: compare(strategy.metrics.maxDrawdownPercent, benchmark.metrics.maxDrawdownPercent),
    },
    {
      label: '領到股利',
      strategy: `${formatMoney(strategy.totalDividends)} 元`,
      benchmark: `${formatMoney(benchmark.totalDividends)} 元`,
    },
    {
      label: '交易成本',
      strategy: `${formatMoney(strategy.totalCosts)} 元`,
      benchmark: `${formatMoney(benchmark.totalCosts)} 元`,
      better: compare(benchmark.totalCosts, strategy.totalCosts),
    },
    { label: '交易次數', strategy: `${strategy.tradeCount} 筆`, benchmark: '1 筆' },
    {
      label: '勝率',
      strategy: strategy.winRatePercent == null ? '-' : `${formatPrice(strategy.winRatePercent)}%`,
      benchmark: '-',
    },
    {
      label: '平均每筆報酬',
      strategy: strategy.averageReturnPercent == null ? '-' : `${formatSigned(strategy.averageReturnPercent)}%`,
      benchmark: '-',
    },
    { label: '持股時間', strategy: `${formatPrice(strategy.exposurePercent)}%`, benchmark: '100%' },
  ]

  return (
    <div className="table-wrap">
      <table className="table summary-table">
        <thead>
          <tr>
            <th />
            <th className="num">條件策略</th>
            <th className="num">一直持有</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.label}>
              <th scope="row">{row.label}</th>
              <td className={`num ${row.strategyClass ?? ''} ${row.better === 1 ? 'strong' : ''}`}>{row.strategy}</td>
              <td className={`num ${row.benchmarkClass ?? ''} ${row.better === -1 ? 'strong' : ''}`}>{row.benchmark}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function percentRow(label, strategyValue, benchmarkValue) {
  const text = (v) => (v == null ? '未滿一年' : `${formatSigned(v)}%`)
  return {
    label,
    strategy: text(strategyValue),
    benchmark: text(benchmarkValue),
    strategyClass: trendClass(strategyValue),
    benchmarkClass: trendClass(benchmarkValue),
    better: strategyValue == null || benchmarkValue == null ? 0 : compare(strategyValue, benchmarkValue),
  }
}

/** 1 = 策略比較好、-1 = 一直持有比較好、0 = 一樣（比較好的那格用粗體） */
function compare(a, b) {
  return Math.sign(Number(a) - Number(b))
}

export default StrategySummary
