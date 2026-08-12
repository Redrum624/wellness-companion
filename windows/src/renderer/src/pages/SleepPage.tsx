import { useState, useMemo } from 'react'
import PageLayout from '../components/PageLayout'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { SleepData } from '../types/entry'

const colors = categoryColors.sleep

function computeSleepHours(bedtime: string, wakeTime: string): number {
  const [bh, bm] = bedtime.split(':').map(Number)
  const [wh, wm] = wakeTime.split(':').map(Number)
  let bedMin = bh * 60 + bm
  let wakeMin = wh * 60 + wm
  if (wakeMin <= bedMin) wakeMin += 24 * 60
  return (wakeMin - bedMin) / 60
}

function computeQuality(totalHours: number, wakeUps: number): number {
  let score = 10
  if (totalHours < 7) score -= Math.ceil(7 - totalHours)
  score -= wakeUps
  return Math.max(1, Math.min(10, score))
}

export default function SleepPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'sleep')
  const [bedtime, setBedtime] = useState('23:00')
  const [wakeTime, setWakeTime] = useState('07:00')
  const [wakeUps, setWakeUps] = useState<string[]>([])
  const [newWakeUp, setNewWakeUp] = useState('')

  const totalHours = useMemo(() => computeSleepHours(bedtime, wakeTime), [bedtime, wakeTime])
  const quality = useMemo(() => computeQuality(totalHours, wakeUps.length), [totalHours, wakeUps])
  const saved = entries.length > 0

  const saveSleep = async () => {
    const data: SleepData = { bedtime, wakeTime, wakeUps, totalHours, qualityScore: quality }
    await db.insertEntry('sleep', date, JSON.stringify(data))
    refresh()
  }

  return (
    <PageLayout categoryKey="sleep" title="🌙 Sleep" date={date} onDateChange={goTo}>
      <div style={{ textAlign: 'center', marginBottom: 20 }}>
        <div style={{ fontSize: 40, fontWeight: 700, color: colors.text }}>
          {Math.floor(totalHours)}h {Math.round((totalHours % 1) * 60)}m
        </div>
        <div style={{ fontSize: 13, color: `${colors.text}88` }}>total sleep</div>
      </div>

      {/* Time inputs */}
      <div style={{ display: 'flex', gap: 16, justifyContent: 'center', marginBottom: 20 }}>
        <TimeInput label="Bedtime" value={bedtime} onChange={setBedtime} />
        <span style={{ color: `${colors.text}50`, paddingTop: 20 }}>→</span>
        <TimeInput label="Wake up" value={wakeTime} onChange={setWakeTime} />
      </div>

      {/* Quality score */}
      <div style={{
        borderRadius: 14, padding: 16, background: 'rgba(255,255,255,0.35)', marginBottom: 12
      }}>
        <div style={{ fontSize: 12, color: `${colors.text}80` }}>Quality score</div>
        <div style={{ fontSize: 28, fontWeight: 600, color: colors.text }}>{quality}/10</div>
      </div>

      {/* Wake-ups */}
      <div style={{
        borderRadius: 14, padding: 16, background: 'rgba(255,255,255,0.35)', marginBottom: 16
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 12, color: `${colors.text}80` }}>Wake-ups</span>
          <div style={{ display: 'flex', gap: 6 }}>
            <input
              type="time"
              value={newWakeUp}
              onChange={e => setNewWakeUp(e.target.value)}
              style={{
                border: 'none', borderRadius: 8, padding: '3px 8px',
                background: 'rgba(255,255,255,0.4)', color: colors.text,
                fontSize: 12, fontFamily: 'inherit'
              }}
            />
            <button onClick={() => { if (newWakeUp) { setWakeUps([...wakeUps, newWakeUp]); setNewWakeUp('') } }} style={{
              border: 'none', borderRadius: 8, padding: '3px 10px',
              background: 'rgba(255,255,255,0.5)', color: colors.text,
              fontSize: 12, cursor: 'pointer', fontFamily: 'inherit'
            }}>+ Add</button>
          </div>
        </div>
        {wakeUps.length === 0 && <div style={{ fontSize: 12, color: `${colors.text}50`, marginTop: 6 }}>No wake-ups</div>}
        {wakeUps.map((w, i) => (
          <div key={i} style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0' }}>
            <span style={{ fontSize: 13, color: '#F0997B' }}>{w}</span>
            <button onClick={() => setWakeUps(wakeUps.filter((_, j) => j !== i))} style={{
              border: 'none', background: 'none', color: `${colors.text}50`, cursor: 'pointer', fontSize: 13
            }}>✕</button>
          </div>
        ))}
      </div>

      {!saved ? (
        <button onClick={saveSleep} style={{
          width: '100%', border: 'none', borderRadius: 14, padding: '10px',
          background: 'rgba(255,255,255,0.5)', color: colors.text,
          fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'inherit'
        }}>Save sleep log</button>
      ) : (
        <div style={{ textAlign: 'center', fontSize: 14, color: `${colors.text}70` }}>Sleep logged ✓</div>
      )}
    </PageLayout>
  )
}

function TimeInput({ label, value, onChange }: { label: string; value: string; onChange: (v: string) => void }) {
  const colors = categoryColors.sleep
  return (
    <div style={{ textAlign: 'center' }}>
      <div style={{ fontSize: 11, color: `${colors.text}80` }}>{label}</div>
      <input
        type="time"
        value={value}
        onChange={e => onChange(e.target.value)}
        style={{
          border: 'none', borderRadius: 14, padding: '10px 16px',
          background: 'rgba(255,255,255,0.4)', color: colors.text,
          fontSize: 20, fontFamily: 'inherit', textAlign: 'center'
        }}
      />
    </div>
  )
}
