import { Link } from 'react-router'
import { formatPrice, formatSigned, trendClass } from '../utils/format'

/**
 * 文章上的股票標籤：代號、名稱、最新收盤與漲跌幅（漲紅、跌綠，前面加 ▲▼，不只靠顏色分辨）。
 *
 * @param onClick 有給的話是按鈕（論壇頁：篩選這檔），沒給就連到個股頁
 */
function StockTagChip({ stock, onClick }) {
  const content = (
    <>
      <span>
        {stock.stockId} {stock.name}
      </span>
      {stock.close != null && (
        <span className="stock-tag-quote">
          {formatPrice(stock.close)}
          {stock.changePercent != null && (
            <span className={trendClass(stock.changePercent)}>
              {' '}
              {arrow(stock.changePercent)}
              {formatSigned(stock.changePercent)}%
            </span>
          )}
        </span>
      )}
    </>
  )
  const title = stock.priceDate ? `${stock.priceDate} 收盤` : undefined

  if (onClick) {
    return (
      <button type="button" className="stock-tag" onClick={() => onClick(stock)} title={title}>
        {content}
      </button>
    )
  }
  return (
    <Link to={`/stocks/${stock.stockId}`} className="stock-tag" title={title}>
      {content}
    </Link>
  )
}

function arrow(value) {
  const number = Number(value)
  if (number > 0) return '▲'
  if (number < 0) return '▼'
  return ''
}

export default StockTagChip
