import { useState, useMemo, useEffect } from 'react'
import PageLayout from '../components/PageLayout'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { BadHabitsData, BadHabitSubstance } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.badhabits

interface SubstanceDef {
  key: BadHabitSubstance
  icon: string
  label: string
  color: string
  hasLevel: boolean
  levelWords: string[]
}

const substances: SubstanceDef[] = [
  {
    key: 'alcohol',
    icon: '🍷',
    label: 'Alcohol',
    color: '#7A2040',
    hasLevel: true,
    levelWords: ['Sober', 'Regular', 'Warm', 'Buzzed', 'Tipsy', 'Drunk', 'Very drunk', 'Sloppy', 'Puke', 'Blackout', 'Blackout']
  },
  {
    key: 'weed',
    icon: '🌿',
    label: 'Weed',
    color: '#27500A',
    hasLevel: true,
    levelWords: ['Sober', 'Regular', 'Mellow', 'Buzzed', 'High', 'Very high', 'Stoned', 'Couch-locked', 'Spinning', 'Greened out', 'Greened out']
  },
  {
    key: 'tobacco',
    icon: '🚬',
    label: 'Tobacco / Vape',
    color: '#6B5562',
    hasLevel: false,
    levelWords: []
  },
  {
    key: 'selfharm',
    icon: '🩹',
    label: 'Self-harm',
    color: '#8B4A5A',
    hasLevel: false,
    levelWords: []
  }
]

export default function BadHabitsPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'badhabits')
  const [levels, setLevels] = useState<{ alcohol: number; weed: number }>({ alcohol: 0, weed: 0 })

  useEffect(() => {
    Promise.all([
      db.getSetting(`badhabits:${date}:alcohol:level`),
      db.getSetting(`badhabits:${date}:weed:level`)
    ]).then(([a, w]) =>
      setLevels({ alcohol: Number(a) || 0, weed: Number(w) || 0 })
    )
  }, [date])

  const counts = useMemo(() => {
    const c: Record<BadHabitSubstance, number> = { alcohol: 0, weed: 0, tobacco: 0, selfharm: 0 }
    entries.forEach(e => {
      const d = parseData<BadHabitsData>(e)
      if (d && d.substance in c) c[d.substance] += d.count || 0
    })
    return c
  }, [entries])

  const log = async (substance: BadHabitSubstance) => {
    const data: BadHabitsData = { substance, count: 1 }
    if (substance === 'alcohol' || substance === 'weed') data.level = levels[substance]
    await db.insertEntry('badhabits', date, JSON.stringify(data))
    refresh()
  }

  const undoLast = async (substance: BadHabitSubstance) => {
    const match = [...entries].reverse().find(e => parseData<BadHabitsData>(e)?.substance === substance)
    if (match) {
      await db.deleteEntry(match.id)
      refresh()
    }
  }

  const setLevel = async (substance: 'alcohol' | 'weed', value: number) => {
    setLevels(prev => ({ ...prev, [substance]: value }))
    await db.setSetting(`badhabits:${date}:${substance}:level`, String(value))
  }

  const totalToday = counts.alcohol + counts.weed + counts.tobacco + counts.selfharm

  return (
    <PageLayout categoryKey="badhabits" title="⚠️ Bad Habits" date={date} onDateChange={goTo}>
      {/* Summary */}
      <div style={{ textAlign: 'center', marginBottom: 22 }}>
        <div style={{ fontSize: 38, fontWeight: 700, color: colors.text }}>{totalToday}</div>
        <div style={{ fontSize: 12, color: `${colors.text}88` }}>
          {totalToday === 0 ? 'No slips today — nice' : `${totalToday} consumption${totalToday === 1 ? '' : 's'} logged`}
        </div>
      </div>

      {/* Substance cards */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
        {substances.map(s => {
          const count = counts[s.key]
          const level = s.hasLevel ? levels[s.key as 'alcohol' | 'weed'] : 0
          return (
            <div key={s.key} style={{
              background: 'rgba(255,255,255,0.35)',
              borderRadius: 16,
              padding: 16,
              border: `1px solid ${colors.text}1A`
            }}>
              {/* Header row */}
              <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 12 }}>
                <div style={{
                  width: 44, height: 44, borderRadius: '50%',
                  background: `${s.color}22`,
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: 22
                }}>{s.icon}</div>
                <div style={{ flex: 1 }}>
                  <div style={{ fontSize: 15, fontWeight: 600, color: colors.text }}>{s.label}</div>
                  <div style={{ fontSize: 11, color: `${colors.text}88` }}>
                    {count === 0 ? 'none today' : `${count} time${count === 1 ? '' : 's'}`}
                  </div>
                </div>
                <div style={{ fontSize: 28, fontWeight: 700, color: s.color, minWidth: 44, textAlign: 'right' }}>
                  {count}
                </div>
              </div>

              {/* Counter buttons */}
              <div style={{ display: 'flex', gap: 8, marginBottom: s.hasLevel ? 14 : 0 }}>
                <button
                  onClick={() => undoLast(s.key)}
                  disabled={count === 0}
                  style={{
                    flex: 1, border: 'none', borderRadius: 12, padding: '10px 0',
                    background: count === 0 ? 'rgba(255,255,255,0.2)' : 'rgba(255,255,255,0.5)',
                    color: count === 0 ? `${colors.text}40` : colors.text,
                    fontSize: 15, fontWeight: 600, cursor: count === 0 ? 'default' : 'pointer',
                    fontFamily: 'inherit'
                  }}
                >−1</button>
                <button
                  onClick={() => log(s.key)}
                  style={{
                    flex: 2, border: 'none', borderRadius: 12, padding: '10px 0',
                    background: s.color, color: '#fff',
                    fontSize: 15, fontWeight: 600, cursor: 'pointer',
                    fontFamily: 'inherit'
                  }}
                >+1 {s.label.split(' ')[0].toLowerCase()}</button>
              </div>

              {/* Level slider (alcohol, weed) */}
              {s.hasLevel && (
                <div>
                  <div style={{
                    display: 'flex', justifyContent: 'space-between', alignItems: 'baseline',
                    marginBottom: 6
                  }}>
                    <span style={{ fontSize: 11, color: `${colors.text}88` }}>Peak level</span>
                    <span style={{ fontSize: 13, fontWeight: 600, color: s.color }}>
                      {level}/10 · {s.levelWords[level] || ''}
                    </span>
                  </div>
                  <input
                    type="range"
                    min={0}
                    max={10}
                    step={1}
                    value={level}
                    onChange={e => setLevel(s.key as 'alcohol' | 'weed', Number(e.target.value))}
                    style={{
                      width: '100%',
                      accentColor: s.color,
                      cursor: 'pointer'
                    }}
                  />
                  <div style={{
                    display: 'flex', justifyContent: 'space-between',
                    fontSize: 9, color: `${colors.text}60`, marginTop: 2
                  }}>
                    <span>sober</span>
                    <span>buzzed</span>
                    <span>blackout</span>
                  </div>
                </div>
              )}
            </div>
          )
        })}
      </div>

      {/* Today's log */}
      {entries.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>Log</div>
          {entries.map(e => {
            const d = parseData<BadHabitsData>(e)
            const time = new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            const def = substances.find(s => s.key === d?.substance)
            return (
              <div key={e.id} style={{
                display: 'flex', alignItems: 'center', gap: 8, padding: '6px 12px',
                borderRadius: 8, background: 'rgba(255,255,255,0.25)',
                marginBottom: 3, fontSize: 12, color: colors.text
              }}>
                <span>{def?.icon}</span>
                <span style={{ flex: 1 }}>{def?.label || d?.substance}</span>
                {d?.level != null && def?.hasLevel && (
                  <span style={{ fontSize: 11, color: `${colors.text}88` }}>
                    lvl {d.level}
                  </span>
                )}
                <span style={{ fontSize: 11, color: `${colors.text}66` }}>{time}</span>
              </div>
            )
          })}
        </div>
      )}
    </PageLayout>
  )
}
