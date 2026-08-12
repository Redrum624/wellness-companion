import { useState, useEffect, useMemo } from 'react'
import PageLayout from '../components/PageLayout'
import QuickButtons from '../components/QuickButtons'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import { DailyGoals } from '../lib/daily-goals'
import { formatMinutes } from '../lib/date-utils'
import type { HobbyData, Hobby } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.hobbies
const defaultColors = ['#AFA9EC', '#F0997B', '#9FE1CB', '#85B7EB', '#F5D6E3', '#F5E5C4', '#D6EDCC', '#E2E0D8']

export default function HobbiesPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'hobbies')
  const [hobbies, setHobbies] = useState<Hobby[]>([])
  const [newHobbyName, setNewHobbyName] = useState('')
  const [selectedColor, setSelectedColor] = useState(0)
  const [showAdd, setShowAdd] = useState(false)

  useEffect(() => {
    db.getHobbies().then(setHobbies)
  }, [])

  const totalMinutes = useMemo(() =>
    entries.reduce((sum, e) => sum + (parseData<HobbyData>(e)?.durationMin ?? 0), 0), [entries])

  const logTime = async (hobby: Hobby, minutes: number) => {
    const data: HobbyData = { hobbyName: hobby.name, durationMin: minutes, color: hobby.color }
    await db.insertEntry('hobbies', date, JSON.stringify(data))
    refresh()
  }

  const addHobby = async () => {
    if (!newHobbyName.trim()) return
    await db.addHobby(newHobbyName.trim(), defaultColors[selectedColor])
    const updated = await db.getHobbies()
    setHobbies(updated)
    setNewHobbyName('')
    setShowAdd(false)
  }

  return (
    <PageLayout categoryKey="hobbies" title="🎨 Hobbies" date={date} onDateChange={goTo}>
      <div style={{ textAlign: 'center', marginBottom: 16 }}>
        <div style={{ fontSize: 15, color: colors.text, fontWeight: 500 }}>{formatMinutes(totalMinutes)} today</div>
        <div style={{ fontSize: 11, color: `${colors.text}80` }}>
          Goal: {DailyGoals.HOBBIES_DAILY_MIN}+ min · {Math.floor(totalMinutes / 5)} cranes
        </div>
      </div>

      {/* Hobby list */}
      {hobbies.length === 0 && !showAdd && (
        <div style={{ fontSize: 13, color: `${colors.text}60`, textAlign: 'center', marginBottom: 12 }}>
          Add a hobby to get started!
        </div>
      )}

      {hobbies.map(hobby => {
        const hobbyMin = entries
          .map(e => parseData<HobbyData>(e))
          .filter(d => d?.hobbyName === hobby.name)
          .reduce((sum, d) => sum + (d?.durationMin ?? 0), 0)

        return (
          <div key={hobby.id} style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '8px 12px', borderRadius: 12,
            background: 'rgba(255,255,255,0.35)', marginBottom: 4
          }}>
            <div style={{ width: 12, height: 12, borderRadius: '50%', background: hobby.color, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 13, color: colors.text }}>{hobby.name}</div>
              {hobbyMin > 0 && <div style={{ fontSize: 11, color: `${colors.text}70` }}>{formatMinutes(hobbyMin)} today</div>}
            </div>
            <QuickButtons values={[5, 15, 30]} label="m" color={colors.text} onSelect={min => logTime(hobby, min)} />
          </div>
        )
      })}

      {/* Add hobby */}
      {showAdd ? (
        <div style={{
          borderRadius: 12, padding: 14, background: 'rgba(255,255,255,0.35)', marginTop: 8
        }}>
          <input
            autoFocus
            value={newHobbyName}
            onChange={e => setNewHobbyName(e.target.value)}
            placeholder="Hobby name"
            onKeyDown={e => { if (e.key === 'Enter') addHobby() }}
            style={{
              width: '100%', border: 'none', borderRadius: 8, padding: '6px 10px',
              background: 'rgba(255,255,255,0.4)', color: colors.text,
              fontSize: 13, fontFamily: 'inherit', outline: 'none', marginBottom: 8
            }}
          />
          <div style={{ display: 'flex', gap: 6, marginBottom: 8 }}>
            {defaultColors.map((c, i) => (
              <div
                key={c}
                onClick={() => setSelectedColor(i)}
                style={{
                  width: 24, height: 24, borderRadius: '50%', background: c,
                  cursor: 'pointer', border: i === selectedColor ? `2px solid ${colors.text}` : '2px solid transparent'
                }}
              />
            ))}
          </div>
          <div style={{ display: 'flex', gap: 8 }}>
            <button onClick={addHobby} style={{
              border: 'none', borderRadius: 10, padding: '6px 14px',
              background: 'rgba(255,255,255,0.5)', color: colors.text,
              fontSize: 12, cursor: 'pointer', fontFamily: 'inherit'
            }}>Add</button>
            <button onClick={() => setShowAdd(false)} style={{
              border: 'none', borderRadius: 10, padding: '6px 14px',
              background: 'transparent', color: `${colors.text}70`,
              fontSize: 12, cursor: 'pointer', fontFamily: 'inherit'
            }}>Cancel</button>
          </div>
        </div>
      ) : (
        <button onClick={() => setShowAdd(true)} style={{
          width: '100%', border: 'none', borderRadius: 14, padding: '10px',
          background: 'rgba(255,255,255,0.4)', color: colors.text,
          fontSize: 13, cursor: 'pointer', fontFamily: 'inherit', marginTop: 8
        }}>
          + Add hobby
        </button>
      )}
    </PageLayout>
  )
}
