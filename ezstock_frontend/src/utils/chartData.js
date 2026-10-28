/**
 * 把後端回傳的日線轉成圖表要用的格式。
 * 後端的價格是 BigDecimal，到 JSON 會變成數字；這裡統一轉成 Number 並補上圖表需要的欄位。
 */
export function toChartPoints(prices) {
  return prices.map((p) => withDerived({
    date: p.date,
    open: Number(p.open),
    high: Number(p.high),
    low: Number(p.low),
    close: Number(p.close),
    volume: Number(p.volume),
  }))
}

/**
 * 日線合併成週線：同一週（週一到週日）的
 * 開盤取第一天、收盤取最後一天、最高取最大、最低取最小、成交量加總。
 * 5 年有 1200 多根日 K，畫出來每根不到 1px，改用週 K 約 260 根比較看得清楚。
 */
export function toWeekly(points) {
  const weeks = []
  let current = null
  for (const p of points) {
    const week = mondayOf(p.date)
    if (!current || current.week !== week) {
      current = { week, date: p.date, open: p.open, high: p.high, low: p.low, close: p.close, volume: 0 }
      weeks.push(current)
    }
    current.high = Math.max(current.high, p.high)
    current.low = Math.min(current.low, p.low)
    current.close = p.close
    current.volume += p.volume
    current.date = p.date // 週 K 的日期用該週最後一個交易日
  }
  return weeks.map(({ week, ...rest }) => withDerived(rest, week))
}

function withDerived(point, weekStart) {
  return {
    ...point,
    ...(weekStart && { weekStart }),
    // K 線用：Recharts 的 Bar 給一個 [最低, 最高] 的陣列，就會畫出從最低到最高的範圍
    range: [point.low, point.high],
    // 成交量：FinMind 給的是「股」，台股習慣看「張」（1 張 = 1000 股）
    lots: Math.round(point.volume / 1000),
  }
}

/** "2026-10-28"（週三）→ "2026-10-26"（該週週一） */
function mondayOf(isoDate) {
  const [y, m, d] = isoDate.split('-').map(Number)
  const date = new Date(y, m - 1, d)
  const offset = (date.getDay() + 6) % 7 // 週一 = 0 … 週日 = 6
  date.setDate(date.getDate() - offset)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}
