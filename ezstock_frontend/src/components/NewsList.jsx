import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { getNews } from '../api/newsApi'

/**
 * 新聞列表：首頁（熱門股 + 自選股）和個股頁共用。
 * 只顯示標題、來源、時間，點標題開新分頁看原文（內文版權屬於新聞來源）。
 */
function NewsList({ stockIds, limit = 20, title = '相關新聞', showStocks = true }) {
  // 用字串當作這次查詢的身分：陣列每次重新渲染都是新的物件，不能直接放進 useEffect 的依賴
  const idsKey = stockIds.join(',')
  const requestKey = `${idsKey}|${limit}`
  const [result, setResult] = useState({ requestKey: null, news: [], error: null })

  useEffect(() => {
    if (!idsKey) return
    const ids = idsKey.split(',')
    let ignore = false
    getNews(ids, limit)
      .then((news) => {
        if (ignore) return
        console.info(`[新聞] ${ids.length} 檔股票，共 ${news.length} 則`)
        setResult({ requestKey, news, error: null })
      })
      .catch((error) => {
        if (!ignore) setResult({ requestKey, news: [], error })
      })
    return () => {
      ignore = true
    }
  }, [idsKey, limit, requestKey])

  const loading = result.requestKey !== requestKey

  return (
    <section className="card news">
      <h2 className="section-title">{title}</h2>
      {loading && <NewsSkeleton />}
      {!loading && result.error && <p className="error">{result.error.message}</p>}
      {!loading && !result.error && result.news.length === 0 && (
        <p className="muted">最近幾天沒有相關新聞</p>
      )}
      {!loading && result.news.length > 0 && (
        <ol className="news-list">
          {result.news.map((item) => (
            <li key={item.link} className="news-item">
              <a className="news-title" href={item.link} target="_blank" rel="noopener noreferrer">
                {item.title}
              </a>
              <div className="news-meta">
                <span className="muted">{item.source}</span>
                <time className="muted" dateTime={item.publishedAt}>
                  {formatAgo(item.publishedAt)}
                </time>
                {showStocks &&
                  item.stocks.map((stock) => (
                    <Link key={stock.stockId} to={`/stocks/${stock.stockId}`} className="stock-tag">
                      {stock.stockId} {stock.name}
                    </Link>
                  ))}
              </div>
            </li>
          ))}
        </ol>
      )}
      <p className="chart-caption muted">新聞來源：FinMind，點標題看原文</p>
    </section>
  )
}

function NewsSkeleton() {
  return (
    <ol className="news-list" aria-busy="true" aria-label="新聞載入中">
      {[0, 1, 2, 3].map((i) => (
        <li key={i} className="news-item">
          <span className="skeleton skeleton-title" />
          <span className="skeleton skeleton-meta" />
        </li>
      ))}
    </ol>
  )
}

/** "2026-11-03T13:30:00" → 「5 分鐘前」「3 小時前」；超過一天顯示「11/02 20:00」 */
function formatAgo(isoDateTime) {
  const time = new Date(isoDateTime)
  const minutes = Math.floor((Date.now() - time.getTime()) / 60000)
  if (Number.isNaN(minutes)) return ''
  if (minutes < 1) return '剛剛'
  if (minutes < 60) return `${minutes} 分鐘前`
  if (minutes < 24 * 60) return `${Math.floor(minutes / 60)} 小時前`
  const [date, clock = ''] = isoDateTime.split('T')
  return `${date.slice(5).replace('-', '/')} ${clock.slice(0, 5)}`.trim()
}

export default NewsList
