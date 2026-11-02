import { useState } from 'react'
import { formatMoney } from '../utils/format'

const KIND_LABEL = { CASH: '現金', LIABILITY: '負債' }

/**
 * 現金或負債帳戶清單：可以新增、修改名稱和金額、刪除。
 * onCreate / onUpdate / onDelete 由頁面提供，成功時回傳 true（表單就清空或關閉）。
 */
function AccountList({ kind, accounts, busy, onCreate, onUpdate, onDelete }) {
  const label = KIND_LABEL[kind]
  const [draft, setDraft] = useState({ name: '', balance: '' })
  const [editing, setEditing] = useState(null)
  const total = accounts.reduce((sum, account) => sum + Number(account.balance), 0)

  async function handleCreate(event) {
    event.preventDefault()
    const ok = await onCreate({ kind, name: draft.name.trim(), balance: Number(draft.balance) })
    if (ok) setDraft({ name: '', balance: '' })
  }

  async function handleUpdate(event) {
    event.preventDefault()
    const ok = await onUpdate(editing.id, { name: editing.name.trim(), balance: Number(editing.balance) })
    if (ok) setEditing(null)
  }

  return (
    <section className="asset-section">
      <h3 className="section-title">
        {label}
        <span className="muted asset-section-total">合計 {formatMoney(total)} 元</span>
      </h3>

      {accounts.length === 0 && <p className="muted">還沒有{label}帳戶。</p>}
      <ul className="account-list">
        {accounts.map((account) =>
          editing?.id === account.id ? (
            <li key={account.id}>
              <form className="account-row" onSubmit={handleUpdate}>
                <input
                  className="form-input"
                  value={editing.name}
                  onChange={(event) => setEditing({ ...editing, name: event.target.value })}
                  maxLength={50}
                  aria-label="名稱"
                  required
                />
                <input
                  type="number"
                  className="form-input form-input-small"
                  value={editing.balance}
                  onChange={(event) => setEditing({ ...editing, balance: event.target.value })}
                  min={0}
                  step="0.01"
                  aria-label="金額"
                  required
                />
                <button type="submit" className="link-button" disabled={busy}>
                  儲存
                </button>
                <button type="button" className="link-button" onClick={() => setEditing(null)}>
                  取消
                </button>
              </form>
            </li>
          ) : (
            <li key={account.id} className="account-row">
              <span className="money-account-name">{account.name}</span>
              <span className="money-account-balance">{formatMoney(account.balance)} 元</span>
              <button
                type="button"
                className="link-button"
                onClick={() => setEditing({ id: account.id, name: account.name, balance: String(account.balance) })}
              >
                修改
              </button>
              <button
                type="button"
                className="icon-button"
                onClick={() => onDelete(account)}
                disabled={busy}
                aria-label={`刪除${label} ${account.name}`}
              >
                ✕
              </button>
            </li>
          ),
        )}
      </ul>

      <form className="account-row account-add" onSubmit={handleCreate}>
        <input
          className="form-input"
          placeholder={kind === 'CASH' ? '例如：台新活存' : '例如：信貸、房貸'}
          value={draft.name}
          onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          maxLength={50}
          aria-label={`新${label}名稱`}
          required
        />
        <input
          type="number"
          className="form-input form-input-small"
          placeholder="金額"
          value={draft.balance}
          onChange={(event) => setDraft({ ...draft, balance: event.target.value })}
          min={0}
          step="0.01"
          aria-label={`新${label}金額`}
          required
        />
        <button type="submit" className="link-button" disabled={busy}>
          + 新增{label}
        </button>
      </form>
    </section>
  )
}

export default AccountList
