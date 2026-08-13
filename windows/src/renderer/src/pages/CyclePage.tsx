import { useState, useEffect, useMemo, useCallback } from 'react'
import { format, subDays, addDays, startOfMonth, endOfMonth, startOfWeek, addMonths, subMonths, differenceInCalendarDays, parseISO, isSameDay } from 'date-fns'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { Entry, CycleData } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.cycle

// ─── Cycle analytics ───────────────────────────────────────

interface CycleStats {
  avgCycleLength: number | null
  avgPeriodLength: number | null
  regularity: number | null          // 0-100
  cycles: { start: string; end: string; length: number }[]
  nextPeriod: string | null
  nextOvulation: string | null
}

function computeStats(flowDates: string[]): CycleStats {
  const empty: CycleStats = { avgCycleLength: null, avgPeriodLength: null, regularity: null, cycles: [], nextPeriod: null, nextOvulation: null }
  if (flowDates.length === 0) return empty

  // Sort ascending
  const sorted = [...flowDates].sort()

  // Group consecutive dates into period spans
  const periods: { start: string; end: string }[] = []
  let spanStart = sorted[0]
  let spanEnd = sorted[0]

  for (let i = 1; i < sorted.length; i++) {
    const prev = parseISO(spanEnd)
    const curr = parseISO(sorted[i])
    // Allow a 1-day gap within the same period
    if (differenceInCalendarDays(curr, prev) <= 2) {
      spanEnd = sorted[i]
    } else {
      periods.push({ start: spanStart, end: spanEnd })
      spanStart = sorted[i]
      spanEnd = sorted[i]
    }
  }
  periods.push({ start: spanStart, end: spanEnd })

  // Period lengths
  const periodLengths = periods.map(p => differenceInCalendarDays(parseISO(p.end), parseISO(p.start)) + 1)
  const avgPeriodLength = periodLengths.length > 0
    ? Math.round(periodLengths.reduce((a, b) => a + b, 0) / periodLengths.length)
    : null

  // Cycle lengths (start-to-start)
  const cycles: CycleStats['cycles'] = []
  for (let i = 1; i < periods.length; i++) {
    const len = differenceInCalendarDays(parseISO(periods[i].start), parseISO(periods[i - 1].start))
    if (len >= 15 && len <= 60) {
      cycles.push({ start: periods[i - 1].start, end: periods[i].start, length: len })
    }
  }

  const avgCycleLength = cycles.length > 0
    ? Math.round(cycles.reduce((a, c) => a + c.length, 0) / cycles.length)
    : null

  // Regularity score: based on std deviation of cycle lengths
  let regularity: number | null = null
  if (cycles.length >= 2 && avgCycleLength) {
    const variance = cycles.reduce((sum, c) => sum + Math.pow(c.length - avgCycleLength, 2), 0) / cycles.length
    const stdDev = Math.sqrt(variance)
    // Perfect (0 stddev) = 100, stddev of 7+ days = 0
    regularity = Math.max(0, Math.round(100 - (stdDev / 7) * 100))
  }

  // Predictions based on last period start + avg cycle length
  let nextPeriod: string | null = null
  let nextOvulation: string | null = null
  if (avgCycleLength && periods.length > 0) {
    const lastStart = parseISO(periods[periods.length - 1].start)
    nextPeriod = format(addDays(lastStart, avgCycleLength), 'yyyy-MM-dd')
    // Ovulation ~14 days before next period
    nextOvulation = format(addDays(lastStart, avgCycleLength - 14), 'yyyy-MM-dd')
  }

  return { avgCycleLength, avgPeriodLength, regularity, cycles, nextPeriod, nextOvulation }
}

// ─── Calendar component ────────────────────────────────────

interface CalendarProps {
  month: Date
  flowDates: Set<string>
  predictedPeriod: string | null
  predictedOvulation: string | null
  avgPeriodLength: number | null
  selectedDate: string
  onDateClick: (date: string) => void
}

function CycleCalendar({ month, flowDates, predictedPeriod, predictedOvulation, avgPeriodLength, selectedDate, onDateClick }: CalendarProps) {
  const monthStart = startOfMonth(month)
  const monthEnd = endOfMonth(month)
  const calStart = startOfWeek(monthStart, { weekStartsOn: 1 })

  // Build predicted period date set
  const predictedDates = useMemo(() => {
    const set = new Set<string>()
    if (predictedPeriod && avgPeriodLength) {
      for (let i = 0; i < avgPeriodLength; i++) {
        set.add(format(addDays(parseISO(predictedPeriod), i), 'yyyy-MM-dd'))
      }
    }
    return set
  }, [predictedPeriod, avgPeriodLength])

  const weeks: Date[][] = []
  let day = calStart
  while (day <= monthEnd || weeks.length < 6) {
    const week: Date[] = []
    for (let i = 0; i < 7; i++) {
      week.push(day)
      day = addDays(day, 1)
    }
    weeks.push(week)
    if (day > monthEnd && weeks.length >= 5) break
  }

  const today = format(new Date(), 'yyyy-MM-dd')

  return (
    <div>
      {/* Day headers */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 2, marginBottom: 4 }}>
        {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map(d => (
          <div key={d} style={{ fontSize: 10, color: `${colors.text}60`, textAlign: 'center', padding: 2 }}>{d}</div>
        ))}
      </div>
      {/* Weeks */}
      {weeks.map((week, wi) => (
        <div key={wi} style={{ display: 'grid', gridTemplateColumns: 'repeat(7, 1fr)', gap: 2 }}>
          {week.map(d => {
            const ds = format(d, 'yyyy-MM-dd')
            const inMonth = d.getMonth() === month.getMonth()
            const isFlow = flowDates.has(ds)
            const isPredicted = predictedDates.has(ds)
            const isOvulation = predictedOvulation === ds
            const isSelected = ds === selectedDate
            const isToday = ds === today

            let bg = 'rgba(255,255,255,0.2)'
            let textColor = inMonth ? colors.text : `${colors.text}30`
            if (isFlow) { bg = '#E84672'; textColor = '#fff' }
            else if (isOvulation) { bg = '#8B5CF6'; textColor = '#fff' }
            else if (isPredicted) { bg = '#E8467240'; textColor = colors.text }

            return (
              <div
                key={ds}
                onClick={() => onDateClick(ds)}
                style={{
                  textAlign: 'center', padding: '6px 0', borderRadius: 8,
                  fontSize: 12, cursor: 'pointer', background: bg, color: textColor,
                  fontWeight: isToday ? 700 : 400,
                  outline: isSelected ? `2px solid ${colors.text}` : 'none',
                  outlineOffset: -2
                }}
              >
                {d.getDate()}
              </div>
            )
          })}
        </div>
      ))}
      {/* Legend */}
      <div style={{ display: 'flex', gap: 14, marginTop: 10, flexWrap: 'wrap' }}>
        {[
          { color: '#E84672', label: 'Period' },
          { color: '#E8467240', label: 'Predicted' },
          { color: '#8B5CF6', label: 'Ovulation' }
        ].map(l => (
          <div key={l.label} style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 11, color: `${colors.text}90` }}>
            <div style={{ width: 10, height: 10, borderRadius: 3, background: l.color }} />
            {l.label}
          </div>
        ))}
      </div>
    </div>
  )
}

// ─── Stat card ─────────────────────────────────────────────

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div style={{
      flex: 1, minWidth: 100, padding: '10px 12px', borderRadius: 12,
      background: 'rgba(255,255,255,0.3)', textAlign: 'center'
    }}>
      <div style={{ fontSize: 20, fontWeight: 600, color: colors.text }}>{value}</div>
      <div style={{ fontSize: 11, color: `${colors.text}80` }}>{label}</div>
      {sub && <div style={{ fontSize: 10, color: `${colors.text}60`, marginTop: 2 }}>{sub}</div>}
    </div>
  )
}

// ─── Main page ─────────────────────────────────────────────

export default function CyclePage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'cycle')
  const [allFlowEntries, setAllFlowEntries] = useState<Entry[]>([])
  const [calMonth, setCalMonth] = useState(new Date())
  const [selectedFlow, setSelectedFlow] = useState<'light' | 'medium' | 'heavy' | null>(null)

  // Load all cycle entries (last 365 days)
  const loadAll = useCallback(async () => {
    const start = format(subDays(new Date(), 365), 'yyyy-MM-dd')
    const end = format(new Date(), 'yyyy-MM-dd')
    const all = await db.getEntriesByDateRange(start, end, 'cycle')
    setAllFlowEntries(all)
  }, [])

  useEffect(() => { loadAll() }, [loadAll])

  // Extract unique flow dates
  const flowDates = useMemo(() => {
    const set = new Set<string>()
    allFlowEntries.forEach(e => set.add(e.date))
    return set
  }, [allFlowEntries])

  // Compute stats
  const stats = useMemo(() => computeStats([...flowDates]), [flowDates])

  // Current day's flow status
  const todayFlow = useMemo(() => {
    if (entries.length === 0) return null
    return parseData<CycleData>(entries[entries.length - 1])
  }, [entries])

  const toggleFlow = async (flow: 'light' | 'medium' | 'heavy') => {
    // If same flow already logged today, remove it (toggle off)
    if (todayFlow?.flow === flow) {
      for (const e of entries) {
        await db.deleteEntry(e.id)
      }
    } else {
      // Remove existing entries for this day, then add new one
      for (const e of entries) {
        await db.deleteEntry(e.id)
      }
      const data: CycleData = { flow }
      await db.insertEntry('cycle', date, JSON.stringify(data))
    }
    refresh()
    loadAll()
  }

  const handleCalDateClick = (d: string) => {
    goTo(d)
  }

  const regularityLabel = (score: number | null) => {
    if (score === null) return 'N/A'
    if (score >= 80) return 'Very regular'
    if (score >= 60) return 'Regular'
    if (score >= 40) return 'Somewhat irregular'
    return 'Irregular'
  }

  return (
    <PageLayout categoryKey="cycle" title="🩸 Cycle" date={date} onDateChange={goTo}>
      {/* Flow toggle for selected day */}
      <div style={{
        borderRadius: 14, padding: 14, background: 'rgba(255,255,255,0.35)', marginBottom: 16
      }}>
        <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 8 }}>Log flow for this day</div>
        <div style={{ display: 'flex', gap: 8 }}>
          {(['light', 'medium', 'heavy'] as const).map(level => {
            const active = todayFlow?.flow === level
            const icons = { light: '🩸', medium: '🩸🩸', heavy: '🩸🩸🩸' }
            return (
              <button
                key={level}
                onClick={() => toggleFlow(level)}
                style={{
                  flex: 1, border: 'none', borderRadius: 10, padding: '10px 6px',
                  background: active ? '#E84672' : 'rgba(255,255,255,0.4)',
                  color: active ? '#fff' : colors.text,
                  fontSize: 12, cursor: 'pointer', fontFamily: 'inherit',
                  fontWeight: active ? 600 : 400
                }}
              >
                <div style={{ fontSize: 14, marginBottom: 2 }}>{icons[level]}</div>
                {level.charAt(0).toUpperCase() + level.slice(1)}
              </button>
            )
          })}
        </div>
        {todayFlow && (
          <div style={{ fontSize: 11, color: `${colors.text}60`, marginTop: 6, textAlign: 'center' }}>
            Tap again to remove
          </div>
        )}
      </div>

      {/* Stats cards */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16, flexWrap: 'wrap' }}>
        <Stat
          label="Cycle length"
          value={stats.avgCycleLength ? `${stats.avgCycleLength}d` : '--'}
          sub={stats.cycles.length > 0 ? `${stats.cycles.length} cycle${stats.cycles.length !== 1 ? 's' : ''} tracked` : 'Log more data'}
        />
        <Stat
          label="Period length"
          value={stats.avgPeriodLength ? `${stats.avgPeriodLength}d` : '--'}
        />
        <Stat
          label="Regularity"
          value={stats.regularity !== null ? `${stats.regularity}%` : '--'}
          sub={regularityLabel(stats.regularity)}
        />
      </div>

      {/* Predictions */}
      {(stats.nextPeriod || stats.nextOvulation) && (
        <div style={{
          borderRadius: 14, padding: 14, background: 'rgba(255,255,255,0.35)', marginBottom: 16
        }}>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 8 }}>Predictions</div>
          <div style={{ display: 'flex', gap: 12, flexWrap: 'wrap' }}>
            {stats.nextOvulation && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ width: 10, height: 10, borderRadius: 3, background: '#8B5CF6' }} />
                <span style={{ fontSize: 13, color: colors.text }}>
                  Ovulation: <b>{format(parseISO(stats.nextOvulation), 'MMM d')}</b>
                </span>
                {differenceInCalendarDays(parseISO(stats.nextOvulation), new Date()) >= 0 && (
                  <span style={{ fontSize: 11, color: `${colors.text}60` }}>
                    ({differenceInCalendarDays(parseISO(stats.nextOvulation), new Date())}d away)
                  </span>
                )}
              </div>
            )}
            {stats.nextPeriod && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                <div style={{ width: 10, height: 10, borderRadius: 3, background: '#E84672' }} />
                <span style={{ fontSize: 13, color: colors.text }}>
                  Next period: <b>{format(parseISO(stats.nextPeriod), 'MMM d')}</b>
                </span>
                {differenceInCalendarDays(parseISO(stats.nextPeriod), new Date()) >= 0 && (
                  <span style={{ fontSize: 11, color: `${colors.text}60` }}>
                    ({differenceInCalendarDays(parseISO(stats.nextPeriod), new Date())}d away)
                  </span>
                )}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Calendar */}
      <div style={{
        borderRadius: 14, padding: 14, background: 'rgba(255,255,255,0.35)'
      }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
          <button onClick={() => setCalMonth(m => subMonths(m, 1))} style={{
            border: 'none', background: 'none', cursor: 'pointer', fontSize: 16, color: colors.text, padding: '4px 8px'
          }}><ChevronLeft size={18} strokeWidth={2} style={{ verticalAlign: 'text-bottom' }} /></button>
          <div style={{ fontSize: 14, fontWeight: 600, color: colors.text }}>
            {format(calMonth, 'MMMM yyyy')}
          </div>
          <button onClick={() => setCalMonth(m => addMonths(m, 1))} style={{
            border: 'none', background: 'none', cursor: 'pointer', fontSize: 16, color: colors.text, padding: '4px 8px'
          }}><ChevronRight size={18} strokeWidth={2} style={{ verticalAlign: 'text-bottom' }} /></button>
        </div>
        <CycleCalendar
          month={calMonth}
          flowDates={flowDates}
          predictedPeriod={stats.nextPeriod}
          predictedOvulation={stats.nextOvulation}
          avgPeriodLength={stats.avgPeriodLength}
          selectedDate={date}
          onDateClick={handleCalDateClick}
        />
      </div>
    </PageLayout>
  )
}
