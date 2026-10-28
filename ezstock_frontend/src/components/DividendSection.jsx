import { useEffect, useState } from 'react'
import { getDividends } from '../api/stockApi'
import { formatPrice, minusMonths } from '../utils/format'

// 表格預設只顯示最近幾筆，季配息的 ETF 一年就有 4 筆
const COLLAPSED_ROWS = 8

/** 個股頁的股利區塊：近一年股利與殖利率 + 歷年除息紀錄 */
function DividendSection({ stockId, latestDate, latestClose }) {
  const [result, setResult] = useState({ stockId: null, dividends: [], error: null })
  const [expanded, setExpanded] = useState(false)

  useEffect(() => {
    let ignore = false
    getDividends(stockId)
      .then((dividends) => {
        if (!ignore) setResult({ stockId, dividends, error: null })
      })
      .catch((error) => {
        if (!ignore) setResult({ stockId, dividends: [], error })
      })
    return () => {
      ignore = true
    }
  }, [stockId])

  if (result.stockId !== stockId) {
    return <section className="card dividend-section muted">載入股利紀錄中…</section>
  }

  const { dividends, error } = result
  const trailing = trailingYear(dividends, latestDate, latestClose)
  const rows = expanded ? dividends : dividends.slice(0, COLLAPSED_ROWS)

  return (
    <section className="card dividend-section">
      <h2 className="section-title">股利紀錄</h2>

      {error && <p className="error">{error.message}</p>}
      {!error && dividends.length === 0 && <p className="muted">最近 10 年沒有現金股利紀錄</p>}

      {dividends.length > 0 && (
        <>
          <div className="stat-row">
            <Stat label="近一年現金股利" value={`${formatPrice(trailing.total)} 元`} />
            <Stat label="近一年殖利率" value={trailing.yieldPercent == null ? '-' : `${formatPrice(trailing.yieldPercent)}%`} />
            <Stat label="近一年配息次數" value={`${trailing.count} 次`} />
          </div>

          <div className="table-wrap">
            <table className="table">
              <thead>
                <tr>
                  <th>除息日</th>
                  <th className="num">現金股利</th>
                  <th className="num">除息前收盤</th>
                  <th className="num">殖利率</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((d) => (
                  <tr key={d.exDate}>
                    <td>{d.exDate}</td>
                    <td className="num">{formatDividend(d.cashDividend)}</td>
                    <td className="num">{d.closeBeforeExDate == null ? '-' : formatPrice(d.closeBeforeExDate)}</td>
                    <td className="num">{d.yieldPercent == null ? '-' : `${formatPrice(d.yieldPercent)}%`}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {dividends.length > COLLAPSED_ROWS && (
            <button type="button" className="link-button" onClick={() => setExpanded((v) => !v)}>
              {expanded ? '收合' : `顯示全部 ${dividends.length} 筆`}
            </button>
          )}
          <p className="chart-caption muted">殖利率 = 現金股利 ÷ 除息前一個交易日收盤價；近一年殖利率以最新收盤價計算</p>
        </>
      )}
    </section>
  )
}

function Stat({ label, value }) {
  return (
    <div className="stat">
      <div className="stat-label muted">{label}</div>
      <div className="stat-value">{value}</div>
    </div>
  )
}

/** 最近 12 個月（以最新交易日往回推）的股利合計，殖利率用最新收盤價計算 */
function trailingYear(dividends, latestDate, latestClose) {
  const since = minusMonths(latestDate, 12)
  const recent = dividends.filter((d) => d.exDate > since && d.exDate <= latestDate)
  const total = recent.reduce((sum, d) => sum + Number(d.cashDividend), 0)
  const yieldPercent = latestClose ? (total / Number(latestClose)) * 100 : null
  return { total, yieldPercent, count: recent.length }
}

/** 股利常有 3、4 位小數（例如 0.4500），去掉多餘的 0，至少留 2 位 */
function formatDividend(value) {
  return Number(value).toLocaleString('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 4 })
}

export default DividendSection
