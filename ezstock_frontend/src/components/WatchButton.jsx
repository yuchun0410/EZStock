import { useWatchlist } from '../hooks/useWatchlist'

/** 個股頁標題旁的星號：加入 / 移除自選股 */
function WatchButton({ stock }) {
  const { has, toggle, isFull } = useWatchlist()
  const watched = has(stock.stockId)
  const disabled = !watched && isFull

  return (
    <button
      type="button"
      className={`watch-button ${watched ? 'watched' : ''}`}
      onClick={() => toggle(stock)}
      disabled={disabled}
      aria-pressed={watched}
      title={disabled ? '自選股已達上限 50 檔' : undefined}
    >
      <span aria-hidden="true">{watched ? '★' : '☆'}</span>
      {watched ? '已加入自選' : '加入自選'}
    </button>
  )
}

export default WatchButton
