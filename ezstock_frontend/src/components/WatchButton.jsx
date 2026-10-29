import { useLocation, useNavigate } from 'react-router'
import { MAX_ITEMS, useWatchlist } from '../hooks/useWatchlist'

/** 個股頁標題旁的星號：加入 / 移除自選股。沒登入時按下去會先去登入，登入完回到這一頁 */
function WatchButton({ stock }) {
  const { has, toggle, isFull, signedIn, authChecking } = useWatchlist()
  const navigate = useNavigate()
  const location = useLocation()
  const watched = has(stock.stockId)
  const disabled = authChecking || (signedIn && !watched && isFull)

  function handleClick() {
    if (!signedIn) {
      console.info(`[自選股] 沒登入，先去登入頁（要加入 ${stock.stockId}）`)
      navigate('/login', { state: { from: location.pathname + location.search } })
      return
    }
    toggle(stock)
  }

  return (
    <button
      type="button"
      className={`watch-button ${watched ? 'watched' : ''}`}
      onClick={handleClick}
      disabled={disabled}
      aria-pressed={watched}
      title={
        !signedIn ? '登入後就能使用自選股' : disabled ? `自選股已達上限 ${MAX_ITEMS} 檔` : undefined
      }
    >
      <span aria-hidden="true">{watched ? '★' : '☆'}</span>
      {watched ? '已加入自選' : '加入自選'}
    </button>
  )
}

export default WatchButton
