import { Link, useLocation } from 'react-router'
import { useWatchlist } from '../hooks/useWatchlist'

/** 首頁的「我的自選股」 */
function WatchlistCard() {
  const { items, remove, signedIn, authChecking, loading, loadFailed } = useWatchlist()
  const location = useLocation()

  if (authChecking) {
    return null
  }

  if (!signedIn) {
    return (
      <section className="card watchlist">
        <h2 className="section-title">我的自選股</h2>
        <p className="muted">
          <Link to="/login" state={{ from: location.pathname }}>
            登入
          </Link>
          後就能使用自選股，換電腦登入也看得到。
        </p>
      </section>
    )
  }

  return (
    <section className="card watchlist">
      <h2 className="section-title">我的自選股</h2>
      {loadFailed && <p className="error">自選股載入失敗，請重新整理頁面</p>}
      {loading ? (
        <p className="muted">載入自選股…</p>
      ) : items.length === 0 ? (
        <p className="muted">還沒有自選股。進入個股頁按「☆ 加入自選」就會出現在這裡。</p>
      ) : (
        <ul className="watchlist-items">
          {items.map((item) => (
            <li key={item.stockId} className="watchlist-item">
              <Link to={`/stocks/${item.stockId}`} className="watchlist-link">
                <span className="search-result-id">{item.stockId}</span>
                <span>{item.name}</span>
              </Link>
              <button
                type="button"
                className="icon-button"
                onClick={() => remove(item.stockId)}
                aria-label={`從自選股移除 ${item.stockId} ${item.name}`}
              >
                ✕
              </button>
            </li>
          ))}
        </ul>
      )}
      <p className="chart-caption muted">自選股存在你的帳號裡，換電腦登入也看得到</p>
    </section>
  )
}

export default WatchlistCard
