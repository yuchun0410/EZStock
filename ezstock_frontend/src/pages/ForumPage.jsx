import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { listPosts } from '../api/forumApi'
import PostList from '../components/PostList'
import StockPicker from '../components/StockPicker'
import { useAuth } from '../hooks/useAuth'

/**
 * 論壇首頁。篩選條件和頁數放在網址（?stockId=2330&page=1），
 * 重新整理、上一頁、分享連結都會停在同一個地方。
 */
function ForumPage() {
  const { isAuthenticated } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const stockId = searchParams.get('stockId') ?? ''
  const stockName = searchParams.get('stockName') ?? ''
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0)
  // key 記錄這份資料是哪個條件查的；條件變了、新資料還沒回來時，就知道畫面上的是舊資料（不在 effect 裡同步 setState）
  const key = `${stockId}|${page}`
  const [state, setState] = useState({ key: null, data: null, error: null })

  useEffect(() => {
    let ignore = false
    listPosts({ stockId: stockId || undefined, page })
      .then((data) => {
        if (ignore) return
        console.info(`[論壇] 文章列表 ${stockId || '全部'} 第 ${page + 1} 頁，共 ${data.total} 篇`)
        setState({ key, data, error: null })
      })
      .catch((error) => {
        if (!ignore) setState({ key, data: null, error })
      })
    return () => {
      ignore = true
    }
  }, [key, stockId, page])

  function filterBy(stock) {
    const next = stock ? { stockId: stock.stockId, stockName: stock.name } : {}
    setSearchParams(next)
  }

  function goToPage(next) {
    const params = Object.fromEntries(searchParams)
    setSearchParams({ ...params, page: String(next) })
    window.scrollTo({ top: 0 })
  }

  const { data } = state
  const loading = state.key !== key
  const selected = stockId ? [{ stockId, name: stockName }] : []

  return (
    <section className="card">
      <div className="forum-header">
        <h2 className="section-title">論壇</h2>
        {isAuthenticated ? (
          <Link to="/forum/new" state={{ stocks: selected }} className="primary-button forum-new">
            發文
          </Link>
        ) : (
          <Link to="/login" state={{ from: '/forum/new' }} className="link-button">
            登入後發文
          </Link>
        )}
      </div>

      <div className="form-field forum-filter">
        <span className="form-label">只看某一檔的討論</span>
        <StockPicker selected={selected} onChange={(stocks) => filterBy(stocks[0])} max={1} />
      </div>

      {!loading && state.error && <p className="error">{state.error.message}</p>}
      {data && (
        <div className={loading ? 'is-stale' : ''}>
          <PostList posts={data.items} onStockClick={filterBy} />
          {data.totalPages > 1 && (
            <nav className="pagination" aria-label="分頁">
              <button type="button" className="link-button" disabled={page === 0} onClick={() => goToPage(page - 1)}>
                ← 上一頁
              </button>
              <span className="muted">
                第 {page + 1} / {data.totalPages} 頁（共 {data.total} 篇）
              </span>
              <button
                type="button"
                className="link-button"
                disabled={page + 1 >= data.totalPages}
                onClick={() => goToPage(page + 1)}
              >
                下一頁 →
              </button>
            </nav>
          )}
        </div>
      )}
      {!data && loading && <p className="muted">載入文章…</p>}
    </section>
  )
}

export default ForumPage
