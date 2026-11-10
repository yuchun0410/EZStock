import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router'
import { listPosts } from '../api/forumApi'
import PostList from '../components/PostList'
import StockPicker from '../components/StockPicker'
import { useAuth } from '../hooks/useAuth'

const SORTS = [
  { value: 'hot', label: '熱門' },
  { value: 'latest', label: '最新' },
]

/**
 * 論壇首頁。排序、篩選條件和頁數放在網址（?sort=hot&stockId=2330&page=1），
 * 重新整理、上一頁、分享連結都會停在同一個地方。
 * 熱門：最近 30 天的文章依熱度分數（讚、留言、瀏覽，越新分數越高）排序；最新：依發文時間。
 */
function ForumPage() {
  const { isAuthenticated } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const stockId = searchParams.get('stockId') ?? ''
  const stockName = searchParams.get('stockName') ?? ''
  const page = Math.max(0, Number(searchParams.get('page') ?? 0) || 0)
  const sort = searchParams.get('sort') === 'latest' ? 'latest' : 'hot'
  // key 記錄這份資料是哪個條件查的；條件變了、新資料還沒回來時，就知道畫面上的是舊資料（不在 effect 裡同步 setState）
  const key = `${sort}|${stockId}|${page}`
  const [state, setState] = useState({ key: null, data: null, error: null })

  useEffect(() => {
    let ignore = false
    listPosts({ stockId: stockId || undefined, sort, page })
      .then((data) => {
        if (ignore) return
        console.info(`[論壇] 文章列表（${sort === 'hot' ? '熱門' : '最新'}）${stockId || '全部'} 第 ${page + 1} 頁，共 ${data.total} 篇`)
        setState({ key, data, error: null })
      })
      .catch((error) => {
        if (!ignore) setState({ key, data: null, error })
      })
    return () => {
      ignore = true
    }
  }, [key, stockId, page, sort])

  // 換篩選、換排序都回到第 1 頁
  function filterBy(stock) {
    const next = stock ? { stockId: stock.stockId, stockName: stock.name } : {}
    setSearchParams({ ...next, sort })
  }

  function sortBy(next) {
    const params = Object.fromEntries(searchParams)
    delete params.page
    setSearchParams({ ...params, sort: next })
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

      <div className="range-tabs forum-sort" role="tablist" aria-label="文章排序">
        {SORTS.map((option) => (
          <button
            key={option.value}
            type="button"
            role="tab"
            aria-selected={sort === option.value}
            className={`range-tab ${sort === option.value ? 'active' : ''}`}
            onClick={() => sortBy(option.value)}
          >
            {option.label}
          </button>
        ))}
      </div>
      {sort === 'hot' && <p className="muted forum-sort-hint">最近 30 天的文章，依按讚、留言、瀏覽數排序；同樣熱度的話，新文章排前面</p>}

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
