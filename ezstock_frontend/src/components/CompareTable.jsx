import { formatMoney, formatPrice, formatSigned, trendClass } from '../utils/format'

/** 比較結果表：依期末資產由高到低排名 */
function CompareTable({ results, colors }) {
  const ranked = [...results].sort((a, b) => Number(b.finalValue) - Number(a.finalValue))

  return (
    <div className="table-wrap">
      <table className="table compare-table">
        <thead>
          <tr>
            <th>排名</th>
            <th>標的</th>
            <th className="num">期末資產</th>
            <th className="num">總報酬</th>
            <th className="num">年化報酬</th>
            <th className="num">最大回落</th>
            <th className="num">領到股利</th>
            <th className="num">交易成本</th>
            <th>買進</th>
          </tr>
        </thead>
        <tbody>
          {ranked.map((r, index) => (
            <tr key={r.stockId}>
              <td>{index + 1}</td>
              <td>
                <span className="legend-dot" style={{ background: colors[r.stockId] }} />
                {r.stockId} {r.name}
              </td>
              <td className="num strong">{formatMoney(r.finalValue)}</td>
              <td className={`num ${trendClass(r.metrics.totalReturnPercent)}`}>
                {formatSigned(r.metrics.totalReturnPercent)}%
              </td>
              <td className={`num ${trendClass(r.metrics.annualizedReturnPercent)}`}>
                {r.metrics.annualizedReturnPercent == null
                  ? '未滿一年'
                  : `${formatSigned(r.metrics.annualizedReturnPercent)}%`}
              </td>
              <td className="num">{formatPrice(r.metrics.maxDrawdownPercent)}%</td>
              <td className="num">{formatMoney(r.totalDividends)}</td>
              <td className="num">{formatMoney(r.totalCosts)}</td>
              <td className="muted">
                {r.buyDate} 以 {formatPrice(r.buyPrice)} 買 {formatMoney(r.initialShares)} 股
                {r.finalShares !== r.initialShares && `，期末 ${formatMoney(r.finalShares)} 股`}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export default CompareTable
