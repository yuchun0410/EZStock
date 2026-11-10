/** 走勢圖共用的設定。放在獨立檔案，元件檔只匯出元件（React Fast Refresh 的要求）。 */

// 價格圖和成交量圖用同一個 syncId，滑鼠游標會同步
export const CHART_SYNC_ID = 'price'

// 兩張圖的 Y 軸寬度要一樣，上下的柱子才會對齊
export const Y_AXIS_WIDTH = 64

// SVG 的屬性不一定吃 CSS 變數，圖表直接用色碼（跟 index.css 的變數同色）
export const CHART_COLORS = {
  up: '#d1453b',
  down: '#2f8a5b',
  neutral: '#9a6a44',
  flat: '#8a7867',
  grid: '#ece2d5',
  axis: '#8a7867',
  surface: '#fffcf8',
}

/** 單根 K 棒的顏色：收盤 > 開盤紅、< 開盤綠、平盤灰 */
export function candleColor(point) {
  if (point.close > point.open) return CHART_COLORS.up
  if (point.close < point.open) return CHART_COLORS.down
  return CHART_COLORS.flat
}

// 均線：在奶茶色底上要看得清楚；避開代表漲跌的紅綠，也避開品牌棕
export const MOVING_AVERAGES = [
  { period: 5, label: 'MA5', color: '#c28a12' },
  { period: 20, label: 'MA20', color: '#3f6fb5' },
  { period: 60, label: 'MA60', color: '#a052b8' },
]

// 標的比較：最多 5 檔，依「使用者選的順序」固定配色（顏色跟著標的走，不跟著排名走）。
// 這組顏色用過配色檢查：明度、彩度、色盲模擬下相鄰兩色都分得開。
export const SERIES_COLORS = ['#3f6fb5', '#9a6a44', '#a052b8', '#c28a12', '#d0608f']
