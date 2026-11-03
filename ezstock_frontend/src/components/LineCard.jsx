import { useEffect, useState } from 'react'
import { createBindingCode, getLineStatus, unlinkLine } from '../api/lineApi'

const POLL_MS = 5000

/**
 * 「我的資產」頁下面的 LINE 通知卡片：綁定 LINE 後，每天收盤後收到淨資產回報。
 * 產生綁定碼後每 5 秒問一次後端「綁好了沒」，在 LINE 傳完綁定碼，這裡會自己變成「已綁定」。
 */
function LineCard() {
  const [status, setStatus] = useState(null)
  const [binding, setBinding] = useState(null)
  const [error, setError] = useState(null)
  const [now, setNow] = useState(() => Date.now())

  useEffect(() => {
    let ignore = false
    getLineStatus()
      .then((result) => {
        if (!ignore) setStatus(result)
      })
      .catch((e) => {
        if (!ignore) setError(e)
      })
    return () => {
      ignore = true
    }
  }, [])

  // 有綁定碼時：每秒更新倒數，每 5 秒確認一次是否已綁定
  useEffect(() => {
    if (!binding) return undefined
    const tick = setInterval(() => setNow(Date.now()), 1000)
    const poll = setInterval(() => {
      getLineStatus()
        .then((result) => {
          if (result.linked) {
            console.info('[LINE] 綁定完成')
            setStatus(result)
            setBinding(null)
          }
        })
        .catch(() => {})
    }, POLL_MS)
    return () => {
      clearInterval(tick)
      clearInterval(poll)
    }
  }, [binding])

  async function handleCreate() {
    setError(null)
    try {
      const result = await createBindingCode()
      console.info(`[LINE] 產生綁定碼，${result.expiresInSeconds} 秒內有效`)
      setBinding({ code: result.code, expiresAt: Date.now() + result.expiresInSeconds * 1000 })
      setNow(Date.now())
    } catch (e) {
      setError(e)
    }
  }

  async function handleUnlink() {
    if (!window.confirm('確定解除 LINE 綁定？之後不會再收到每日回報。')) return
    try {
      await unlinkLine()
      console.info('[LINE] 已解除綁定')
      setStatus({ ...status, linked: false })
    } catch (e) {
      setError(e)
    }
  }

  if (!status || !status.available) {
    return null
  }

  const remaining = binding ? Math.max(0, Math.round((binding.expiresAt - now) / 1000)) : 0
  const expired = binding && remaining === 0

  return (
    <section className="card compare-result">
      <h3 className="section-title">LINE 每日回報</h3>
      {error && <p className="error">{error.message}</p>}

      {status.linked ? (
        <>
          <p>✅ 已綁定 LINE。每天收盤更新股價後，會把淨資產明細傳到你的 LINE。</p>
          <p className="muted">在 LINE 傳「資產」可以隨時查詢；傳「說明」看所有指令。</p>
          <button type="button" className="link-button danger" onClick={handleUnlink}>
            解除綁定
          </button>
        </>
      ) : (
        <ol className="line-steps">
          <li>
            加 EZStock 好友
            {status.botId && (
              <>
                ：
                <a href={`https://line.me/R/ti/p/${encodeURIComponent(status.botId)}`} target="_blank" rel="noreferrer">
                  {status.botId}
                </a>
              </>
            )}
          </li>
          <li>
            {binding && !expired ? (
              <>
                在 LINE 傳這段文字給 EZStock：
                <div className="line-code">綁定 {binding.code}</div>
                <span className="muted">
                  {Math.floor(remaining / 60)}:{String(remaining % 60).padStart(2, '0')} 後失效，綁定完成後這裡會自動更新
                </span>
              </>
            ) : (
              <>
                {expired && <span className="muted">綁定碼已過期。</span>}
                <button type="button" className="primary-button line-create" onClick={handleCreate}>
                  產生綁定碼
                </button>
              </>
            )}
          </li>
        </ol>
      )}
    </section>
  )
}

export default LineCard
