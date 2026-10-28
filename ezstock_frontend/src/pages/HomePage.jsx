import { Link } from 'react-router'

/** 首頁：下一步會換成搜尋框，現在先放幾個連結測試路由 */
function HomePage() {
  return (
    <section className="card">
      <h2>熱門個股</h2>
      <ul>
        <li><Link to="/stocks/2330">2330 台積電</Link></li>
        <li><Link to="/stocks/2308">2308 台達電</Link></li>
        <li><Link to="/stocks/0050">0050 元大台灣50</Link></li>
        <li><Link to="/stocks/9999">9999（不存在，測試錯誤畫面）</Link></li>
      </ul>
    </section>
  )
}

export default HomePage
