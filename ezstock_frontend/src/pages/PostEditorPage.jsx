import { useEffect, useState } from 'react'
import { Link, Navigate, useLocation, useNavigate, useParams } from 'react-router'
import { createPost, getPost, updatePost } from '../api/forumApi'
import StockPicker from '../components/StockPicker'
import { useAuth } from '../hooks/useAuth'

const MAX_TITLE = 100
const MAX_CONTENT = 10000
const MAX_STOCKS = 5

/** 發文（/forum/new）與修改文章（/forum/:postId/edit）共用同一個表單 */
function PostEditorPage() {
  const { postId } = useParams()
  const editing = Boolean(postId)
  const { status, isAuthenticated } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const [form, setForm] = useState({
    title: '',
    content: '',
    // 從論壇的股票篩選或個股頁進來，先帶入那一檔
    stocks: location.state?.stocks ?? [],
  })
  const [state, setState] = useState({ status: editing ? 'loading' : 'ready', error: null })

  useEffect(() => {
    if (!editing || !isAuthenticated) return
    let ignore = false
    getPost(postId)
      .then((post) => {
        if (ignore) return
        if (!post.editable) {
          setState({ status: 'error', error: new Error('只有作者可以修改這篇文章') })
          return
        }
        setForm({
          title: post.title,
          content: post.content,
          stocks: post.stocks.map((s) => ({ stockId: s.stockId, name: s.name })),
        })
        setState({ status: 'ready', error: null })
      })
      .catch((error) => {
        if (!ignore) setState({ status: 'error', error })
      })
    return () => {
      ignore = true
    }
  }, [editing, postId, isAuthenticated])

  if (status === 'checking') return <p className="muted">確認登入狀態…</p>
  if (!isAuthenticated) return <Navigate to="/login" state={{ from: location.pathname }} replace />
  if (state.status === 'loading') return <p className="muted">載入文章…</p>

  const canSubmit = form.title.trim() && form.content.trim() && state.status !== 'saving'

  async function handleSubmit(event) {
    event.preventDefault()
    if (!canSubmit) return
    const payload = {
      title: form.title.trim(),
      content: form.content,
      stockIds: form.stocks.map((s) => s.stockId),
    }
    setState({ status: 'saving', error: null })
    try {
      const post = editing ? await updatePost(postId, payload) : await createPost(payload)
      console.info(`[論壇] ${editing ? '修改' : '發表'}文章 id=${post.id}`)
      navigate(`/forum/${post.id}`, { replace: true })
    } catch (error) {
      setState({ status: 'ready', error })
    }
  }

  return (
    <section className="card">
      <p>
        <Link to={editing ? `/forum/${postId}` : '/forum'} className="muted">
          ← {editing ? '回文章' : '回論壇'}
        </Link>
      </p>
      <h2 className="section-title">{editing ? '修改文章' : '發文'}</h2>

      {state.status === 'error' && !form.title ? (
        <p className="error">{state.error.message}</p>
      ) : (
        <form onSubmit={handleSubmit} className="compare-form">
          <label className="form-field">
            <span className="form-label">標題</span>
            <input
              className="form-input"
              value={form.title}
              onChange={(event) => setForm({ ...form, title: event.target.value })}
              maxLength={MAX_TITLE}
              required
              autoFocus
            />
          </label>
          <label className="form-field">
            <span className="form-label">內容</span>
            <textarea
              className="form-input post-input"
              value={form.content}
              onChange={(event) => setForm({ ...form, content: event.target.value })}
              maxLength={MAX_CONTENT}
              rows={12}
              required
            />
            <span className="muted form-hint">
              {form.content.length} / {MAX_CONTENT}
            </span>
          </label>
          <div className="form-field">
            <span className="form-label">標記股票（選填，最多 {MAX_STOCKS} 檔）</span>
            <StockPicker
              selected={form.stocks}
              onChange={(stocks) => setForm({ ...form, stocks })}
              max={MAX_STOCKS}
            />
          </div>

          {state.error && (
            <p className="error" role="alert">
              {state.error.message}
            </p>
          )}
          <button type="submit" className="primary-button" disabled={!canSubmit}>
            {state.status === 'saving' ? '送出中…' : editing ? '儲存修改' : '發表'}
          </button>
        </form>
      )}
    </section>
  )
}

export default PostEditorPage
