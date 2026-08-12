import { useState, useMemo } from 'react'
import PageLayout from '../components/PageLayout'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { FoodData } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.food
const mealSlots = [
  { type: 'breakfast', icon: '🌅', label: 'Breakfast' },
  { type: 'lunch', icon: '☀️', label: 'Lunch' },
  { type: 'dinner', icon: '🌙', label: 'Dinner' },
  { type: 'snacks', icon: '🍪', label: 'Snacks' }
]

export default function FoodPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'food')
  const [editingMeal, setEditingMeal] = useState<string | null>(null)
  const [description, setDescription] = useState('')

  const loggedMeals = useMemo(() => {
    const map: Record<string, string> = {}
    entries.forEach(e => {
      const d = parseData<FoodData>(e)
      if (d) map[d.mealType] = d.description || ''
    })
    return map
  }, [entries])

  const saveMeal = async () => {
    if (!editingMeal) return
    const data: FoodData = { mealType: editingMeal, description }
    await db.insertEntry('food', date, JSON.stringify(data))
    setEditingMeal(null)
    setDescription('')
    refresh()
  }

  return (
    <PageLayout categoryKey="food" title="🥪 Food" date={date} onDateChange={goTo}>
      <div style={{ fontSize: 13, color: `${colors.text}88`, marginBottom: 16 }}>
        {Object.keys(loggedMeals).length}/4 meals logged
      </div>

      {mealSlots.map(slot => {
        const logged = loggedMeals[slot.type]
        const isEditing = editingMeal === slot.type
        return (
          <div
            key={slot.type}
            style={{
              borderRadius: 14, padding: '14px 16px', marginBottom: 8,
              background: 'rgba(255,255,255,0.35)',
              borderLeft: logged !== undefined ? `3px solid ${colors.text}60` : '3px solid transparent',
              cursor: isEditing ? 'default' : 'pointer'
            }}
            onClick={() => { if (!isEditing) { setEditingMeal(slot.type); setDescription(logged || '') } }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <span style={{ fontSize: 18 }}>{slot.icon}</span>
              <span style={{ fontWeight: 500, color: colors.text, fontSize: 14 }}>{slot.label}</span>
              {logged !== undefined && !isEditing && (
                <span style={{ fontSize: 12, color: `${colors.text}70`, marginLeft: 'auto' }}>{logged || 'Logged'}</span>
              )}
            </div>
            {isEditing && (
              <div style={{ marginTop: 10, display: 'flex', gap: 8 }}>
                <input
                  autoFocus
                  value={description}
                  onChange={e => setDescription(e.target.value)}
                  placeholder="What did you eat?"
                  onKeyDown={e => { if (e.key === 'Enter') saveMeal(); if (e.key === 'Escape') setEditingMeal(null) }}
                  style={{
                    flex: 1, border: 'none', borderRadius: 8, padding: '6px 10px',
                    background: 'rgba(255,255,255,0.4)', color: colors.text,
                    fontSize: 12, fontFamily: 'inherit', outline: 'none'
                  }}
                />
                <button onClick={saveMeal} style={{
                  border: 'none', borderRadius: 8, padding: '6px 12px',
                  background: `${colors.text}15`, color: colors.text,
                  fontSize: 12, cursor: 'pointer', fontFamily: 'inherit'
                }}>Save</button>
              </div>
            )}
          </div>
        )
      })}
    </PageLayout>
  )
}
