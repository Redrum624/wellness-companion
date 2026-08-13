import { Routes, Route, useLocation } from 'react-router-dom'
import Sidebar from './components/Sidebar'
import Dashboard from './pages/Dashboard'
import WaterPage from './pages/WaterPage'
import FoodPage from './pages/FoodPage'
import BathroomPage from './pages/BathroomPage'
import HealthPage from './pages/HealthPage'
import SleepPage from './pages/SleepPage'
import EmotionsPage from './pages/EmotionsPage'
import InteractionsPage from './pages/InteractionsPage'
import ChoresPage from './pages/ChoresPage'
import HobbiesPage from './pages/HobbiesPage'
import IdeasPage from './pages/IdeasPage'
import CyclePage from './pages/CyclePage'
import BadHabitsPage from './pages/BadHabitsPage'
import InsightsPage from './pages/InsightsPage'

export default function App() {
  const { pathname } = useLocation()
  return (
    <div style={{ display: 'flex', height: '100vh', overflow: 'hidden' }}>
      <Sidebar />
      <main style={{ flex: 1, overflow: 'auto', background: 'linear-gradient(180deg, #F5DFE3 0%, #E8DDF2 50%, #DCE8DA 100%)' }}>
        <div key={pathname} className="page-enter">
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/water" element={<WaterPage />} />
            <Route path="/food" element={<FoodPage />} />
            <Route path="/bathroom" element={<BathroomPage />} />
            <Route path="/health" element={<HealthPage />} />
            <Route path="/sleep" element={<SleepPage />} />
            <Route path="/emotions" element={<EmotionsPage />} />
            <Route path="/interactions" element={<InteractionsPage />} />
            <Route path="/chores" element={<ChoresPage />} />
            <Route path="/hobbies" element={<HobbiesPage />} />
            <Route path="/ideas" element={<IdeasPage />} />
            <Route path="/cycle" element={<CyclePage />} />
            <Route path="/badhabits" element={<BadHabitsPage />} />
            <Route path="/insights" element={<InsightsPage />} />
          </Routes>
        </div>
      </main>
    </div>
  )
}
