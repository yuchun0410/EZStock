import { useEffect, useSyncExternalStore } from 'react'
import { addToWatchlist, getWatchlist, importWatchlist, removeFromWatchlist } from '../api/watchlistApi'
import { useAuthState } from '../auth/authStore'

/**
 * 自選股（要登入才能用），存在資料庫、跟著帳號走，所有元件共用同一份（useSyncExternalStore）。
 *
 * 加入、移除用「樂觀更新」：畫面先改，再呼叫 API；API 失敗就改回來。按星號馬上有反應，不用等網路。
 *
 * 舊版的自選股存在瀏覽器的 localStorage。登入後如果發現還有舊資料，就匯入帳號一次，成功後清掉。
 */

const LEGACY_STORAGE_KEY = 'ezstock.watchlist'
export const MAX_ITEMS = 50

const listeners = new Set()
// status：idle 還沒載入、loading 載入中、ready 好了、error 載入失敗
let store = { memberId: null, items: [], status: 'idle' }

function update(patch) {
  store = { ...store, ...patch }
  listeners.forEach((listener) => listener())
}

function subscribe(listener) {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function getSnapshot() {
  return store
}

/** 舊版存在瀏覽器裡的自選股代號；讀不到（無痕模式、封鎖網站資料）就當作沒有 */
function readLegacyIds() {
  try {
    const list = JSON.parse(window.localStorage.getItem(LEGACY_STORAGE_KEY) ?? '[]')
    return Array.isArray(list) ? list.map((item) => item.stockId).filter(Boolean) : []
  } catch {
    return []
  }
}

function clearLegacy() {
  try {
    window.localStorage.removeItem(LEGACY_STORAGE_KEY)
  } catch {
    // 刪不掉也沒關係，下次登入再匯入一次，後端會略過已存在的
  }
}

/** 後端回的 { stockId, name, type, addedAt } 只留畫面用得到的欄位 */
function fromServer(list) {
  return list.map((item) => ({ stockId: item.stockId, name: item.name, addedAt: item.addedAt }))
}

/** 登入後載入一次；同一個會員不重複載入（React StrictMode 會讓 effect 跑兩次） */
async function load(memberId) {
  if (store.memberId === memberId && store.status !== 'idle') return
  update({ memberId, items: [], status: 'loading' })

  const legacyIds = readLegacyIds()
  try {
    let list
    if (legacyIds.length > 0) {
      list = await importWatchlist(legacyIds)
      clearLegacy()
      console.info(`[自選股] 已把瀏覽器裡的舊自選股 ${legacyIds.length} 檔匯入帳號，帳號共 ${list.length} 檔`)
    } else {
      list = await getWatchlist()
      console.info(`[自選股] 載入帳號自選股 ${list.length} 檔`)
    }
    if (store.memberId === memberId) {
      update({ items: fromServer(list), status: 'ready' })
    }
  } catch (error) {
    console.warn('[自選股] 載入失敗', error)
    if (store.memberId === memberId) {
      update({ status: 'error' })
    }
  }
}

/** 樂觀更新：先改畫面，API 成功就用後端回的清單，失敗就改回原本的 */
async function change(optimisticItems, call, label) {
  const previous = store.items
  update({ items: optimisticItems })
  try {
    const list = await call()
    update({ items: fromServer(list) })
    console.info(`[自選股] ${label}，共 ${list.length} 檔`)
  } catch (error) {
    console.warn(`[自選股] ${label}失敗，已復原：${error.message}`)
    update({ items: previous })
  }
}

export function useWatchlist() {
  const { status: authStatus, member } = useAuthState()
  const snapshot = useSyncExternalStore(subscribe, getSnapshot)
  const signedIn = authStatus === 'authenticated'
  const memberId = member?.id ?? null

  useEffect(() => {
    if (signedIn) {
      load(memberId)
    } else if (authStatus === 'anonymous' && store.memberId !== null) {
      // 登出：清掉畫面上的自選股
      update({ memberId: null, items: [], status: 'idle' })
    }
  }, [signedIn, memberId, authStatus])

  const items = signedIn ? snapshot.items : []

  function has(stockId) {
    return items.some((item) => item.stockId === stockId)
  }

  function add(stock) {
    if (!signedIn || has(stock.stockId)) return false
    if (items.length >= MAX_ITEMS) {
      console.warn(`[自選股] 已達上限 ${MAX_ITEMS} 檔，無法加入 ${stock.stockId}`)
      return false
    }
    const item = { stockId: stock.stockId, name: stock.name, addedAt: new Date().toISOString() }
    change([...items, item], () => addToWatchlist(stock.stockId), `加入 ${stock.stockId} ${stock.name}`)
    return true
  }

  function remove(stockId) {
    if (!signedIn || !has(stockId)) return
    change(
      items.filter((item) => item.stockId !== stockId),
      () => removeFromWatchlist(stockId),
      `移除 ${stockId}`,
    )
  }

  function toggle(stock) {
    if (has(stock.stockId)) {
      remove(stock.stockId)
    } else {
      add(stock)
    }
  }

  return {
    items,
    has,
    add,
    remove,
    toggle,
    isFull: items.length >= MAX_ITEMS,
    // 自選股要登入才能用；checking 代表還在確認登入狀態
    signedIn,
    authChecking: authStatus === 'checking',
    loading: signedIn && snapshot.status === 'loading',
    loadFailed: signedIn && snapshot.status === 'error',
  }
}
