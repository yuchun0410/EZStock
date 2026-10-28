import { formatLots, formatPrice } from '../utils/format'
import { MOVING_AVERAGES } from './chartConfig'

/** 滑鼠移到某一天（或某一週）時顯示的資訊框 */
function PriceTooltip({ active, payload, weekly, maPeriods = [] }) {
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
      {MOVING_AVERAGES.filter((ma) => maPeriods.includes(ma.period) && p[`ma${ma.period}`] != null).map((ma) => (
        <div key={ma.period} className="chart-tooltip-ma">
          <span className="legend-swatch" style={{ background: ma.color }} />
          {ma.label} {formatPrice(p[`ma${ma.period}`])}
        </div>
      ))}
    </div>
  )
}

export default PriceTooltip
