import { Link } from 'react-router'

function Header() {
  return (
    <header className="header">
      <div className="header-inner">
        <Link to="/" className="logo">
          EZ<span>Stock</span>
        </Link>
        <span className="tagline">台股查詢與回測</span>
      </div>
    </header>
  )
}

export default Header
