import { formatLots, formatPrice } from '../utils/format'

/** 滑鼠移到某一天（或某一週）時顯示的資訊框 */
function PriceTooltip({ active, payload, weekly }) {
  if (!active || !payload?.length) return null
  const p = payload[0].payload
  return (
    <div className="chart-tooltip">
      <div className="chart-tooltip-date">{weekly ? `${p.weekStart} 當週` : p.date}</div>
      <div>
        收盤 <strong>{formatPrice(p.close)}</strong>
      </div>
      <div className="muted">
        開 {formatPrice(p.open)}　高 {formatPrice(p.high)}　低 {formatPrice(p.low)}
      </div>
      <div className="muted">成交量 {formatLots(p.volume)} 張</div>
    </div>
  )
}

export default PriceTooltip
