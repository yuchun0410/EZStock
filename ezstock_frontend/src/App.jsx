import { Route, Routes } from 'react-router'
import Header from './components/Header'
import ComparePage from './pages/ComparePage'
import HomePage from './pages/HomePage'
import StockPage from './pages/StockPage'
import StrategyPage from './pages/StrategyPage'
import NotFoundPage from './pages/NotFoundPage'
import './App.css'

function App() {
  return (
    <>
      <Header />
      <main className="container">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/stocks/:stockId" element={<StockPage />} />
          <Route path="/backtest/compare" element={<ComparePage />} />
          <Route path="/backtest/strategy" element={<StrategyPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </main>
    </>
  )
}

export default App
