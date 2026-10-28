import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { searchStocks } from '../api/stockApi'
import WatchlistCard from '../components/WatchlistCard'

// 停止打字多久才送出搜尋（毫秒）。每打一個字就打 API 太浪費。
const SEARCH_DELAY_MS = 300

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

const TYPE_LABELS = { STOCK: '股票', ETF: 'ETF' }

/** 首頁：輸入代號或名稱搜尋股票 */
function HomePage() {
  const navigate = useNavigate()
  // input：輸入框上看到的字；query：真正拿去搜尋的字。
  // 用注音、倉頡打字時，選字前輸入框會先出現「ㄊㄞ」這種組字中的符號，
  // 這時只更新 input，等選完字（compositionend）才更新 query，避免拿注音去搜尋。
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const [result, setResult] = useState({ keyword: '', stocks: [], error: null })
  const keyword = query.trim()

  useEffect(() => {
    if (!keyword) return
    // 防抖（debounce）：每次打字都先取消上一個計時器，停下來 300ms 才真的搜尋
    let ignore = false
    const timer = setTimeout(() => {
      searchStocks(keyword)
        .then((stocks) => {
          if (!ignore) setResult({ keyword, stocks, error: null })
        })
        .catch((error) => {
          if (!ignore) setResult({ keyword, stocks: [], error })
        })
    }, SEARCH_DELAY_MS)
    return () => {
      ignore = true
      clearTimeout(timer)
    }
  }, [keyword])

  const isCurrent = keyword !== '' && result.keyword === keyword
  const loading = keyword !== '' && !isCurrent

  // 按 Enter：代號完全相符或只有一筆結果，就直接進個股頁
  function handleSubmit(event) {
    event.preventDefault()
    if (!isCurrent) return
    const exact = result.stocks.find((stock) => stock.stockId === keyword)
    const target = exact ?? (result.stocks.length === 1 ? result.stocks[0] : null)
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
            placeholder="輸入代號或名稱，例如 2330、台積"
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
        {isCurrent && result.error && <p className="error">{result.error.message}</p>}
        {isCurrent && !result.error && result.stocks.length === 0 && (
          <p className="muted">找不到符合「{keyword}」的股票</p>
        )}
        {isCurrent && result.stocks.length > 0 && (
          <ul className="search-results">
            {result.stocks.map((stock) => (
              <li key={stock.stockId}>
                <Link to={`/stocks/${stock.stockId}`} className="search-result">
                  <span className="search-result-id">{stock.stockId}</span>
                  <span className="search-result-name">{stock.name}</span>
                  <span className="muted">
                    {TYPE_LABELS[stock.type] ?? stock.type}
                    {stock.industry && ` · ${stock.industry}`}
                  </span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>

      {!keyword && <WatchlistCard />}

      {!keyword && (
        <section className="card popular">
          <h2 className="section-title">熱門個股</h2>
          <div className="chips">
            {POPULAR_STOCKS.map((stock) => (
              <Link key={stock.stockId} to={`/stocks/${stock.stockId}`} className="chip">
                {stock.stockId} {stock.name}
              </Link>
            ))}
          </div>
        </section>
      )}
    </>
  )
}

export default HomePage
