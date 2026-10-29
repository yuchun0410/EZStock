import { useState } from 'react'
import { useStockSearch } from '../hooks/useStockSearch'
import { useWatchlist } from '../hooks/useWatchlist'
import { SERIES_COLORS } from './chartConfig'

/**
 * 選擇要比較的標的（最多 max 檔）：
 * 上面是已選的標的（顏色跟圖表一致），下面可以搜尋，或從自選股一鍵加入。
 */
function StockPicker({ selected, onChange, max }) {
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')
  const keyword = query.trim()
  const { stocks, loading } = useStockSearch(keyword)
  const { items: watchlist } = useWatchlist()

  const isFull = selected.length >= max
  const selectedIds = new Set(selected.map((s) => s.stockId))
  const suggestions = stocks.filter((s) => !selectedIds.has(s.stockId)).slice(0, 8)
  const watchlistToAdd = watchlist.filter((s) => !selectedIds.has(s.stockId))

  function add(stock) {
    if (isFull || selectedIds.has(stock.stockId)) return
    onChange([...selected, { stockId: stock.stockId, name: stock.name }])
    setInput('')
    setQuery('')
  }

  function remove(stockId) {
    onChange(selected.filter((s) => s.stockId !== stockId))
  }

  return (
    <div className="picker">
      <div className="picker-selected">
        {selected.map((stock, index) => (
          <span key={stock.stockId} className="picker-chip">
            <span className="legend-dot" style={{ background: SERIES_COLORS[index] }} />
            {stock.stockId} {stock.name}
            <button
              type="button"
              className="icon-button"
              onClick={() => remove(stock.stockId)}
              aria-label={`移除 ${stock.stockId} ${stock.name}`}
            >
              ✕
            </button>
          </span>
        ))}
        {selected.length === 0 && <span className="muted">還沒選擇標的</span>}
      </div>

      <div className="picker-search">
        <input
          className="search-input"
          type="search"
          placeholder={placeholder(isFull, max)}
          value={input}
          disabled={isFull}
          onChange={(event) => {
            setInput(event.target.value)
            // 注音選字中不送出搜尋，等選完字才更新（跟首頁一樣）
            if (!event.nativeEvent.isComposing) setQuery(event.target.value)
          }}
          onCompositionEnd={(event) => setQuery(event.currentTarget.value)}
          onKeyDown={(event) => {
            // 在表單裡按 Enter 會送出整個表單，這裡改成「加入第一個搜尋結果」
            if (event.key === 'Enter' && !event.nativeEvent.isComposing) {
              event.preventDefault()
              if (suggestions.length > 0) add(suggestions[0])
            }
          }}
          autoComplete="off"
        />
        {keyword && !isFull && (
          <ul className="picker-suggestions">
            {loading && <li className="muted picker-hint">搜尋中…</li>}
            {!loading && suggestions.length === 0 && <li className="muted picker-hint">找不到可以加入的標的</li>}
            {suggestions.map((stock) => (
              <li key={stock.stockId}>
                <button type="button" className="picker-suggestion" onClick={() => add(stock)}>
                  <span className="search-result-id">{stock.stockId}</span>
                  <span>{stock.name}</span>
                </button>
              </li>
            ))}
          </ul>
        )}
      </div>

      {watchlistToAdd.length > 0 && !isFull && (
        <div className="picker-watchlist">
          <span className="muted">從自選股加入：</span>
          {watchlistToAdd.map((stock) => (
            <button key={stock.stockId} type="button" className="chip" onClick={() => add(stock)}>
              + {stock.stockId} {stock.name}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}

function placeholder(isFull, max) {
  if (!isFull) return max === 1 ? '搜尋代號或名稱，例如 0050、台積' : '搜尋代號或名稱加入比較，例如 0050、台積'
  return max === 1 ? '要換股票，請先按上面的 ✕ 移除' : `最多比較 ${max} 檔`
}

export default StockPicker
