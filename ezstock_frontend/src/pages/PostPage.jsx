import { useEffect, useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { addComment, deleteComment, deletePost, getPost, likePost, unlikePost } from '../api/forumApi'
import PostStats, { StatIcon } from '../components/PostStats'
import StockSparkline from '../components/StockSparkline'
import StockTagChip from '../components/StockTagChip'
import { useAuth } from '../hooks/useAuth'
import { formatDateTime } from '../utils/format'

const MAX_COMMENT = 1000

/**
 * 文章內頁：全文、留言串、留言框。作者看得到「修改」「刪除」。
 * 內文用 white-space: pre-wrap 保留換行，不用 dangerouslySetInnerHTML，所以不會有 XSS。
 */
function PostPage() {
  const { postId } = useParams()
  const { isAuthenticated, member } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [state, setState] = useState({ status: 'loading', post: null, error: null })
  const [comment, setComment] = useState('')
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState(null)

  // 登入狀態改變（例如剛登入）時重新載入，「能不能修改、刪除」才會正確
  useEffect(() => {
    let ignore = false
    getPost(postId)
      .then((post) => {
        if (!ignore) setState({ status: 'done', post, error: null })
      })
      .catch((error) => {
        if (!ignore) setState({ status: 'error', post: null, error })
      })
    return () => {
      ignore = true
    }
  }, [postId, member?.id])

  async function run(action, label) {
    setBusy(true)
    setActionError(null)
    try {
      const result = await action()
      console.info(`[論壇] ${label}`)
      return result ?? true
    } catch (error) {
      console.warn(`[論壇] ${label}失敗：${error.message}`)
      setActionError(error)
      return null
    } finally {
      setBusy(false)
    }
  }

  async function handleComment(event) {
    event.preventDefault()
    const post = await run(() => addComment(postId, comment.trim()), `在文章 ${postId} 留言`)
    if (post) {
      setState({ status: 'done', post, error: null })
      setComment('')
    }
  }

  async function handleDeleteComment(commentId) {
    if (!window.confirm('確定刪除這則留言？')) return
    const post = await run(() => deleteComment(commentId), `刪除留言 ${commentId}`)
    if (post) setState({ status: 'done', post, error: null })
  }

  /**
   * 按讚：先馬上改畫面（樂觀更新），按起來不卡；API 回來後用伺服器的數字校正，失敗就改回原本的樣子。
   * 沒登入的話帶去登入頁，登入完回到這篇。
   */
  async function handleLike() {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location.pathname } })
      return
    }
    const before = state.post
    const liking = !before.liked
    setState({ ...state, post: { ...before, liked: liking, likeCount: before.likeCount + (liking ? 1 : -1) } })
    try {
      const result = liking ? await likePost(postId) : await unlikePost(postId)
      console.info(`[論壇] ${liking ? '按讚' : '收回讚'} 文章 ${postId}，讚數 ${result.likeCount}`)
      setState((current) => ({ ...current, post: { ...current.post, liked: result.liked, likeCount: result.likeCount } }))
    } catch (error) {
      console.warn(`[論壇] ${liking ? '按讚' : '收回讚'}失敗：${error.message}`)
      setState((current) => ({ ...current, post: before }))
      setActionError(error)
    }
  }

  async function handleDeletePost() {
    if (!window.confirm('確定刪除這篇文章？刪除後就看不到了。')) return
    if (await run(() => deletePost(postId), `刪除文章 ${postId}`)) {
      navigate('/forum', { replace: true })
    }
  }

  if (state.status === 'loading') return <p className="muted">載入文章…</p>
  if (state.status === 'error') {
    return (
      <section className="card">
        <p className="error">{state.error.message}</p>
        <Link to="/forum">回論壇</Link>
      </section>
    )
  }

  const { post } = state
  return (
    <>
      <article className="card">
        <p>
          <Link to="/forum" className="muted">
            ← 回論壇
          </Link>
        </p>
        <h2 className="post-detail-title">{post.title}</h2>
        <div className="post-meta">
          <span>{post.author.displayName}</span>
          <span className="muted">{formatDateTime(post.createdAt)}</span>
          {post.edited && <span className="muted">（已編輯 {formatDateTime(post.updatedAt)}）</span>}
          {post.stocks.map((stock) => (
            <StockTagChip key={stock.stockId} stock={stock} />
          ))}
        </div>
        <div className="post-content">{post.content}</div>
        <div className="post-reactions">
          <button
            type="button"
            className={`like-button ${post.liked ? 'liked' : ''}`}
            aria-pressed={post.liked}
            onClick={handleLike}
          >
            <StatIcon name="like" />
            {post.liked ? '已按讚' : '讚'} {post.likeCount}
          </button>
          <PostStats post={post} showLikes={false} />
        </div>
        {post.stocks.length > 0 && (
          <div className="sparkline-grid">
            {post.stocks.map((stock) => (
              <StockSparkline key={stock.stockId} stock={stock} />
            ))}
          </div>
        )}
        {post.editable && (
          <div className="post-actions">
            <Link to={`/forum/${post.id}/edit`} className="link-button">
              修改
            </Link>
            <button type="button" className="link-button danger" onClick={handleDeletePost} disabled={busy}>
              刪除
            </button>
          </div>
        )}
      </article>

      <section className="card compare-result">
        <h3 className="section-title">留言（{post.commentCount}）</h3>
        {actionError && (
          <p className="error" role="alert">
            {actionError.message}
          </p>
        )}
        {post.comments.length === 0 && <p className="muted">還沒有留言。</p>}
        <ul className="comment-list">
          {post.comments.map((c) => (
            <li key={c.id} className="comment-item">
              {c.deleted ? (
                <span className="muted">此留言已刪除</span>
              ) : (
                <>
                  <div className="post-meta">
                    <strong>{c.author.displayName}</strong>
                    <span className="muted">{formatDateTime(c.createdAt)}</span>
                    {c.deletable && (
                      <button
                        type="button"
                        className="icon-button"
                        onClick={() => handleDeleteComment(c.id)}
                        disabled={busy}
                        aria-label="刪除這則留言"
                      >
                        ✕
                      </button>
                    )}
                  </div>
                  <div className="comment-content">{c.content}</div>
                </>
              )}
            </li>
          ))}
        </ul>

        {isAuthenticated ? (
          <form onSubmit={handleComment} className="comment-form">
            <textarea
              className="form-input comment-input"
              value={comment}
              onChange={(event) => setComment(event.target.value)}
              maxLength={MAX_COMMENT}
              rows={3}
              placeholder="寫下你的看法…"
              aria-label="留言"
              required
            />
            <div className="comment-form-footer">
              <span className="muted form-hint">
                {comment.length} / {MAX_COMMENT}
              </span>
              <button type="submit" className="primary-button" disabled={busy || !comment.trim()}>
                {busy ? '送出中…' : '留言'}
              </button>
            </div>
          </form>
        ) : (
          <p className="muted">
            <Link to="/login" state={{ from: location.pathname }}>
              登入
            </Link>
            後就能留言。
          </p>
        )}
      </section>
    </>
  )
}

export default PostPage
