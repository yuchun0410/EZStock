import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    // 開發時前端跑在 5173、後端跑在 8080。
    // 把 /api 開頭的請求轉給後端，瀏覽器看起來是同一個網站，就不會有 CORS 問題。
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
