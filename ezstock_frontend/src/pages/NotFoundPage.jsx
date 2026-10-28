import { Link } from 'react-router'

function NotFoundPage() {
  return (
    <section className="card">
      <h2>找不到這個頁面</h2>
      <p className="muted">網址可能打錯了。</p>
      <Link to="/">回首頁</Link>
    </section>
  )
}

export default NotFoundPage
