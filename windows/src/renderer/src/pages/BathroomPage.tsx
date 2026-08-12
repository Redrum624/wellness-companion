import { useState, useMemo } from 'react'
import PageLayout from '../components/PageLayout'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import { DailyGoals } from '../lib/daily-goals'
import type { BathroomData } from '../types/entry'

const colors = categoryColors.bathroom

export default function BathroomPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'bathroom')
  const [note, setNote] = useState('')

  const poopCount = useMemo(() =>
    entries.filter(e => { try { return (JSON.parse(e.data) as BathroomData).type === 'poop' } catch { return false } }).length
  , [entries])

  const logNow = async (type?: string) => {
    const data: BathroomData = { type, note: note || undefined }
    await db.insertEntry('bathroom', date, JSON.stringify(data))
    setNote('')
    refresh()
  }

  return (
    <PageLayout categoryKey="bathroom" title="🚽 Bathroom" date={date} onDateChange={goTo}>
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <div style={{ fontSize: 48, fontWeight: 700, color: colors.text }}>{entries.length}</div>
        <div style={{ fontSize: 13, color: `${colors.text}88` }}>
          breaks today (normal: {DailyGoals.BATHROOM_NORMAL_MIN}–{DailyGoals.BATHROOM_NORMAL_MAX})
        </div>
        {poopCount > 0 && (
          <div style={{ fontSize: 13, color: `${colors.text}88`, marginTop: 4 }}>
            💩 {poopCount} poop{poopCount !== 1 ? 's' : ''}
          </div>
        )}
      </div>

      {/* Quick log buttons */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
        <button onClick={() => logNow()} style={{
          flex: 1, border: 'none', borderRadius: 14, padding: '12px 10px',
          background: 'rgba(255,255,255,0.5)', color: colors.text,
          fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit'
        }}>
          🚽 Log break
        </button>
        <button onClick={() => logNow('poop')} style={{
          flex: 1, border: 'none', borderRadius: 14, padding: '12px 10px',
          background: 'rgba(255,255,255,0.5)', color: colors.text,
          fontSize: 14, fontWeight: 600, cursor: 'pointer', fontFamily: 'inherit'
        }}>
          💩 Log poop
        </button>
      </div>

      {/* Note input */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 20 }}>
        <input
          value={note}
          onChange={e => setNote(e.target.value)}
          placeholder="Optional note..."
          style={{
            flex: 1, border: 'none', borderRadius: 10, padding: '10px 14px',
            background: 'rgba(255,255,255,0.3)', color: colors.text,
            fontSize: 13, fontFamily: 'inherit', outline: 'none'
          }}
        />
      </div>

      {/* Timeline */}
      {entries.length > 0 && (
        <div>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 8 }}>Timeline</div>
          {entries.map(e => {
            const time = new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            const data = JSON.parse(e.data) as BathroomData
            return (
              <div key={e.id} style={{
                display: 'flex', alignItems: 'center', gap: 10, padding: '6px 0'
              }}>
                <div style={{ width: 10, height: 10, borderRadius: '50%', background: '#ED93B199', flexShrink: 0 }} />
                <div style={{
                  flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  padding: '6px 12px', borderRadius: 8, background: 'rgba(255,255,255,0.3)',
                  fontSize: 13, color: colors.text
                }}>
                  <span>{time} {data.type === 'poop' ? '💩' : ''}</span>
                  {data.note && <span style={{ color: `${colors.text}70` }}>{data.note}</span>}
                </div>
              </div>
            )
          })}
        </div>
      )}
    </PageLayout>
  )
}
