import { Link, NavLink } from 'react-router'

function Header() {
  return (
    <header className="header">
      <div className="header-inner">
        <Link to="/" className="logo">
          EZ<span>Stock</span>
        </Link>
        <span className="tagline">台股查詢與回測</span>
        <nav className="nav" aria-label="主選單">
          <NavLink to="/" end className="nav-link">
            查詢
          </NavLink>
          <NavLink to="/backtest/compare" className="nav-link">
            標的比較
          </NavLink>
          <NavLink to="/backtest/strategy" className="nav-link">
            條件回測
          </NavLink>
          <NavLink to="/screener" className="nav-link">
            條件選股
          </NavLink>
        </nav>
      </div>
    </header>
  )
}

export default Header
