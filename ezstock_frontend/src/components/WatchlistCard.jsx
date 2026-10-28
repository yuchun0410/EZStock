import { Link } from 'react-router'
import { useWatchlist } from '../hooks/useWatchlist'

/** 首頁的「我的自選股」 */
function WatchlistCard() {
  const { items, remove } = useWatchlist()

  return (
    <section className="card watchlist">
      <h2 className="section-title">我的自選股</h2>
      {items.length === 0 ? (
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
      <p className="chart-caption muted">自選股目前存在這台電腦的瀏覽器裡</p>
    </section>
  )
}

export default WatchlistCard
