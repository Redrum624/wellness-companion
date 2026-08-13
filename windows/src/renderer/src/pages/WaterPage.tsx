import { useState, useMemo } from 'react'
import { RotateCcw } from 'lucide-react'
import PageLayout from '../components/PageLayout'
import QuickButtons from '../components/QuickButtons'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import { DailyGoals } from '../lib/daily-goals'
import { formatMlCompact } from '../lib/date-utils'
import type { WaterData } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.water

export default function WaterPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'water')
  const [customMl, setCustomMl] = useState('')

  const totalConsumed = useMemo(() =>
    entries.reduce((sum, e) => {
      const d = parseData<WaterData>(e)
      return sum + (d?.type === 'drink' ? d.ml : 0)
    }, 0), [entries])

  const progress = Math.min(totalConsumed / DailyGoals.WATER_ML, 1)

  const logDrink = async (ml: number) => {
    const data: WaterData = { ml, type: 'drink', bottleCapacity: DailyGoals.WATER_BOTTLE_DEFAULT_ML }
    await db.insertEntry('water', date, JSON.stringify(data))
    refresh()
  }

  const logRefill = async () => {
    const data: WaterData = { ml: DailyGoals.WATER_BOTTLE_DEFAULT_ML, type: 'refill', bottleCapacity: DailyGoals.WATER_BOTTLE_DEFAULT_ML }
    await db.insertEntry('water', date, JSON.stringify(data))
    refresh()
  }

  return (
    <PageLayout categoryKey="water" title="💧 Water" date={date} onDateChange={goTo}>
      {/* Total */}
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <div style={{ fontSize: 40, fontWeight: 700, color: colors.text }}>{formatMlCompact(totalConsumed)}</div>
        <div style={{ fontSize: 13, color: `${colors.text}88` }}>of {DailyGoals.WATER_ML}ml daily goal</div>
        {/* Progress bar */}
        <div style={{ height: 6, borderRadius: 3, background: 'rgba(255,255,255,0.3)', marginTop: 8 }}>
          <div style={{ height: '100%', borderRadius: 3, background: progress >= 1 ? '#5DCAA5' : `${colors.text}50`, width: `${progress * 100}%`, transition: 'width 0.3s' }} />
        </div>
        <div style={{ fontSize: 11, color: progress >= 1 ? '#5DCAA5' : `${colors.text}60`, marginTop: 4 }}>
          {Math.round(progress * 100)}% of daily goal
        </div>
      </div>

      {/* Quick buttons */}
      <div style={{ marginBottom: 16 }}>
        <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>Quick add</div>
        <QuickButtons values={[100, 250, 500]} label="ml" color={colors.text} onSelect={logDrink} />
      </div>

      {/* Custom amount */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <input
          type="number"
          value={customMl}
          onChange={e => setCustomMl(e.target.value)}
          placeholder="Custom ml"
          style={{
            flex: 1, border: 'none', borderRadius: 10, padding: '8px 12px',
            background: 'rgba(255,255,255,0.3)', color: colors.text,
            fontSize: 13, fontFamily: 'inherit', outline: 'none'
          }}
          onKeyDown={e => { if (e.key === 'Enter' && customMl) { logDrink(Number(customMl)); setCustomMl('') } }}
        />
        <button onClick={logRefill} style={{
          border: 'none', borderRadius: 10, padding: '8px 14px',
          background: 'rgba(255,255,255,0.4)', color: colors.text,
          fontSize: 13, cursor: 'pointer', fontFamily: 'inherit',
          display: 'flex', alignItems: 'center', gap: 6
        }}>
          <RotateCcw size={16} strokeWidth={2} style={{ verticalAlign: 'text-bottom' }} /> Refill
        </button>
      </div>

      {/* Today's log */}
      {entries.length > 0 && (
        <div>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>Log</div>
          {entries.map(e => {
            const d = parseData<WaterData>(e)
            const time = new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            return (
              <div key={e.id} style={{
                display: 'flex', justifyContent: 'space-between', padding: '6px 12px',
                borderRadius: 8, background: d?.type === 'drink' ? 'rgba(180,218,248,0.3)' : 'rgba(168,228,192,0.3)',
                marginBottom: 3, fontSize: 12, color: colors.text
              }}>
                <span>{time}</span>
                <span>{d?.ml}ml {d?.type}</span>
              </div>
            )
          })}
        </div>
      )}
    </PageLayout>
  )
}
