import { useState } from 'react'
import PageLayout from '../components/PageLayout'
import SliderInput from '../components/SliderInput'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { HealthData } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.health
const commonSymptoms = ['Headache', 'Fatigue', 'Nausea', 'Cramps', 'Dizziness']

export default function HealthPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'health')
  const [energy, setEnergy] = useState(5)
  const [rating, setRating] = useState(5)
  const [symptoms, setSymptoms] = useState<string[]>([])
  const [note, setNote] = useState('')

  const toggleSymptom = (s: string) => {
    setSymptoms(prev => prev.includes(s) ? prev.filter(x => x !== s) : [...prev, s])
  }

  const logHealth = async () => {
    const data: HealthData = { energyLevel: energy, dailyRating: rating, symptoms, note: note || undefined }
    await db.insertEntry('health', date, JSON.stringify(data))
    setSymptoms([])
    setNote('')
    refresh()
  }

  return (
    <PageLayout categoryKey="health" title="💚 Health" date={date} onDateChange={goTo}>
      <SliderInput value={energy} onChange={setEnergy} color={colors.text} label="Energy" />
      <div style={{ height: 12 }} />
      <SliderInput value={rating} onChange={setRating} color={colors.text} label="Rating" />

      <div style={{ marginTop: 16 }}>
        <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>Symptoms</div>
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
          {commonSymptoms.map(s => (
            <button
              key={s}
              onClick={() => toggleSymptom(s)}
              style={{
                border: symptoms.includes(s) ? `1.5px solid ${colors.text}` : '1.5px solid transparent',
                borderRadius: 14, padding: '5px 12px',
                background: symptoms.includes(s) ? `${colors.text}20` : 'rgba(255,255,255,0.3)',
                color: colors.text, fontSize: 12, cursor: 'pointer', fontFamily: 'inherit'
              }}
            >
              {s}
            </button>
          ))}
        </div>
      </div>

      <textarea
        value={note}
        onChange={e => setNote(e.target.value)}
        placeholder="Notes (optional)..."
        rows={2}
        style={{
          width: '100%', border: 'none', borderRadius: 10, padding: '8px 12px',
          background: 'rgba(255,255,255,0.3)', color: colors.text,
          fontSize: 13, fontFamily: 'inherit', outline: 'none', resize: 'vertical', marginTop: 12
        }}
      />

      <button onClick={logHealth} style={{
        width: '100%', border: 'none', borderRadius: 14, padding: '10px',
        background: 'rgba(255,255,255,0.5)', color: colors.text,
        fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'inherit', marginTop: 10
      }}>
        Log health check
      </button>

      {entries.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>Today's logs</div>
          {entries.map(e => {
            const d = parseData<HealthData>(e)
            const time = new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            return (
              <div key={e.id} style={{
                padding: '6px 12px', borderRadius: 8, background: 'rgba(255,255,255,0.25)',
                marginBottom: 3, fontSize: 12, color: colors.text
              }}>
                <span style={{ color: `${colors.text}70` }}>{time}</span>
                {d?.energyLevel && <span style={{ marginLeft: 8 }}>Energy: {d.energyLevel}/10</span>}
                {d?.symptoms && d.symptoms.length > 0 && (
                  <span style={{ marginLeft: 8, color: '#F0997B' }}>{d.symptoms.join(', ')}</span>
                )}
              </div>
            )
          })}
        </div>
      )}
    </PageLayout>
  )
}
