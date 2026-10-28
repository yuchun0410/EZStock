import { useSyncExternalStore } from 'react'

/**
 * 自選股，先存在瀏覽器的 localStorage（之後做會員時再搬到資料庫）。
 *
 * 用 useSyncExternalStore 讓所有用到自選股的元件共用同一份資料：
 * 在個股頁按星號，回到首頁清單就會更新；開兩個分頁時，另一個分頁也會跟著更新（storage 事件）。
 *
 * localStorage 在無痕模式、或使用者封鎖網站資料時可能讀不到或寫不進去，
 * 所以每次存取都包 try/catch，失敗就當成空清單，不讓整個頁面壞掉。
 */

const STORAGE_KEY = 'ezstock.watchlist'
const MAX_ITEMS = 50

const listeners = new Set()
let cache = read()

function read() {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    const list = raw ? JSON.parse(raw) : []
    return Array.isArray(list) ? list : []
  } catch (error) {
    console.warn('[自選股] 讀取失敗，當作空清單', error)
    return []
  }
}

function write(list) {
  cache = list
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(list))
  } catch (error) {
    console.warn('[自選股] 寫入失敗，這次的變更只在目前分頁有效', error)
  }
  listeners.forEach((listener) => listener())
}

function subscribe(listener) {
  listeners.add(listener)
  // 其他分頁改了 localStorage，瀏覽器會在這個分頁觸發 storage 事件
  const onStorage = (event) => {
    if (event.key === STORAGE_KEY) {
      cache = read()
      listener()
    }
  }
  window.addEventListener('storage', onStorage)
  return () => {
    listeners.delete(listener)
    window.removeEventListener('storage', onStorage)
  }
}

function getSnapshot() {
  return cache
}

export function useWatchlist() {
  const items = useSyncExternalStore(subscribe, getSnapshot)

  function has(stockId) {
    return items.some((item) => item.stockId === stockId)
  }

  function add(stock) {
    if (has(stock.stockId)) return
    if (items.length >= MAX_ITEMS) {
      console.warn(`[自選股] 已達上限 ${MAX_ITEMS} 檔，無法加入 ${stock.stockId}`)
      return false
    }
    write([...items, { stockId: stock.stockId, name: stock.name, addedAt: new Date().toISOString() }])
    console.info(`[自選股] 加入 ${stock.stockId} ${stock.name}，共 ${items.length + 1} 檔`)
    return true
  }

  function remove(stockId) {
    if (!has(stockId)) return
    write(items.filter((item) => item.stockId !== stockId))
    console.info(`[自選股] 移除 ${stockId}，共 ${items.length - 1} 檔`)
  }

  function toggle(stock) {
    if (has(stock.stockId)) {
      remove(stock.stockId)
    } else {
      add(stock)
    }
  }

  return { items, has, add, remove, toggle, isFull: items.length >= MAX_ITEMS }
}
