import { useState } from 'react'
import PageLayout from '../components/PageLayout'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { EmotionData } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.emotions

const moods = [
  { key: 'happy', emoji: '😊', label: 'Happy', bg: '#F5E5C4' },
  { key: 'calm', emoji: '😌', label: 'Calm', bg: '#CCE8DD' },
  { key: 'sad', emoji: '☹️', label: 'Sad', bg: '#D4E9F7' },
  { key: 'angry', emoji: '😠', label: 'Angry', bg: '#F5C4B3' },
  { key: 'anxious', emoji: '😰', label: 'Anxious', bg: '#F5D6E3' },
  { key: 'tired', emoji: '😴', label: 'Tired', bg: '#DEDCF7' }
]

export default function EmotionsPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'emotions')
  const [selected, setSelected] = useState<string | null>(null)
  const [note, setNote] = useState('')

  const logEmotion = async () => {
    if (!selected) return
    const data: EmotionData = { emotion: selected, note: note || undefined }
    await db.insertEntry('emotions', date, JSON.stringify(data))
    setSelected(null)
    setNote('')
    refresh()
  }

  return (
    <PageLayout categoryKey="emotions" title="🌻 Emotions" date={date} onDateChange={goTo}>
      <div style={{ fontSize: 15, color: colors.text, fontWeight: 500, marginBottom: 16, textAlign: 'center' }}>
        How are you feeling?
      </div>

      {/* Mood grid */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 10, marginBottom: 16 }}>
        {moods.map(mood => (
          <button
            key={mood.key}
            onClick={() => setSelected(mood.key)}
            style={{
              border: selected === mood.key ? `2px solid ${colors.text}` : '2px solid transparent',
              borderRadius: 14, padding: '12px 8px',
              background: mood.bg, cursor: 'pointer', textAlign: 'center',
              transform: selected === mood.key ? 'scale(1.05)' : 'scale(1)',
              transition: 'all 0.15s'
            }}
          >
            <div style={{ fontSize: 26 }}>{mood.emoji}</div>
            <div style={{ fontSize: 11, color: colors.text, marginTop: 4 }}>{mood.label}</div>
          </button>
        ))}
      </div>

      {/* Note + log */}
      <textarea
        value={note}
        onChange={e => setNote(e.target.value)}
        placeholder="Add a note (optional)..."
        rows={2}
        style={{
          width: '100%', border: 'none', borderRadius: 10, padding: '8px 12px',
          background: 'rgba(255,255,255,0.3)', color: colors.text,
          fontSize: 13, fontFamily: 'inherit', outline: 'none', resize: 'vertical'
        }}
      />
      <button
        onClick={logEmotion}
        disabled={!selected}
        style={{
          width: '100%', border: 'none', borderRadius: 14, padding: '10px',
          background: selected ? 'rgba(255,255,255,0.5)' : 'rgba(255,255,255,0.2)',
          color: selected ? colors.text : `${colors.text}50`,
          fontSize: 14, fontWeight: 500, cursor: selected ? 'pointer' : 'default',
          fontFamily: 'inherit', marginTop: 10
        }}
      >
        Log feeling
      </button>

      {/* Today's log */}
      {entries.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>Today's moods</div>
          {entries.map(e => {
            const d = parseData<EmotionData>(e)
            const mood = moods.find(m => m.key === d?.emotion)
            const time = new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            return (
              <div key={e.id} style={{
                display: 'flex', alignItems: 'center', gap: 8,
                padding: '6px 12px', borderRadius: 8, background: 'rgba(255,255,255,0.25)',
                marginBottom: 3, fontSize: 12, color: colors.text
              }}>
                <span style={{ color: `${colors.text}70` }}>{time}</span>
                <span>{mood?.emoji}</span>
                <span>{mood?.label}</span>
                {d?.note && <span style={{ color: `${colors.text}60`, marginLeft: 'auto' }}>{d.note}</span>}
              </div>
            )
          })}
        </div>
      )}
    </PageLayout>
  )
}
