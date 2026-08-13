import type { CSSProperties } from 'react'
import type { Entry } from '../types/entry'
import { getCategoryByKey } from '../lib/categories'
import { categoryColors, type CategoryKey } from '../styles/theme'

interface Props {
  entries: Entry[]
  filterCategory?: string
  onFilterChange?: (category: string) => void
}

export default function Timeline({ entries, filterCategory, onFilterChange }: Props) {
  const filtered = filterCategory
    ? entries.filter(e => e.category === filterCategory)
    : entries

  return (
    <div>
      {/* Filter pills */}
      <div style={{ display: 'flex', gap: 6, marginBottom: 12, flexWrap: 'wrap' }}>
        <FilterPill
          label="All"
          active={!filterCategory}
          onClick={() => onFilterChange?.('')}
        />
        {['water', 'food', 'bathroom', 'health', 'sleep', 'emotions', 'interactions', 'chores', 'hobbies'].map(key => {
          const cat = getCategoryByKey(key)
          return cat ? (
            <FilterPill
              key={key}
              label={cat.icon}
              active={filterCategory === key}
              onClick={() => onFilterChange?.(key)}
              color={categoryColors[key as CategoryKey].text}
            />
          ) : null
        })}
      </div>

      {/* Entry list */}
      {filtered.length === 0 && (
        <div style={{ fontSize: 13, color: '#3D326260', padding: 16 }}>No entries for this date</div>
      )}
      {filtered.map((entry, idx) => {
        const cat = getCategoryByKey(entry.category)
        const colors = categoryColors[entry.category as CategoryKey]
        const time = new Date(entry.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
        const data = JSON.parse(entry.data)
        const preview = getPreview(entry.category, data)

        return (
          <div
            key={entry.id}
            className="timeline-entry"
            style={{
              display: 'flex', alignItems: 'center', gap: 10,
              padding: '8px 12px', marginBottom: 4,
              borderRadius: 10, background: 'rgba(255,255,255,0.3)',
              fontSize: 13,
              // Stagger the fade-in per row; cap the delay growth past ~15 rows
              // so a long list doesn't keep the tail waiting to appear.
              '--i': Math.min(idx, 15)
            } as CSSProperties}
          >
            <span style={{ fontSize: 14 }}>{cat?.icon}</span>
            <span style={{ color: '#3D326280', width: 48, flexShrink: 0 }}>{time}</span>
            <span style={{ color: colors?.text || '#333', flex: 1 }}>{preview}</span>
          </div>
        )
      })}
    </div>
  )
}

function FilterPill({ label, active, onClick, color }: { label: string; active: boolean; onClick: () => void; color?: string }) {
  return (
    <button
      onClick={onClick}
      style={{
        border: 'none', borderRadius: 12, padding: '4px 10px',
        background: active ? 'rgba(255,255,255,0.5)' : 'rgba(255,255,255,0.2)',
        color: color || '#3D3262', fontSize: 12, cursor: 'pointer',
        fontWeight: active ? 600 : 400, fontFamily: 'inherit'
      }}
    >
      {label}
    </button>
  )
}

function getPreview(category: string, data: any): string {
  switch (category) {
    case 'water': return `${data.ml}ml ${data.type}`
    case 'food': return `${data.mealType}: ${data.description || '(no description)'}`
    case 'bathroom': return data.note || 'Bathroom break'
    case 'health': return data.energyLevel ? `Energy: ${data.energyLevel}/10` : 'Health check'
    case 'sleep': return `${data.totalHours?.toFixed(1) || 0}h sleep`
    case 'emotions': return data.emotion ? data.emotion.charAt(0).toUpperCase() + data.emotion.slice(1) : 'Mood logged'
    case 'interactions': return data.journalText?.slice(0, 60) || 'Interaction logged'
    case 'chores': return `${data.tasks?.filter((t: any) => t.completed).length || 0}/${data.tasks?.length || 0} tasks done`
    case 'hobbies': return `${data.hobbyName}: ${data.durationMin}min`
    default: return 'Entry'
  }
}
