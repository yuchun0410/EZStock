import { useState } from 'react'
import { Link } from 'react-router'
import { formatMoney, formatPrice, formatSigned, formatSignedMoney, trendClass } from '../utils/format'
import StockPicker from './StockPicker'

/**
 * 持股表格：每檔的成本、現價、現值、損益、報酬率，可以新增、修改、刪除。
 * 同一檔再新增一次就是修改（後端用 PUT，同一檔只會有一筆）。
 */
function HoldingTable({ holdings, busy, onSave, onDelete }) {
  const [stocks, setStocks] = useState([])
  const [draft, setDraft] = useState({ shares: '', averageCost: '' })

  function edit(holding) {
    setStocks([{ stockId: holding.stockId, name: holding.name }])
    setDraft({ shares: String(holding.shares), averageCost: String(holding.averageCost) })
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (stocks.length !== 1) return
    const ok = await onSave(stocks[0], { shares: Number(draft.shares), averageCost: Number(draft.averageCost) })
    if (ok) {
      setStocks([])
      setDraft({ shares: '', averageCost: '' })
    }
  }

  const editingExisting = stocks.length === 1 && holdings.some((h) => h.stockId === stocks[0].stockId)

  return (
    <section className="asset-section">
      <h3 className="section-title">股票持股</h3>

      {holdings.length === 0 ? (
        <p className="muted">還沒有持股。</p>
      ) : (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th>股票</th>
                <th className="num">股數</th>
                <th className="num">平均成本</th>
                <th className="num">現價</th>
                <th className="num">現值</th>
                <th className="num">損益</th>
                <th className="num">報酬率</th>
                <th aria-label="操作" />
              </tr>
            </thead>
            <tbody>
              {holdings.map((h) => (
                <tr key={h.stockId}>
                  <td>
                    <Link to={`/stocks/${h.stockId}`}>
                      {h.stockId} {h.name}
                    </Link>
                  </td>
                  <td className="num">{formatMoney(h.shares)}</td>
                  <td className="num">{formatPrice(h.averageCost)}</td>
                  <td className="num">
                    {h.price == null ? '—' : formatPrice(h.price)}
                    {h.priceDate && <div className="muted asset-price-date">{h.priceDate}</div>}
                  </td>
                  <td className="num">{h.marketValue == null ? '—' : formatMoney(h.marketValue)}</td>
                  <td className={`num ${trendClass(h.unrealizedPnl)}`}>
                    {h.unrealizedPnl == null ? '—' : formatSignedMoney(h.unrealizedPnl)}
                  </td>
                  <td className={`num ${trendClass(h.returnPercent)}`}>
                    {h.returnPercent == null ? '—' : `${formatSigned(h.returnPercent)}%`}
                  </td>
                  <td className="asset-actions">
                    <button type="button" className="link-button" onClick={() => edit(h)}>
                      修改
                    </button>
                    <button
                      type="button"
                      className="icon-button"
                      onClick={() => onDelete(h)}
                      disabled={busy}
                      aria-label={`刪除持股 ${h.stockId} ${h.name}`}
                    >
                      ✕
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <form className="holding-form" onSubmit={handleSubmit}>
        <div className="form-field holding-stock">
          <span className="form-label">{editingExisting ? '修改持股' : '新增持股'}</span>
          <StockPicker selected={stocks} onChange={setStocks} max={1} />
        </div>
        <div className="form-row">
          <label className="form-field">
            <span className="form-label">股數</span>
            <input
              type="number"
              className="form-input holding-input"
              value={draft.shares}
              onChange={(event) => setDraft({ ...draft, shares: event.target.value })}
              min={1}
              step={1}
              placeholder="例如 1000"
              required
            />
            <span className="muted form-hint">1 張 = 1000 股</span>
          </label>
          <label className="form-field">
            <span className="form-label">每股平均成本</span>
            <input
              type="number"
              className="form-input holding-input"
              value={draft.averageCost}
              onChange={(event) => setDraft({ ...draft, averageCost: event.target.value })}
              min={0.0001}
              step="0.0001"
              placeholder="例如 950.5"
              required
            />
            <span className="muted form-hint">建議含手續費</span>
          </label>
          <button type="submit" className="primary-button" disabled={busy || stocks.length !== 1}>
            {editingExisting ? '儲存修改' : '新增持股'}
          </button>
        </div>
      </form>
    </section>
  )
}

export default HoldingTable
