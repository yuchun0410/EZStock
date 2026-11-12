import { useEffect, useState } from 'react'
import { getInstitutional } from '../api/marketApi'
import { trendClass } from '../utils/format'

const hundredMillion = new Intl.NumberFormat('zh-TW', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

/** 金額（元）→ 億元，例如 -3411732990 → "-34.12" */
function toYi(value) {
  const n = Number(value) / 1e8
  return (n > 0 ? '+' : '') + hundredMillion.format(n)
}

/**
 * 首頁的三大法人買賣超：外資、投信、自營商、合計。
 * 買超紅、賣超綠（跟漲跌同一套顏色），長條長度代表金額大小，用同一個比例尺比較三類法人。
 */
function InstitutionalFlow() {
  const [state, setState] = useState({ status: 'loading', data: null })

  useEffect(() => {
    let ignore = false
    getInstitutional()
      .then((data) => {
        if (!ignore) setState({ status: 'done', data })
      })
      .catch((error) => {
        console.warn(`[首頁] 三大法人載入失敗：${error.message}`)
        if (!ignore) setState({ status: 'error', data: null })
      })
    return () => {
      ignore = true
    }
  }, [])

  const { data } = state
  if (state.status === 'error' || (state.status === 'done' && !data?.date)) return null
  if (state.status === 'loading') return <section className="card institutional is-loading" aria-busy="true" />

  const rows = [...data.groups, data.total]
  const max = Math.max(...data.groups.map((g) => Math.abs(Number(g.net))), 1)

  return (
    <section className="card institutional" aria-label="三大法人買賣超">
      <div className="institutional-head">
        <h2 className="section-title">三大法人買賣超</h2>
        <span className="muted">{data.date}，單位：億元</span>
      </div>
      <dl className="institutional-rows">
        {rows.map((row) => {
          const trend = trendClass(row.net)
          const isTotal = row === data.total
          const width = isTotal ? 0 : (Math.abs(Number(row.net)) / max) * 100
          return (
            <div key={row.name} className={`institutional-row ${isTotal ? 'is-total' : ''}`}>
              <dt>{row.name}</dt>
              <dd className="institutional-bar-cell" aria-hidden="true">
                {!isTotal && <span className={`institutional-bar ${trend}`} style={{ width: `${width}%` }} />}
              </dd>
              <dd className={`institutional-net ${trend}`}>
                {toYi(row.net)}
                <span className="muted institutional-label">{Number(row.net) >= 0 ? '買超' : '賣超'}</span>
              </dd>
            </div>
          )
        })}
      </dl>
    </section>
  )
}

export default InstitutionalFlow
