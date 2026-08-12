import { useState } from 'react'
import PageLayout from '../components/PageLayout'
import TagInput from '../components/TagInput'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import type { IdeaData } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.ideas

export default function IdeasPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'ideas')
  const [title, setTitle] = useState('')
  const [body, setBody] = useState('')
  const [tags, setTags] = useState<string[]>([])
  const [editingId, setEditingId] = useState<string | null>(null)

  const save = async () => {
    if (!title.trim() && !body.trim()) return
    const data: IdeaData = { title: title.trim(), body: body.trim(), tags }
    if (editingId) {
      await db.updateEntry(editingId, JSON.stringify(data))
      setEditingId(null)
    } else {
      await db.insertEntry('ideas', date, JSON.stringify(data))
    }
    setTitle('')
    setBody('')
    setTags([])
    refresh()
  }

  const startEdit = (e: any) => {
    const d = parseData<IdeaData>(e)
    if (!d) return
    setEditingId(e.id)
    setTitle(d.title)
    setBody(d.body)
    setTags(d.tags || [])
  }

  const cancelEdit = () => {
    setEditingId(null)
    setTitle('')
    setBody('')
    setTags([])
  }

  const deleteEntry = async (id: string) => {
    await db.deleteEntry(id)
    if (editingId === id) cancelEdit()
    refresh()
  }

  return (
    <PageLayout categoryKey="ideas" title="💡 Ideas" date={date} onDateChange={goTo}>
      {/* Input form */}
      <div style={{
        borderRadius: 14, padding: 16, background: 'rgba(255,255,255,0.35)', marginBottom: 20
      }}>
        <input
          value={title}
          onChange={e => setTitle(e.target.value)}
          placeholder="Idea title"
          style={{
            width: '100%', border: 'none', borderRadius: 10, padding: '8px 12px',
            background: 'rgba(255,255,255,0.4)', color: colors.text,
            fontSize: 14, fontWeight: 500, fontFamily: 'inherit', outline: 'none', marginBottom: 10
          }}
        />
        <textarea
          value={body}
          onChange={e => setBody(e.target.value)}
          placeholder="Describe your idea..."
          rows={4}
          style={{
            width: '100%', border: 'none', borderRadius: 10, padding: '10px 12px',
            background: 'rgba(255,255,255,0.3)', color: colors.text,
            fontSize: 13, fontFamily: 'inherit', outline: 'none', resize: 'vertical', marginBottom: 10
          }}
        />
        <div style={{ marginBottom: 10 }}>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 4 }}>Tags</div>
          <TagInput tags={tags} onChange={setTags} placeholder="Add tags..." color={colors.text} />
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button onClick={save} style={{
            flex: 1, border: 'none', borderRadius: 14, padding: '10px',
            background: 'rgba(255,255,255,0.5)', color: colors.text,
            fontSize: 14, fontWeight: 500, cursor: 'pointer', fontFamily: 'inherit'
          }}>
            {editingId ? 'Update idea' : 'Save idea'}
          </button>
          {editingId && (
            <button onClick={cancelEdit} style={{
              border: 'none', borderRadius: 14, padding: '10px 16px',
              background: 'transparent', color: `${colors.text}70`,
              fontSize: 13, cursor: 'pointer', fontFamily: 'inherit'
            }}>
              Cancel
            </button>
          )}
        </div>
      </div>

      {/* Entries list */}
      {entries.length > 0 && (
        <div>
          <div style={{ fontSize: 12, color: `${colors.text}80`, marginBottom: 8 }}>
            {entries.length} idea{entries.length !== 1 ? 's' : ''} today
          </div>
          {entries.map(e => {
            const d = parseData<IdeaData>(e)
            const time = new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            return (
              <div key={e.id} style={{
                padding: '10px 14px', borderRadius: 12, background: 'rgba(255,255,255,0.3)',
                marginBottom: 6, color: colors.text
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: 4 }}>
                  <div>
                    {d?.title && <div style={{ fontSize: 14, fontWeight: 500 }}>{d.title}</div>}
                    <div style={{ fontSize: 11, color: `${colors.text}60` }}>{time}</div>
                  </div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    <button onClick={() => startEdit(e)} style={{
                      border: 'none', background: 'none', cursor: 'pointer',
                      fontSize: 12, color: `${colors.text}70`, padding: '2px 4px'
                    }}>edit</button>
                    <button onClick={() => deleteEntry(e.id)} style={{
                      border: 'none', background: 'none', cursor: 'pointer',
                      fontSize: 12, color: `${colors.text}50`, padding: '2px 4px'
                    }}>delete</button>
                  </div>
                </div>
                {d?.body && <div style={{ fontSize: 13, lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>{d.body}</div>}
                {d?.tags && d.tags.length > 0 && (
                  <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 6 }}>
                    {d.tags.map(tag => (
                      <span key={tag} style={{
                        fontSize: 11, padding: '2px 8px', borderRadius: 8,
                        background: 'rgba(255,255,255,0.4)', color: `${colors.text}90`
                      }}>{tag}</span>
                    ))}
                  </div>
                )}
              </div>
            )
          })}
        </div>
      )}
    </PageLayout>
  )
}
