import { Link } from 'react-router'
import { formatDateTime } from '../utils/format'
import PostStats from './PostStats'
import StockTagChip from './StockTagChip'

/**
 * 文章列表（論壇頁、個股頁共用）。
 * 標題、摘要都是一般文字，React 會自動跳脫，就算有人在文章裡寫 <script> 也只會被當成文字顯示。
 *
 * @param onStockClick 點股票標籤時要做什麼（論壇頁：篩選這檔）；沒給就連到個股頁
 */
function PostList({ posts, onStockClick }) {
  if (posts.length === 0) {
    return <p className="muted">還沒有文章。</p>
  }

  return (
    <ul className="post-list">
      {posts.map((post) => (
        <li key={post.id} className="post-item">
          <Link to={`/forum/${post.id}`} className="post-title">
            {post.title}
          </Link>
          {post.excerpt && <p className="post-excerpt muted">{post.excerpt}</p>}
          <div className="post-meta">
            <span>{post.author.displayName}</span>
            <span className="muted">{formatDateTime(post.createdAt)}</span>
            {post.edited && <span className="muted">（已編輯）</span>}
            <PostStats post={post} />
            {post.stocks.map((stock) => (
              <StockTagChip key={stock.stockId} stock={stock} onClick={onStockClick} />
            ))}
          </div>
        </li>
      ))}
    </ul>
  )
}

export default PostList
