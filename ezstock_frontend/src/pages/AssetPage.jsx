import { useEffect, useState } from 'react'
import { Link, useLocation } from 'react-router'
import {
  createAccount,
  deleteAccount,
  deleteHolding,
  getAssetSummary,
  saveHolding,
  updateAccount,
} from '../api/assetApi'
import AccountList from '../components/AccountList'
import AllocationBar from '../components/AllocationBar'
import HoldingTable from '../components/HoldingTable'
import { useAuth } from '../hooks/useAuth'
import { formatMoney, formatSigned, formatSignedMoney, trendClass } from '../utils/format'

/** 我的資產：淨資產 = 現金 + 股票現值 − 負債（要登入） */
function AssetPage() {
  const { status, isAuthenticated } = useAuth()
  const location = useLocation()
  const [state, setState] = useState({ status: 'loading', summary: null, error: null })
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState(null)

  useEffect(() => {
    if (!isAuthenticated) return
    let ignore = false
    getAssetSummary()
      .then((summary) => {
        if (ignore) return
        console.info(`[資產] 載入儀表板：淨資產 ${summary.netWorth}`)
        setState({ status: 'done', summary, error: null })
      })
      .catch((error) => {
        if (!ignore) setState({ status: 'error', summary: null, error })
      })
    return () => {
      ignore = true
    }
  }, [isAuthenticated])

  /** 執行一個修改；後端回傳最新的儀表板，直接換上。成功回傳 true，表單才會清空 */
  async function run(action, label) {
    setBusy(true)
    setActionError(null)
    try {
      const summary = await action()
      setState({ status: 'done', summary, error: null })
      console.info(`[資產] ${label}，淨資產 ${summary.netWorth}`)
      return true
    } catch (error) {
      console.warn(`[資產] ${label}失敗：${error.message}`)
      setActionError(error)
      return false
    } finally {
      setBusy(false)
    }
  }

  if (status === 'checking') {
    return <p className="muted">確認登入狀態…</p>
  }

  if (!isAuthenticated) {
    return (
      <section className="card">
        <h2 className="section-title">我的資產</h2>
        <p className="muted">
          記錄現金、股票和負債，自動用最新股價算出淨資產。請先
          <Link to="/login" state={{ from: location.pathname }}>
            登入
          </Link>
          。
        </p>
      </section>
    )
  }

  if (state.status === 'loading') {
    return <p className="muted">載入資產…</p>
  }

  if (state.status === 'error') {
    return (
      <section className="card">
        <p className="error">{state.error.message}</p>
        {state.error.traceId && <p className="muted">錯誤代碼：{state.error.traceId}</p>}
      </section>
    )
  }

  const { summary } = state
  const accountsOf = (kind) => summary.accounts.filter((account) => account.kind === kind)

  return (
    <>
      <section className="card">
        <h2 className="section-title">我的資產</h2>
        <div className="asset-hero">
          <span className="stat-label muted">淨資產</span>
          <span className="asset-hero-value">{formatMoney(summary.netWorth)} 元</span>
          <span className="muted">現金 + 股票現值 − 負債</span>
        </div>
        <div className="stat-row">
          <div className="stat">
            <div className="stat-label muted">總資產</div>
            <div className="stat-value">{formatMoney(summary.totalAssets)}</div>
          </div>
          <div className="stat">
            <div className="stat-label muted">負債</div>
            <div className="stat-value">{formatMoney(summary.liabilityTotal)}</div>
          </div>
          <div className="stat">
            <div className="stat-label muted">股票未實現損益</div>
            <div className={`stat-value ${trendClass(summary.stockUnrealizedPnl)}`}>
              {formatSignedMoney(summary.stockUnrealizedPnl)}
              {summary.stockReturnPercent != null && (
                <span className="asset-percent">（{formatSigned(summary.stockReturnPercent)}%）</span>
              )}
            </div>
          </div>
        </div>
        <AllocationBar summary={summary} />
        {summary.notes.length > 0 && (
          <div className="notice" role="status">
            <ul>
              {summary.notes.map((note) => (
                <li key={note}>{note}</li>
              ))}
            </ul>
          </div>
        )}
      </section>

      <section className="card compare-result">
        {actionError && (
          <p className="error" role="alert">
            {actionError.message}
          </p>
        )}
        <HoldingTable
          holdings={summary.holdings}
          busy={busy}
          onSave={(stock, holding) =>
            run(() => saveHolding(stock.stockId, holding), `儲存持股 ${stock.stockId} ${stock.name}`)
          }
          onDelete={(holding) =>
            window.confirm(`確定刪除持股 ${holding.stockId} ${holding.name}？`) &&
            run(() => deleteHolding(holding.stockId), `刪除持股 ${holding.stockId}`)
          }
        />
        <div className="asset-accounts">
          {['CASH', 'LIABILITY'].map((kind) => (
            <AccountList
              key={kind}
              kind={kind}
              accounts={accountsOf(kind)}
              busy={busy}
              onCreate={(account) => run(() => createAccount(account), `新增帳戶 ${account.name}`)}
              onUpdate={(id, account) => run(() => updateAccount(id, account), `修改帳戶 id=${id}`)}
              onDelete={(account) =>
                window.confirm(`確定刪除「${account.name}」？`) &&
                run(() => deleteAccount(account.id), `刪除帳戶 id=${account.id}`)
              }
            />
          ))}
        </div>
      </section>
    </>
  )
}

export default AssetPage
