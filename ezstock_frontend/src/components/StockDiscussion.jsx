import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { listPosts } from '../api/forumApi'
import PostList from './PostList'

const PREVIEW = 5

/** 個股頁最下面：「討論這檔的文章」，最多 5 篇，附看更多與發文連結 */
function StockDiscussion({ stockId, name }) {
  const [state, setState] = useState({ status: 'loading', data: null, error: null })

  useEffect(() => {
    let ignore = false
    listPosts({ stockId, page: 0 })
      .then((data) => {
        if (!ignore) setState({ status: 'done', data, error: null })
      })
      .catch((error) => {
        if (!ignore) setState({ status: 'error', data: null, error })
      })
    return () => {
      ignore = true
    }
  }, [stockId])

  const { data } = state
  const forumLink = `/forum?stockId=${encodeURIComponent(stockId)}&stockName=${encodeURIComponent(name)}`

  return (
    <section className="card compare-result">
      <div className="forum-header">
        <h3 className="section-title">討論這檔的文章{data ? `（${data.total}）` : ''}</h3>
        <Link to="/forum/new" state={{ stocks: [{ stockId, name }] }} className="link-button">
          + 發文討論
        </Link>
      </div>
      {state.status === 'loading' && <p className="muted">載入文章…</p>}
      {state.status === 'error' && <p className="error">{state.error.message}</p>}
      {data && <PostList posts={data.items.slice(0, PREVIEW)} />}
      {data && data.total > PREVIEW && (
        <p>
          <Link to={forumLink}>看全部 {data.total} 篇 →</Link>
        </p>
      )}
    </section>
  )
}

export default StockDiscussion
