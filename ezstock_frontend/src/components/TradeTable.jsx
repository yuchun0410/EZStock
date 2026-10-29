import { useState } from 'react'
import { formatMoney, formatPrice, formatSigned, trendClass } from '../utils/format'

const COLLAPSED_ROWS = 10

/** 每一筆交易的明細，最新的在上面 */
function TradeTable({ trades }) {
  const [expanded, setExpanded] = useState(false)
  const newestFirst = [...trades].reverse()
  const rows = expanded ? newestFirst : newestFirst.slice(0, COLLAPSED_ROWS)

  if (trades.length === 0) {
    return <p className="muted">這段期間沒有符合買進條件的日子，所以沒有交易。</p>
  }

  return (
    <>
      <div className="table-wrap">
        <table className="table">
          <thead>
            <tr>
              <th>買進</th>
              <th>賣出</th>
              <th className="num">股數</th>
              <th className="num">損益</th>
              <th className="num">報酬</th>
              <th className="num">持有</th>
              <th>賣出原因</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((t) => (
              <tr key={t.entryDate} className={t.open ? 'is-open' : ''}>
                <td>
                  {t.entryDate}
                  <div className="muted">{formatPrice(t.entryPrice)}</div>
                </td>
                <td>
                  {t.open ? '持有中' : t.exitDate}
                  {!t.open && <div className="muted">{formatPrice(t.exitPrice)}</div>}
                </td>
                <td className="num">{formatMoney(t.shares)}</td>
                <td className={`num ${trendClass(t.profit)}`}>{formatSigned(t.profit).replace(/\.00$/, '')}</td>
                <td className={`num ${trendClass(t.returnPercent)}`}>{formatSigned(t.returnPercent)}%</td>
                <td className="num">{t.holdingDays} 天</td>
                <td>{t.exitReason}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {trades.length > COLLAPSED_ROWS && (
        <button type="button" className="link-button" onClick={() => setExpanded((v) => !v)}>
          {expanded ? '收合' : `顯示全部 ${trades.length} 筆`}
        </button>
      )}
    </>
  )
}

export default TradeTable
