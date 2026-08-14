import { useState, useEffect } from 'react'
import PageLayout from '../components/PageLayout'
import TagInput from '../components/TagInput'
import StarRating from '../components/StarRating'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { InteractionData, Person } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.interactions

export default function InteractionsPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'interactions')
  const [people, setPeople] = useState<string[]>([])
  const [rating, setRating] = useState(0)
  const [journal, setJournal] = useState('')
  const [savedPeople, setSavedPeople] = useState<Person[]>([])
  const [managePeople, setManagePeople] = useState(false)

  const loadPeople = () => db.getPeople().then((p: Person[]) => setSavedPeople(p))
  useEffect(() => { loadPeople() }, [])

  const removePerson = async (id: string) => {
    await db.deletePerson(id)
    await loadPeople()
  }

  const logEntry = async () => {
    const data: InteractionData = { people, qualityRating: rating, journalText: journal }
    await db.insertEntry('interactions', date, JSON.stringify(data))
    // Save new people
    for (const name of people) {
      if (!savedPeople.some(p => p.name === name)) {
        await db.addPerson(name)
      }
    }
    await loadPeople()
    setPeople([])
    setRating(0)
    setJournal('')
    refresh()
  }

  return (
    <PageLayout categoryKey="interactions" title="💬 Journal" date={date} onDateChange={goTo}>
      {/* People */}
      <div style={{ marginBottom: 14 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6 }}>
          <div style={{ fontSize: 12, color: `${colors.text}80` }}>Who?</div>
          {savedPeople.length > 0 && (
            <button
              onClick={() => setManagePeople(m => !m)}
              style={{ border: 'none', background: 'none', color: `${colors.text}60`, fontSize: 11, cursor: 'pointer', fontFamily: 'inherit', padding: 0 }}
            >
              {managePeople ? 'done' : 'manage people'}
            </button>
          )}
        </div>
        <TagInput tags={people} onChange={setPeople} suggestions={savedPeople.map(p => p.name)} placeholder="Add people..." color={colors.text} />
        {managePeople && (
          <div style={{ marginTop: 8 }}>
            <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
              {savedPeople.map(p => (
                <span key={p.id} style={{
                  display: 'inline-flex', alignItems: 'center', gap: 4,
                  padding: '3px 10px', borderRadius: 12,
                  background: 'rgba(255,255,255,0.3)', color: colors.text, fontSize: 12
                }}>
                  {p.name}
                  <button
                    onClick={() => removePerson(p.id)}
                    title={`Remove ${p.name} from suggestions`}
                    style={{ border: 'none', background: 'none', color: `${colors.text}80`, cursor: 'pointer', fontSize: 14, padding: 0, fontFamily: 'inherit' }}
                  >
                    ×
                  </button>
                </span>
              ))}
            </div>
            <div style={{ fontSize: 10, color: `${colors.text}50`, marginTop: 4 }}>
              Removing a person only clears them from suggestions — past entries keep their names.
            </div>
          </div>
        )}
      </div>

      {/* Rating */}
      <div style={{ marginBottom: 14 }}>
        <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>How was it?</div>
        <StarRating value={rating} onChange={setRating} color={colors.text} />
      </div>

      {/* Journal */}
      <textarea
        value={journal}
        onChange={e => setJournal(e.target.value)}
        placeholder="Write about your interactions..."
        rows={5}
        style={{
          width: '100%', border: 'none', borderRadius: 10, padding: '10px 14px',
          background: 'rgba(255,255,255,0.3)', color: colors.text,
          fontSize: 13, fontFamily: 'inherit', outline: 'none', resize: 'vertical'
        }}
      />

      <button onClick={logEntry} style={{
        width: '100%', border: 'none', borderRadius: 14, padding: '10px',
        background: 'rgba(255,255,255,0.5)', color: colors.text,
        fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'inherit', marginTop: 10
      }}>
        Save entry
      </button>

      {entries.length > 0 && (
        <div style={{ marginTop: 20 }}>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 6 }}>Today's entries</div>
          {entries.map(e => {
            const d = parseData<InteractionData>(e)
            const time = new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            return (
              <div key={e.id} style={{
                padding: '8px 12px', borderRadius: 10, background: 'rgba(255,255,255,0.25)',
                marginBottom: 4, fontSize: 12, color: colors.text
              }}>
                <div style={{ color: `${colors.text}70`, marginBottom: 2 }}>{time} · {'⭐'.repeat(d?.qualityRating || 0)}</div>
                {d?.people && d.people.length > 0 && <div style={{ marginBottom: 2 }}>{d.people.join(', ')}</div>}
                {d?.journalText && <div>{d.journalText.slice(0, 120)}{d.journalText.length > 120 ? '...' : ''}</div>}
              </div>
            )
          })}
        </div>
      )}
    </PageLayout>
  )
}
