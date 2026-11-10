import { Link, NavLink } from 'react-router'
import AccountMenu from './AccountMenu'

/*
 * 上方功能列（桌機）／頂欄 + 底部分頁列（手機）。
 * 同一份選單：寬螢幕橫排在上方（只顯示文字），窄螢幕用 CSS 改成固定在畫面底部（圖示 + 短名稱），大拇指按得到。
 * 圖示是內嵌的 SVG，用 currentColor，跟著文字顏色變（選中時變品牌色）。
 */
const NAV_ITEMS = [
  { to: '/', label: '查詢', short: '查詢', end: true, icon: 'search' },
  { to: '/backtest/compare', label: '標的比較', short: '比較', icon: 'compare' },
  { to: '/backtest/strategy', label: '條件回測', short: '回測', icon: 'strategy' },
  { to: '/screener', label: '條件選股', short: '選股', icon: 'filter' },
  { to: '/forum', label: '論壇', short: '論壇', icon: 'forum' },
  { to: '/assets', label: '我的資產', short: '資產', icon: 'wallet' },
]

const ICON_PATHS = {
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm9 16-4.2-4.2',
  compare: 'M4 19V9m5.3 10V5m5.4 14v-7M20 19V8',
  strategy: 'M4 17l5-5 4 3 7-8M15 7h5v5',
  filter: 'M4 5h16l-6.2 7.4V19l-3.6-1.8v-4.8L4 5Z',
  forum: 'M5 5h14v10H10l-5 4V5Z',
  wallet: 'M4 7.5h16v11H4zM4 7.5 16 4v3.5M16 13h1.5',
}

function NavIcon({ name }) {
  return (
    <svg className="nav-icon" viewBox="0 0 24 24" aria-hidden="true">
      <path d={ICON_PATHS[name]} />
    </svg>
  )
}

function Header() {
  return (
    <header className="topbar">
      <Link to="/" className="logo" aria-label="EZStock 首頁">
        <span className="logo-mark" aria-hidden="true">
          EZ
        </span>
        <span className="logo-text">EZStock</span>
      </Link>
      <nav className="nav" aria-label="主選單">
        {NAV_ITEMS.map((item) => (
          <NavLink key={item.to} to={item.to} end={item.end} className="nav-link" aria-label={item.label}>
            <NavIcon name={item.icon} />
            <span className="nav-label" aria-hidden="true">
              {item.label}
            </span>
            <span className="nav-label-short" aria-hidden="true">
              {item.short}
            </span>
          </NavLink>
        ))}
      </nav>
      <p className="topbar-note">股價每個交易日收盤後更新</p>
      <AccountMenu />
    </header>
  )
}

export default Header
