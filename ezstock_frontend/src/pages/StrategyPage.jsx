import { useEffect, useState } from 'react'
import { getConditionTypes, runStrategy } from '../api/backtestApi'
import ConditionEditor from '../components/ConditionEditor'
import FeeSettings from '../components/FeeSettings'
import StockPicker from '../components/StockPicker'
import StrategyChart from '../components/StrategyChart'
import StrategySummary from '../components/StrategySummary'
import TradeTable from '../components/TradeTable'
import { isValidDiscount, toDiscountRatio } from '../utils/fee'
import { formatMoney, formatSigned, minusMonths, toIsoDate, trendClass } from '../utils/format'

const MAX_RULES = 3

// 常見的策略範本，點一下就把條件填好。第一個是進頁面時的預設值
const TEMPLATES = [
  {
    name: '月線趨勢',
    entry: [{ type: 'CLOSE_ABOVE_MOVING_AVERAGE', value: '20' }],
    exit: [
      { type: 'CLOSE_BELOW_MOVING_AVERAGE', value: '20' },
      { type: 'STOP_LOSS', value: '8' },
    ],
  },
  {
    name: '季線趨勢',
    entry: [{ type: 'CLOSE_ABOVE_MOVING_AVERAGE', value: '60' }],
    exit: [{ type: 'CLOSE_BELOW_MOVING_AVERAGE', value: '60' }],
  },
  {
    name: '破底買進',
    entry: [{ type: 'CLOSE_BELOW_LOWEST_LOW', value: '5' }],
    exit: [
      { type: 'TAKE_PROFIT', value: '10' },
      { type: 'STOP_LOSS', value: '5' },
      { type: 'HOLDING_DAYS', value: '20' },
    ],
  },
  {
    name: '突破追高',
    entry: [{ type: 'CLOSE_ABOVE_HIGHEST_HIGH', value: '20' }],
    exit: [
      { type: 'CLOSE_BELOW_MOVING_AVERAGE', value: '20' },
      { type: 'STOP_LOSS', value: '8' },
    ],
  },
]

/** 條件回測：符合條件才買、符合條件才賣，跟一直持有比起來如何？ */
function StrategyPage() {
  const today = toIsoDate(new Date())
  const [types, setTypes] = useState({ list: [], error: null })
  const [stocks, setStocks] = useState([{ stockId: '0050', name: '元大台灣50' }])
  const [startDate, setStartDate] = useState(minusMonths(today, 36))
  const [capital, setCapital] = useState('500000')
  const [entry, setEntry] = useState(TEMPLATES[0].entry)
  const [exit, setExit] = useState(TEMPLATES[0].exit)
  const [reinvest, setReinvest] = useState(true)
  const [includeCosts, setIncludeCosts] = useState(true)
  const [feeDiscount, setFeeDiscount] = useState('6')
  const [state, setState] = useState({ status: 'idle', response: null, error: null })

  // 條件種類由後端決定，進頁面時抓一次
  useEffect(() => {
    let ignore = false
    getConditionTypes()
      .then((list) => {
        if (!ignore) setTypes({ list, error: null })
      })
      .catch((error) => {
        if (!ignore) setTypes({ list: [], error })
      })
    return () => {
      ignore = true
    }
  }, [])

  const entryTypes = types.list.filter((t) => t.entryAllowed)
  const rulesFilled = (rules) => rules.length > 0 && rules.every((r) => r.value !== '')
  const canSubmit =
    stocks.length === 1 &&
    startDate &&
    Number(capital) >= 1000 &&
    rulesFilled(entry) &&
    rulesFilled(exit) &&
    isValidDiscount(includeCosts, feeDiscount) &&
    types.list.length > 0 &&
    state.status !== 'loading'

  function applyTemplate(template) {
    console.info(`[條件回測] 套用範本：${template.name}`)
    setEntry(template.entry)
    setExit(template.exit)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!canSubmit) return
    const toSpecs = (rules) => rules.map((r) => ({ type: r.type, value: Number(r.value) }))
    const request = {
      stockId: stocks[0].stockId,
      startDate,
      initialCapital: Number(capital),
      entryConditions: toSpecs(entry),
      exitConditions: toSpecs(exit),
      reinvestDividends: reinvest,
      includeCosts,
      feeDiscount: toDiscountRatio(includeCosts, feeDiscount),
    }
    console.info('[條件回測] 送出', request)
    setState((current) => ({ ...current, status: 'loading', error: null }))
    try {
      const response = await runStrategy(request)
      console.info(
        `[條件回測] 完成：條件策略 ${response.strategy.metrics.totalReturnPercent}%、` +
          `一直持有 ${response.benchmark.metrics.totalReturnPercent}%，交易 ${response.strategy.tradeCount} 筆`,
      )
      setState({ status: 'done', response, error: null })
    } catch (error) {
      setState((current) => ({ ...current, status: 'error', error }))
    }
  }

  const { response } = state

  return (
    <>
      <section className="card">
        <h2 className="section-title">條件回測</h2>
        <p className="muted compare-intro">設定買進和賣出的條件，看看照著條件進出，會不會比一直抱著更好？</p>

        {types.error && <p className="error">無法取得條件種類：{types.error.message}</p>}

        <form onSubmit={handleSubmit} className="compare-form">
          <div className="form-field">
            <span className="form-label">股票</span>
            <StockPicker selected={stocks} onChange={setStocks} max={1} />
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
              <span className="form-label">投入金額（元）</span>
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
            </label>
          </div>

          <div className="form-field">
            <span className="form-label">策略範本</span>
            <span className="form-presets">
              {TEMPLATES.map((template) => (
                <button key={template.name} type="button" className="chip" onClick={() => applyTemplate(template)}>
                  {template.name}
                </button>
              ))}
            </span>
          </div>

          {types.list.length > 0 && (
            <div className="condition-columns">
              <ConditionEditor
                title="買進條件"
                hint="空手時，以下條件全部成立，隔天開盤買進"
                rules={entry}
                onChange={setEntry}
                types={entryTypes}
                max={MAX_RULES}
              />
              <ConditionEditor
                title="賣出條件"
                hint="持有時，以下任一個條件成立，隔天開盤賣出"
                rules={exit}
                onChange={setExit}
                types={types.list}
                max={MAX_RULES}
              />
            </div>
          )}

          <div className="form-row">
            <label className="form-check">
              <input type="checkbox" checked={reinvest} onChange={(event) => setReinvest(event.target.checked)} />
              股利再投入（條件策略與一直持有都適用）
            </label>
            <FeeSettings
              includeCosts={includeCosts}
              onIncludeCostsChange={setIncludeCosts}
              feeDiscount={feeDiscount}
              onFeeDiscountChange={setFeeDiscount}
            />
          </div>

          <button type="submit" className="primary-button" disabled={!canSubmit}>
            {state.status === 'loading' ? '計算中…' : '開始回測'}
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
            {response.stockId} {response.name}｜{response.startDate} ～ {response.endDate}，投入{' '}
            {formatMoney(response.initialCapital)} 元
          </h2>
          <p className="strategy-rules">
            <span>
              <strong>買進：</strong>
              {response.entryDescription}
            </span>
            <span>
              <strong>賣出：</strong>
              {response.exitDescription}
            </span>
            <span>
              <strong>股利：</strong>
              {response.reinvestDividends ? '再投入' : '領現金'}
            </span>
          </p>
          <p className="strategy-verdict">
            條件策略{' '}
            <span className={trendClass(response.strategy.metrics.totalReturnPercent)}>
              {formatSigned(response.strategy.metrics.totalReturnPercent)}%
            </span>
            ，一直持有{' '}
            <span className={trendClass(response.benchmark.metrics.totalReturnPercent)}>
              {formatSigned(response.benchmark.metrics.totalReturnPercent)}%
            </span>
          </p>

          {response.strategy.pendingSignal && (
            <div className="notice" role="status">
              {response.strategy.pendingSignal}
            </div>
          )}

          <StrategyChart
            strategyCurve={response.strategy.equityCurve}
            benchmarkCurve={response.benchmark.equityCurve}
            initialCapital={Number(response.initialCapital)}
          />
          <StrategySummary strategy={response.strategy} benchmark={response.benchmark} />

          <h3 className="section-title trades-title">交易明細</h3>
          <TradeTable trades={response.strategy.trades} />

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

export default StrategyPage
