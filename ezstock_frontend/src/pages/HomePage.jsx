import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import MarketOverview from '../components/MarketOverview'
import NewsList from '../components/NewsList'
import WatchlistCard from '../components/WatchlistCard'
import { useStockSearch } from '../hooks/useStockSearch'
import { useWatchlist } from '../hooks/useWatchlist'
import { isUsStock } from '../utils/format'

const POPULAR_STOCKS = [
  { stockId: '2330', name: '台積電' },
  { stockId: '2317', name: '鴻海' },
  { stockId: '2454', name: '聯發科' },
  { stockId: '2308', name: '台達電' },
  { stockId: '2882', name: '國泰金' },
  { stockId: '0050', name: '元大台灣50' },
  { stockId: '0056', name: '元大高股息' },
  { stockId: '00878', name: '國泰永續高股息' },
]

const POPULAR_US_STOCKS = [
  { stockId: 'AAPL', name: 'Apple' },
  { stockId: 'NVDA', name: 'NVIDIA' },
  { stockId: 'MSFT', name: 'Microsoft' },
  { stockId: 'TSLA', name: 'Tesla' },
  { stockId: 'TSM', name: '台積電 ADR' },
  { stockId: 'SPY', name: 'S&P 500 ETF' },
  { stockId: 'QQQ', name: 'Nasdaq 100 ETF' },
  { stockId: 'VT', name: '全世界股票 ETF' },
]

const TYPE_LABELS = { STOCK: '股票', ETF: 'ETF' }

// 後端一次最多查 20 檔新聞：自選股優先，剩下的名額給熱門股（新聞只有台股）
const MAX_NEWS_STOCKS = 20

/** 首頁：輸入代號或名稱搜尋股票 */
function HomePage() {
  const navigate = useNavigate()
  // input：輸入框上看到的字；query：真正拿去搜尋的字。
  // 用注音、倉頡打字時，選字前輸入框會先出現「ㄊㄞ」這種組字中的符號，
  // 這時只更新 input，等選完字（compositionend）才更新 query，避免拿注音去搜尋。
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const keyword = query.trim()
  const { stocks, error, loading, isCurrent } = useStockSearch(keyword)
  const watchlist = useWatchlist()
  // 等登入狀態和自選股都確定了才抓新聞，避免先用熱門股抓一次、自選股載入後又抓一次
  const newsReady = !watchlist.authChecking && !watchlist.loading
  const newsStockIds = [
    ...new Set([...watchlist.items.map((item) => item.stockId), ...POPULAR_STOCKS.map((stock) => stock.stockId)]),
  ]
    .filter((stockId) => !isUsStock(stockId))
    .slice(0, MAX_NEWS_STOCKS)

  // 按 Enter：代號完全相符或只有一筆結果，就直接進個股頁
  function handleSubmit(event) {
    event.preventDefault()
    if (!isCurrent) return
    // 美股代號不分大小寫：輸入 aapl 也算 AAPL
    const exact = stocks.find((stock) => stock.stockId.toUpperCase() === keyword.toUpperCase())
    const target = exact ?? (stocks.length === 1 ? stocks[0] : null)
    if (target) {
      navigate(`/stocks/${target.stockId}`)
    }
  }

  return (
    <>
      <section className="card">
        <form onSubmit={handleSubmit}>
          <label htmlFor="search" className="search-label">
            搜尋股票
          </label>
          <input
            id="search"
            className="search-input"
            type="search"
            placeholder="輸入代號或名稱，例如 2330、台積、AAPL"
            value={input}
            onChange={(event) => {
              setInput(event.target.value)
              if (!event.nativeEvent.isComposing) setQuery(event.target.value)
            }}
            onCompositionEnd={(event) => setQuery(event.currentTarget.value)}
            autoComplete="off"
            autoFocus
          />
        </form>

        {loading && <p className="muted">搜尋中…</p>}
        {isCurrent && error && <p className="error">{error.message}</p>}
        {isCurrent && !error && stocks.length === 0 && (
          <p className="muted">找不到符合「{keyword}」的股票</p>
        )}
        {isCurrent && stocks.length > 0 && (
          <ul className="search-results">
            {stocks.map((stock) => (
              <li key={stock.stockId}>
                <Link to={`/stocks/${stock.stockId}`} className="search-result">
                  <span className="search-result-id">{stock.stockId}</span>
                  <span className="search-result-name">{stock.name}</span>
                  <span className="muted">
                    {stock.market === 'US' && <span className="market-badge market-us">美股</span>}
                    {TYPE_LABELS[stock.type] ?? stock.type}
                    {stock.industry && ` · ${stock.industry}`}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {!keyword && <MarketOverview />}

      {!keyword && (
        <div className="home-grid">
          {newsReady ? (
            <NewsList stockIds={newsStockIds} title={watchlist.items.length > 0 ? '自選股與熱門股新聞' : '熱門股新聞'} />
          ) : (
            <section className="card news" aria-busy="true" />
          )}
          <div className="home-side">
            <WatchlistCard />
            <section className="card popular">
              <h2 className="section-title">熱門個股</h2>
              <div className="chips">
                {POPULAR_STOCKS.map((stock) => (
                  <Link key={stock.stockId} to={`/stocks/${stock.stockId}`} className="chip">
                    {stock.stockId} {stock.name}
                  </Link>
                ))}
              </div>
              <h3 className="popular-subtitle muted">美股</h3>
              <div className="chips">
                {POPULAR_US_STOCKS.map((stock) => (
                  <Link key={stock.stockId} to={`/stocks/${stock.stockId}`} className="chip">
                    {stock.stockId} {stock.name}
                  </Link>
                ))}
              </div>
            </section>
          </div>
        </div>
      )}
    </>
  )
}

export default HomePage
