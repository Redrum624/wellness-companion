import { useState, useEffect, useMemo } from 'react'
import { useNavigate } from 'react-router-dom'
import { subDays, format } from 'date-fns'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import CategoryCard from '../components/CategoryCard'
import CalendarHeatmap from '../components/CalendarHeatmap'
import Timeline from '../components/Timeline'
import { categories } from '../lib/categories'
import { computeSummary } from '../lib/summary'
import { greetingForHour, todayDisplayString, todayDateString, currentStreak } from '../lib/date-utils'
import { useDateNav } from '../hooks/useDateNav'
import { useDatabase } from '../hooks/useDatabase'
import { formatDate } from '../lib/date-utils'
import type { Entry, DateCount } from '../types/entry'
import type { CategoryKey } from '../styles/theme'
import { DashboardBackground } from '../components/PastelBackground'

export default function Dashboard() {
  const navigate = useNavigate()
  const db = useDatabase()
  const { date, isToday, goToday, goPrev, goNext, goTo } = useDateNav()

  const [todayEntries, setTodayEntries] = useState<Entry[]>([])
  const [heatmapData, setHeatmapData] = useState<Map<string, number>>(new Map())
  const [streaks, setStreaks] = useState<Record<string, number>>({})
  const [filterCategory, setFilterCategory] = useState('')

  // Load today's entries
  useEffect(() => {
    db.getEntriesByDate(date).then(setTodayEntries)
  }, [date])

  // Load heatmap data (last 365 days)
  useEffect(() => {
    const start = format(subDays(new Date(), 364), 'yyyy-MM-dd')
    const end = todayDateString()
    db.getAllDatesWithCounts(start, end).then((counts: DateCount[]) => {
      const map = new Map<string, number>()
      counts.forEach(c => map.set(c.date, c.count))
      setHeatmapData(map)
    })
  }, [todayEntries.length])

  // Load streaks
  useEffect(() => {
    Promise.all(
      categories.map(async cat => {
        const dates = await db.getLoggedDates(cat.key)
        return [cat.key, currentStreak(dates)] as const
      })
    ).then(results => {
      setStreaks(Object.fromEntries(results))
    })
  }, [todayEntries.length])

  // Group entries by category for summaries
  const summaries = useMemo(() => {
    const grouped: Record<string, Entry[]> = {}
    todayEntries.forEach(e => {
      if (!grouped[e.category]) grouped[e.category] = []
      grouped[e.category].push(e)
    })
    const result: Record<string, string> = {}
    categories.forEach(cat => {
      result[cat.key] = computeSummary(cat.key, grouped[cat.key] || [])
    })
    return result
  }, [todayEntries])

  return (
    <div style={{ position: 'relative', minHeight: '100%' }}>
      <DashboardBackground />
    <div style={{ position: 'relative', padding: 24, maxWidth: 900, margin: '0 auto' }}>
      {/* Header */}
      <h1 style={{ fontWeight: 600, color: '#3D3262', fontSize: 24, marginBottom: 2 }}>
        {greetingForHour()} 🌸
      </h1>
      <p style={{ color: '#3D326260', fontSize: 13, marginBottom: 16 }}>
        {todayDisplayString()}
      </p>

      {/* Category cards grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: 10,
        marginBottom: 24
      }}>
        {categories.map(cat => (
          <CategoryCard
            key={cat.key}
            categoryKey={cat.key}
            icon={cat.icon}
            name={cat.name}
            summary={summaries[cat.key] || 'No data'}
            streak={streaks[cat.key] || 0}
            onClick={() => navigate(cat.path)}
          />
        ))}
      </div>

      {/* Calendar heatmap */}
      <div style={{
        background: 'rgba(255,255,255,0.25)',
        borderRadius: 14, padding: 16, marginBottom: 20
      }}>
        <div style={{ fontSize: 12, color: '#3D326280', marginBottom: 8 }}>Activity (last 52 weeks)</div>
        <CalendarHeatmap dateCounts={heatmapData} onDateClick={goTo} />
      </div>

      {/* Timeline */}
      <div style={{
        background: 'rgba(255,255,255,0.25)',
        borderRadius: 14, padding: 16
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
          <button onClick={goPrev} style={navBtnStyle}><ChevronLeft size={18} strokeWidth={2} style={{ verticalAlign: 'text-bottom' }} /></button>
          <span style={{ fontSize: 13, color: '#3D3262', fontWeight: 500, flex: 1, textAlign: 'center' }}>
            {isToday ? 'Today' : formatDate(date)}
          </span>
          <button onClick={goNext} style={navBtnStyle}><ChevronRight size={18} strokeWidth={2} style={{ verticalAlign: 'text-bottom' }} /></button>
          {!isToday && <button onClick={goToday} style={navBtnStyle}>Today</button>}
        </div>
        <Timeline
          entries={todayEntries}
          filterCategory={filterCategory}
          onFilterChange={setFilterCategory}
        />
      </div>
    </div>
    </div>
  )
}

const navBtnStyle: React.CSSProperties = {
  border: 'none', borderRadius: 8, padding: '4px 10px',
  background: 'rgba(255,255,255,0.4)', color: '#3D3262',
  fontSize: 13, cursor: 'pointer', fontFamily: 'inherit', fontWeight: 500
}
