import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getConditionTypes } from '../api/backtestApi'
import { screenStocks } from '../api/screenerApi'
import ConditionEditor from '../components/ConditionEditor'
import { useWatchlist } from '../hooks/useWatchlist'
import { formatPrice, formatSigned, trendClass } from '../utils/format'

const MAX_RULES = 3

// 常見的選股條件，點一下就填好。第一個是進頁面時的預設值
const TEMPLATES = [
  { name: '站上月線', conditions: [{ type: 'CLOSE_ABOVE_MOVING_AVERAGE', value: '20' }] },
  { name: '站上季線', conditions: [{ type: 'CLOSE_ABOVE_MOVING_AVERAGE', value: '60' }] },
  { name: '突破一個月高點', conditions: [{ type: 'CLOSE_ABOVE_HIGHEST_HIGH', value: '20' }] },
  { name: '跌破一週低點', conditions: [{ type: 'CLOSE_BELOW_LOWEST_LOW', value: '5' }] },
]

/** 條件選股：用跟條件回測一樣的條件，找出「今天」符合的股票 */
function ScreenerPage() {
  const watchlist = useWatchlist()
  const [types, setTypes] = useState({ list: [], error: null })
  const [conditions, setConditions] = useState(TEMPLATES[0].conditions)
  const [scope, setScope] = useState('all')
  const [state, setState] = useState({ status: 'idle', response: null, error: null })

  useEffect(() => {
    let ignore = false
    getConditionTypes()
      .then((list) => {
        if (!ignore) setTypes({ list: list.filter((t) => t.entryAllowed), error: null })
      })
      .catch((error) => {
        if (!ignore) setTypes({ list: [], error })
      })
    return () => {
      ignore = true
    }
  }, [])

  const watchlistEmpty = watchlist.items.length === 0
  const canSubmit =
    conditions.length > 0 &&
    conditions.every((c) => c.value !== '') &&
    types.list.length > 0 &&
    !(scope === 'watchlist' && watchlistEmpty) &&
    state.status !== 'loading'

  function applyTemplate(template) {
    console.info(`[條件選股] 套用範本：${template.name}`)
    setConditions(template.conditions)
  }

  async function handleSubmit(event) {
    event.preventDefault()
    if (!canSubmit) return
    const request = {
      stockIds: scope === 'watchlist' ? watchlist.items.map((item) => item.stockId) : null,
      conditions: conditions.map((c) => ({ type: c.type, value: Number(c.value) })),
    }
    console.info('[條件選股] 送出', request)
    setState((current) => ({ ...current, status: 'loading', error: null }))
    try {
      const response = await screenStocks(request)
      console.info(
        `[條件選股] 完成：掃描 ${response.scannedCount} 檔，符合 ${response.matches.length} 檔，` +
          `略過 ${response.skipped.length} 檔`,
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
        <h2 className="section-title">條件選股</h2>
        <p className="muted compare-intro">用最新一天的收盤，找出符合條件的股票。條件跟條件回測一樣，可以先回測看看再拿來選股。</p>

        {types.error && <p className="error">無法取得條件種類：{types.error.message}</p>}

        <form onSubmit={handleSubmit} className="compare-form">
          <div className="form-field">
            <span className="form-label">掃描範圍</span>
            <span className="form-presets">
              <label className="form-check">
                <input type="radio" name="scope" checked={scope === 'all'} onChange={() => setScope('all')} />
                資料庫全部
              </label>
              <label className="form-check">
                <input
                  type="radio"
                  name="scope"
                  checked={scope === 'watchlist'}
                  onChange={() => setScope('watchlist')}
                />
                自選股（{watchlist.items.length} 檔）
              </label>
            </span>
            {scope === 'watchlist' && watchlistEmpty && (
              <p className="muted form-hint">還沒有自選股，到個股頁按星號加入。</p>
            )}
          </div>

          <div className="form-field">
            <span className="form-label">常用條件</span>
            <span className="form-presets">
              {TEMPLATES.map((template) => (
                <button key={template.name} type="button" className="chip" onClick={() => applyTemplate(template)}>
                  {template.name}
                </button>
              ))}
            </span>
          </div>

          {types.list.length > 0 && (
            <ConditionEditor
              title="選股條件"
              hint="以下條件全部成立才列出"
              rules={conditions}
              onChange={setConditions}
              types={types.list}
              max={MAX_RULES}
            />
          )}

          <button type="submit" className="primary-button" disabled={!canSubmit}>
            {state.status === 'loading' ? '掃描中…' : '開始選股'}
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
            {response.conditionDescription}｜掃描 {response.scannedCount} 檔，符合 {response.matches.length} 檔
          </h2>

          {response.matches.length === 0 ? (
            <p className="muted">目前沒有符合條件的股票。</p>
          ) : (
            <div className="table-wrap">
              <table className="table">
                <thead>
                  <tr>
                    <th>股票</th>
                    <th>產業</th>
                    <th>日期</th>
                    <th className="num">收盤</th>
                    <th className="num">漲跌</th>
                    <th className="num">漲跌幅</th>
                  </tr>
                </thead>
                <tbody>
                  {response.matches.map((m) => (
                    <tr key={m.stockId}>
                      <td>
                        <Link to={`/stocks/${m.stockId}`}>
                          {m.stockId} {m.name}
                        </Link>
                      </td>
                      <td className="muted">{m.industry}</td>
                      <td>{m.date}</td>
                      <td className="num">{formatPrice(m.close)}</td>
                      <td className={`num ${trendClass(m.change)}`}>{m.change == null ? '—' : formatSigned(m.change)}</td>
                      <td className={`num ${trendClass(m.change)}`}>
                        {m.changePercent == null ? '—' : `${formatSigned(m.changePercent)}%`}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {response.skipped.length > 0 && (
            <details className="assumptions">
              <summary>略過 {response.skipped.length} 檔（無法判斷）</summary>
              <ul>
                {response.skipped.map((text) => (
                  <li key={text}>{text}</li>
                ))}
              </ul>
            </details>
          )}
          <p className="muted">選股結果僅供參考，不構成投資建議。</p>
        </section>
      )}
    </>
  )
}

export default ScreenerPage
