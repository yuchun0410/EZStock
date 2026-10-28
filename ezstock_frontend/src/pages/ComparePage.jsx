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

// 手續費折扣用「折」輸入（6 = 6 折），跟券商的說法一樣；送給後端時再除以 10 變成 0.6
const MIN_DISCOUNT = 1
const MAX_DISCOUNT = 10
const DISCOUNT_PRESETS = [
  { value: '10', label: '不打折' },
  { value: '6', label: '6 折' },
  { value: '2.8', label: '2.8 折' },
]

/** 標的比較：同一筆錢、同一天買進不同標的，一直抱到今天，誰比較好？ */
function ComparePage() {
  const today = toIsoDate(new Date())
  const [selected, setSelected] = useState(DEFAULT_STOCKS)
  const [startDate, setStartDate] = useState(minusMonths(today, 36))
  const [capital, setCapital] = useState('500000')
  const [reinvest, setReinvest] = useState(true)
  const [includeCosts, setIncludeCosts] = useState(true)
  const [feeDiscount, setFeeDiscount] = useState('6')
  const [state, setState] = useState({ status: 'idle', response: null, error: null, colors: {} })

  const discountValue = Number(feeDiscount)
  const discountValid =
    !includeCosts || (feeDiscount !== '' && discountValue >= MIN_DISCOUNT && discountValue <= MAX_DISCOUNT)
  const canSubmit =
    selected.length > 0 && startDate && Number(capital) >= 1000 && discountValid && state.status !== 'loading'

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
      // 6 折 → 0.6；四捨五入到小數 3 位，避免 2.8 / 10 變成 0.27999999999999997
      feeDiscount: includeCosts ? Math.round((discountValue / 10) * 1000) / 1000 : null,
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
              <div className="form-field">
                <label className="form-check">
                  手續費
                  <input
                    type="number"
                    className="form-input form-input-small"
                    value={feeDiscount}
                    min={MIN_DISCOUNT}
                    max={MAX_DISCOUNT}
                    step={0.1}
                    inputMode="decimal"
                    aria-invalid={!discountValid}
                    onChange={(event) => setFeeDiscount(event.target.value)}
                  />
                  折
                </label>
                <span className="form-presets">
                  {DISCOUNT_PRESETS.map((d) => (
                    <button key={d.value} type="button" className="chip" onClick={() => setFeeDiscount(d.value)}>
                      {d.label}
                    </button>
                  ))}
                </span>
                {!discountValid && (
                  <span className="error form-hint">
                    請輸入 {MIN_DISCOUNT}～{MAX_DISCOUNT} 之間的數字（10 代表不打折）
                  </span>
                )}
              </div>
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
