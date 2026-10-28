import { useEffect, useState } from 'react'
import { searchStocks } from '../api/stockApi'

// 停止打字多久才送出搜尋（毫秒）。每打一個字就打 API 太浪費。
const SEARCH_DELAY_MS = 300

/**
 * 依關鍵字搜尋股票，首頁和標的比較頁共用。
 * - 防抖（debounce）：關鍵字停止變化 300ms 才真的打 API
 * - 競態：舊的請求比較晚回來時，不會蓋掉新的結果
 *
 * @param keyword 已經 trim 過的關鍵字，空字串代表不搜尋
 * @returns stocks 結果、error 錯誤、loading 搜尋中、isCurrent 結果是不是屬於目前的關鍵字
 */
export function useStockSearch(keyword) {
  const [result, setResult] = useState({ keyword: '', stocks: [], error: null })

  useEffect(() => {
    if (!keyword) return
    let ignore = false
    const timer = setTimeout(() => {
      searchStocks(keyword)
        .then((stocks) => {
          if (!ignore) setResult({ keyword, stocks, error: null })
        })
        .catch((error) => {
          if (!ignore) setResult({ keyword, stocks: [], error })
        })
    }, SEARCH_DELAY_MS)
    return () => {
      ignore = true
      clearTimeout(timer)
    }
  }, [keyword])

  const isCurrent = keyword !== '' && result.keyword === keyword
  return {
    stocks: isCurrent ? result.stocks : [],
    error: isCurrent ? result.error : null,
    loading: keyword !== '' && !isCurrent,
    isCurrent,
  }
}
