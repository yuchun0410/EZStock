import { useEffect, useState } from 'react'
import { useParams } from 'react-router'
import { getStock } from '../api/stockApi'
import DividendSection from '../components/DividendSection'
import NewsList from '../components/NewsList'
import PriceSection from '../components/PriceSection'
import StockKeyStats from '../components/StockKeyStats'
import StockDiscussion from '../components/StockDiscussion'
import WatchButton from '../components/WatchButton'
import { formatPrice, formatSigned, trendClass } from '../utils/format'

const TYPE_LABELS = { STOCK: '股票', ETF: 'ETF' }
const MARKET_LABELS = { TW: '台股', US: '美股' }

/** 個股頁：網址 /stocks/:stockId */
function StockPage() {
  const { stockId } = useParams()
  // 把「這份結果屬於哪一檔」一起存起來，網址換股票時就能判斷結果是不是舊的
  const [result, setResult] = useState({ stockId: null, detail: null, error: null })

  useEffect(() => {
    // 使用者快速切換股票時，舊的請求可能比新的晚回來。
    // cleanup 把 ignore 設成 true，舊請求回來就不會蓋掉新畫面。
    let ignore = false
    getStock(stockId)
      .then((detail) => {
        if (!ignore) setResult({ stockId, detail, error: null })
      })
      .catch((error) => {
        if (!ignore) setResult({ stockId, detail: null, error })
      })
    return () => {
      ignore = true
    }
  }, [stockId])

  if (result.stockId !== stockId) {
    return <p className="muted">載入中…（第一次查詢的股票需要從外部抓資料，會稍微久一點）</p>
  }

  if (result.error) {
    return (
      <section className="card">
        <p className="error">{result.error.message}</p>
        {result.error.traceId && <p className="muted">錯誤代碼：{result.error.traceId}</p>}
      </section>
    )
  }

  const { detail } = result
  const us = detail.market === 'US'
  return (
    <>
      <section className="card card-hero">
        <div className="stock-heading">
          <h2 className="stock-title">
            {detail.stockId} {detail.name}
          </h2>
          <WatchButton stock={detail} />
        </div>
        <p className="stock-meta muted">
          <span className={`market-badge ${us ? 'market-us' : 'market-tw'}`}>{MARKET_LABELS[detail.market] ?? '台股'}</span>
          {TYPE_LABELS[detail.type] ?? detail.type}
          {detail.industry && ` · ${detail.industry}`}
        </p>

        {detail.latestClose == null ? (
          <p className="muted">尚無價格資料</p>
        ) : (
          <div className="stock-overview">
            <div>
              <div className="stock-price">
                <span className="stock-close">
                  {us && <span className="stock-currency">US$</span>}
                  {formatPrice(detail.latestClose)}
                </span>
                <span className={`stock-change ${trendClass(detail.change)}`}>
                  {formatSigned(detail.change)}（{formatSigned(detail.changePercent)}%）
                </span>
              </div>
              <p className="stock-date muted">
                {detail.latestDate} 收盤{us && '（美東時間）'}
              </p>
            </div>
            <StockKeyStats
              stockId={detail.stockId}
              latestDate={detail.latestDate}
              latestClose={detail.latestClose}
              change={detail.change}
            />
          </div>
        )}
      </section>
      {detail.latestDate && <PriceSection stockId={detail.stockId} latestDate={detail.latestDate} />}
      {us && detail.latestDate && (
        <p className="card muted us-price-note">
          美股走勢圖使用還原股價（已反映股利與分割），所以較早的價格會比當時的實際成交價低一些，
          長期報酬也包含了股利。資料來源目前沒有美股的股利明細與新聞。
        </p>
      )}
      {!us && detail.latestDate && (
        <DividendSection stockId={detail.stockId} latestDate={detail.latestDate} latestClose={detail.latestClose} />
      )}
      {!us && <NewsList stockIds={[detail.stockId]} limit={10} title="相關新聞" showStocks={false} />}
      <StockDiscussion stockId={detail.stockId} name={detail.name} />
    </>
  )
}

export default StockPage
