import { Route, Routes } from 'react-router'
import Header from './components/Header'
import AssetPage from './pages/AssetPage'
import ForumPage from './pages/ForumPage'
import ComparePage from './pages/ComparePage'
import HomePage from './pages/HomePage'
import StockPage from './pages/StockPage'
import StrategyPage from './pages/StrategyPage'
import LineCallbackPage from './pages/LineCallbackPage'
import LoginPage from './pages/LoginPage'
import NotFoundPage from './pages/NotFoundPage'
import PostEditorPage from './pages/PostEditorPage'
import PostPage from './pages/PostPage'
import RegisterPage from './pages/RegisterPage'
import ScreenerPage from './pages/ScreenerPage'
import './App.css'

function App() {
  return (
    <div className="app-shell">
      <Header />
      <main className="main">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/stocks/:stockId" element={<StockPage />} />
          <Route path="/backtest/compare" element={<ComparePage />} />
          <Route path="/backtest/strategy" element={<StrategyPage />} />
          <Route path="/screener" element={<ScreenerPage />} />
          <Route path="/forum" element={<ForumPage />} />
          <Route path="/forum/new" element={<PostEditorPage />} />
          <Route path="/forum/:postId" element={<PostPage />} />
          <Route path="/forum/:postId/edit" element={<PostEditorPage />} />
          <Route path="/assets" element={<AssetPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/login/line/callback" element={<LineCallbackPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
    </div>
  )
}

export default App
