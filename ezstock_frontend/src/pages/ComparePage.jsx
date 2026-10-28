import { useState } from 'react'
import { compareStocks } from '../api/backtestApi'
import { SERIES_COLORS } from '../components/chartConfig'
import CompareChart from '../components/CompareChart'
import CompareTable from '../components/CompareTable'
import StockPicker from '../components/StockPicker'
import { formatMoney, minusMonths, toIsoDate } from '../utils/format'

const MAX_STOCKS = 5

const DEFAULT_STOCKS = [
  { stockId: '2330', name: '台積電' },
  { stockId: '0050', name: '元大台灣50' },
  { stockId: '00878', name: '國泰永續高股息' },
]

const CAPITAL_PRESETS = [100000, 500000, 1000000]

const FEE_DISCOUNTS = [
  { value: '1', label: '不打折' },
  { value: '0.6', label: '6 折' },
  { value: '0.28', label: '2.8 折' },
]

/** 標的比較：同一筆錢、同一天買進不同標的，一直抱到今天，誰比較好？ */
function ComparePage() {
  const today = toIsoDate(new Date())
  const [selected, setSelected] = useState(DEFAULT_STOCKS)
  const [startDate, setStartDate] = useState(minusMonths(today, 36))
  const [capital, setCapital] = useState('500000')
  const [reinvest, setReinvest] = useState(true)
  const [includeCosts, setIncludeCosts] = useState(true)
  const [feeDiscount, setFeeDiscount] = useState('0.6')
  const [state, setState] = useState({ status: 'idle', response: null, error: null, colors: {} })

  const canSubmit = selected.length > 0 && startDate && Number(capital) >= 1000 && state.status !== 'loading'

  async function handleSubmit(event) {
    event.preventDefault()
    if (!canSubmit) return
    // 顏色在送出的這一刻依照選擇順序決定，之後改選項也不會讓已經畫好的圖換色
    const colors = Object.fromEntries(selected.map((s, i) => [s.stockId, SERIES_COLORS[i]]))
    const request = {
      stockIds: selected.map((s) => s.stockId),
      startDate,
      initialCapital: Number(capital),
      reinvestDividends: reinvest,
      includeCosts,
      feeDiscount: includeCosts ? Number(feeDiscount) : null,
    }
    console.info('[標的比較] 送出', request)
    setState((current) => ({ ...current, status: 'loading', error: null }))
    try {
      const response = await compareStocks(request)
      const best = [...response.results].sort((a, b) => b.finalValue - a.finalValue)[0]
      console.info(`[標的比較] 完成：${response.results.length} 檔，期末資產最高的是 ${best?.stockId} ${best?.name}`)
      setState({ status: 'done', response, error: null, colors })
    } catch (error) {
      setState((current) => ({ ...current, status: 'error', error }))
    }
  }

  const { response } = state
  const warnings = response?.results.flatMap((r) => r.warnings) ?? []

  return (
    <>
      <section className="card">
        <h2 className="section-title">標的比較</h2>
        <p className="muted compare-intro">
          如果當時把同一筆錢分別買進這幾檔，一直抱到今天，現在各會變成多少？
        </p>

        <form onSubmit={handleSubmit} className="compare-form">
          <div className="form-field">
            <span className="form-label">比較標的（最多 {MAX_STOCKS} 檔）</span>
            <StockPicker selected={selected} onChange={setSelected} max={MAX_STOCKS} />
          </div>

          <div className="form-row">
            <label className="form-field">
              <span className="form-label">起始日</span>
              <input
                type="date"
                className="form-input"
                value={startDate}
                max={today}
                min={minusMonths(today, 120)}
                onChange={(event) => setStartDate(event.target.value)}
                required
              />
            </label>

            <label className="form-field">
              <span className="form-label">每檔投入金額（元）</span>
              <input
                type="number"
                className="form-input"
                value={capital}
                min={1000}
                max={100000000}
                step={1000}
                onChange={(event) => setCapital(event.target.value)}
                required
              />
              <span className="form-presets">
                {CAPITAL_PRESETS.map((amount) => (
                  <button key={amount} type="button" className="chip" onClick={() => setCapital(String(amount))}>
                    {formatMoney(amount / 10000)} 萬
                  </button>
                ))}
              </span>
            </label>
          </div>

          <div className="form-row">
            <label className="form-check">
              <input type="checkbox" checked={reinvest} onChange={(event) => setReinvest(event.target.checked)} />
              股利再投入
            </label>
            <label className="form-check">
              <input
                type="checkbox"
                checked={includeCosts}
                onChange={(event) => setIncludeCosts(event.target.checked)}
              />
              計算手續費
            </label>
            {includeCosts && (
              <label className="form-check">
                手續費折扣
                <select
                  className="form-input form-select"
                  value={feeDiscount}
                  onChange={(event) => setFeeDiscount(event.target.value)}
                >
                  {FEE_DISCOUNTS.map((d) => (
                    <option key={d.value} value={d.value}>
                      {d.label}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>

          <button type="submit" className="primary-button" disabled={!canSubmit}>
            {state.status === 'loading' ? '計算中…（沒查過的股票要先抓資料）' : '開始比較'}
          </button>
        </form>
      </section>

      {state.status === 'error' && (
        <section className="card compare-result">
          <p className="error">{state.error.message}</p>
          {state.error.traceId && <p className="muted">錯誤代碼：{state.error.traceId}</p>}
        </section>
      )}

      {response && (
        <section className={`card compare-result ${state.status === 'loading' ? 'is-stale' : ''}`}>
          <h2 className="section-title">
            {response.startDate} ～ {response.endDate}，每檔投入 {formatMoney(response.initialCapital)} 元
          </h2>

          {warnings.length > 0 && (
            <div className="notice" role="alert">
              <strong>請注意：</strong>
              <ul>
                {warnings.map((warning) => (
                  <li key={warning}>{warning}</li>
                ))}
              </ul>
            </div>
          )}

          <CompareChart results={response.results} colors={state.colors} initialCapital={Number(response.initialCapital)} />
          <CompareTable results={response.results} colors={state.colors} />

          <details className="assumptions">
            <summary>回測假設與計算方式</summary>
            <ul>
              {response.assumptions.map((text) => (
                <li key={text}>{text}</li>
              ))}
            </ul>
            <p className="muted">過去的績效不代表未來的報酬，回測結果僅供參考。</p>
          </details>
        </section>
      )}
    </>
  )
}

export default ComparePage
