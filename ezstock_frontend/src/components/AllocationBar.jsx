import { formatMoney } from '../utils/format'
import { SERIES_COLORS } from './chartConfig'

/**
 * 資產配置：一條橫向長條，現金和股票各佔總資產多少。
 * 只有兩三個部分，用長條比圓餅圖更容易比較長短；每段都直接標百分比，不只靠顏色分辨。
 * 顏色沿用驗證過的配色，依固定順序：現金第 1 色、股票第 2 色。
 */
const PARTS = [
  { key: 'cashTotal', label: '現金', color: SERIES_COLORS[0] },
  { key: 'stockValue', label: '股票', color: SERIES_COLORS[1] },
]

function AllocationBar({ summary }) {
  const total = Number(summary.totalAssets)
  if (!(total > 0)) {
    return <p className="muted">新增現金或持股後，這裡會顯示資產配置。</p>
  }

  const parts = PARTS.map((part) => {
    const value = Number(summary[part.key])
    return { ...part, value, percent: (value / total) * 100 }
  }).filter((part) => part.value > 0)

  return (
    <figure className="allocation">
      <div className="allocation-bar" role="img" aria-label={describe(parts)}>
        {parts.map((part) => (
          <div
            key={part.key}
            className="allocation-segment"
            style={{ flexGrow: part.value, background: part.color }}
            title={`${part.label} ${formatMoney(part.value)} 元（${part.percent.toFixed(1)}%）`}
          />
        ))}
      </div>
      <figcaption className="allocation-legend">
        {parts.map((part) => (
          <span key={part.key} className="allocation-item">
            <span className="legend-dot" style={{ background: part.color }} aria-hidden="true" />
            {part.label}
            <strong>{part.percent.toFixed(1)}%</strong>
            <span className="muted">{formatMoney(part.value)} 元</span>
          </span>
        ))}
        {Number(summary.liabilityTotal) > 0 && (
          <span className="allocation-item muted">
            負債佔總資產 {((Number(summary.liabilityTotal) / total) * 100).toFixed(1)}%
          </span>
        )}
      </figcaption>
    </figure>
  )
}

function describe(parts) {
  return '資產配置：' + parts.map((part) => `${part.label} ${part.percent.toFixed(1)}%`).join('、')
}

export default AllocationBar
