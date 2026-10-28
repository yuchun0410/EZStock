/** 走勢圖共用的設定。放在獨立檔案，元件檔只匯出元件（React Fast Refresh 的要求）。 */

// 價格圖和成交量圖用同一個 syncId，滑鼠游標會同步
export const CHART_SYNC_ID = 'price'

// 兩張圖的 Y 軸寬度要一樣，上下的柱子才會對齊
export const Y_AXIS_WIDTH = 64

// SVG 的屬性不一定吃 CSS 變數，圖表直接用色碼（跟 index.css 的變數同色）
export const CHART_COLORS = {
  up: '#d63a3a',
  down: '#1f9d55',
  neutral: '#1f3a5f',
  flat: '#6b7385',
  grid: '#e1e4ea',
  axis: '#6b7385',
  surface: '#ffffff',
}

/** 單根 K 棒的顏色：收盤 > 開盤紅、< 開盤綠、平盤灰 */
export function candleColor(point) {
  if (point.close > point.open) return CHART_COLORS.up
  if (point.close < point.open) return CHART_COLORS.down
  return CHART_COLORS.flat
}

// 均線：顏色用過配色檢查（色盲模擬下也分得開），且避開代表漲跌的紅綠
export const MOVING_AVERAGES = [
  { period: 5, label: 'MA5', color: '#b87800' },
  { period: 20, label: 'MA20', color: '#2563eb' },
  { period: 60, label: 'MA60', color: '#db2777' },
]
