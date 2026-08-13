import { useState, useEffect, useRef } from 'react'
import { subDays, format } from 'date-fns'
import { NotebookPen, Search, BarChart3 } from 'lucide-react'
import { useDatabase, useLlm } from '../hooks/useDatabase'
import { buildWeeklyPortraitPrompt, buildChatPrompt, buildPatternDetectionPrompt, buildMonthlyDeepDivePrompt } from '../lib/prompts'
import type { Entry } from '../types/entry'

export default function InsightsPage() {
  const db = useDatabase()
  const llm = useLlm()
  const [status, setStatus] = useState('idle')
  const [response, setResponse] = useState('')
  const [question, setQuestion] = useState('')
  const [isGenerating, setIsGenerating] = useState(false)
  const responseRef = useRef('')
  const scrollRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    llm.getStatus().then(setStatus)
    const unsub = llm.onStatusChange(setStatus)
    return unsub
  }, [])

  useEffect(() => {
    const unsub = llm.onToken((token) => {
      responseRef.current += token
      setResponse(responseRef.current)
      scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
    })
    return unsub
  }, [])

  const fetchAndRun = async (daysBack: number, promptBuilder: (entries: Entry[], range: string) => string) => {
    if (isGenerating) return
    setIsGenerating(true)
    responseRef.current = ''
    setResponse('')

    try {
      const today = new Date()
      const start = format(subDays(today, daysBack - 1), 'yyyy-MM-dd')
      const end = format(today, 'yyyy-MM-dd')

      const allEntries: Entry[] = []
      for (const cat of ['water', 'food', 'bathroom', 'health', 'sleep', 'emotions', 'interactions', 'chores', 'hobbies']) {
        const entries = await db.getEntriesByDateRange(start, end, cat)
        allEntries.push(...entries)
      }

      const prompt = promptBuilder(allEntries, `${start} to ${end}`)
      await llm.chat(prompt)
    } catch (err: any) {
      const detail = err?.message || 'Unknown error'
      responseRef.current += `\n\nError: ${detail}`
      setResponse(responseRef.current)
    }
    setIsGenerating(false)
  }

  const generatePortrait = () => fetchAndRun(7, buildWeeklyPortraitPrompt)
  const detectPatterns = () => fetchAndRun(14, buildPatternDetectionPrompt)
  const monthlyDeepDive = () => fetchAndRun(30, buildMonthlyDeepDivePrompt)

  const askQuestion = async () => {
    if (!question.trim() || isGenerating) return
    setIsGenerating(true)
    responseRef.current = ''
    setResponse('')
    const q = question
    setQuestion('')

    try {
      const today = format(new Date(), 'yyyy-MM-dd')
      const start = format(subDays(new Date(), 13), 'yyyy-MM-dd')

      const allEntries: Entry[] = []
      for (const cat of ['water', 'food', 'bathroom', 'health', 'sleep', 'emotions', 'interactions', 'chores', 'hobbies']) {
        const entries = await db.getEntriesByDateRange(start, today, cat)
        allEntries.push(...entries)
      }

      const prompt = buildChatPrompt(q, allEntries)
      await llm.chat(prompt)
    } catch (err: any) {
      const detail = err?.message || 'Unknown error'
      responseRef.current += `\n\nError: ${detail}`
      setResponse(responseRef.current)
    }
    setIsGenerating(false)
  }

  const [errorDetail, setErrorDetail] = useState('')

  useEffect(() => {
    if (status === 'error') {
      llm.getError().then((e: string) => setErrorDetail(e || ''))
    }
  }, [status])

  const statusColor = status === 'ready' ? '#5DCAA5' : status === 'loading' || status === 'generating' ? '#F0997B' : status === 'error' ? '#E74C3C' : '#999'
  const statusLabel = status === 'idle' ? 'Model not loaded' : status === 'loading' ? 'Loading model...' : status === 'ready' ? 'Ready' : status === 'generating' ? 'Thinking' : 'Error'

  return (
    <div style={{ padding: 24, maxWidth: 700, margin: '0 auto', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 16 }}>
        <h2 style={{ color: '#3D3262', fontWeight: 600, fontSize: 22, flex: 1 }}>Insights</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
          <div
            className={status === 'generating' ? 'status-dot-generating' : undefined}
            style={{ width: 8, height: 8, borderRadius: '50%', background: statusColor }}
          />
          <span style={{ fontSize: 11, color: '#3D326280' }}>
            {statusLabel}
            {status === 'generating' && <span className="thinking-dots" />}
          </span>
        </div>
      </div>

      {status === 'error' && errorDetail && (
        <div className="pop-in" style={{
          background: 'rgba(231,76,60,0.1)', borderRadius: 10, padding: 12,
          marginBottom: 12, fontSize: 12, color: '#C0392B', whiteSpace: 'pre-wrap', lineHeight: 1.5
        }}>
          {errorDetail}
        </div>
      )}

      {/* Action buttons */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        {[
          { label: 'Weekly Portrait', Icon: NotebookPen, action: generatePortrait },
          { label: 'Find Patterns', Icon: Search, action: detectPatterns },
          { label: 'Monthly Deep Dive', Icon: BarChart3, action: monthlyDeepDive }
        ].map(btn => (
          <button
            key={btn.label}
            onClick={btn.action}
            disabled={isGenerating}
            style={{
              border: 'none', borderRadius: 14, padding: '10px 18px',
              background: isGenerating ? 'rgba(255,255,255,0.2)' : 'rgba(255,255,255,0.5)',
              color: '#3D3262', fontSize: 13, fontWeight: 500,
              cursor: isGenerating ? 'default' : 'pointer', fontFamily: 'inherit',
              display: 'flex', alignItems: 'center', gap: 6
            }}
          >
            <btn.Icon size={16} strokeWidth={2} />
            {btn.label}
          </button>
        ))}
      </div>

      {/* Response area */}
      <div
        ref={scrollRef}
        style={{
          flex: 1, borderRadius: 14, padding: 16,
          background: 'rgba(255,255,255,0.3)',
          overflow: 'auto', whiteSpace: 'pre-wrap',
          fontSize: 14, color: '#3D3262', lineHeight: 1.6,
          minHeight: 200
        }}
      >
        {response}
        {isGenerating && <span className="stream-caret" />}
        {!response && !isGenerating && (
          <span style={{ color: '#3D326240' }}>
            Ask a question about your wellness data, or generate a weekly portrait to see patterns and insights.
          </span>
        )}
      </div>

      {/* Chat input */}
      <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
        <input
          value={question}
          onChange={e => setQuestion(e.target.value)}
          onKeyDown={e => { if (e.key === 'Enter') askQuestion() }}
          placeholder="Ask about your wellness data..."
          disabled={isGenerating}
          style={{
            flex: 1, border: 'none', borderRadius: 14, padding: '10px 16px',
            background: 'rgba(255,255,255,0.4)', color: '#3D3262',
            fontSize: 13, fontFamily: 'inherit', outline: 'none'
          }}
        />
        <button
          onClick={askQuestion}
          disabled={isGenerating || !question.trim()}
          style={{
            border: 'none', borderRadius: 14, padding: '10px 18px',
            background: 'rgba(255,255,255,0.5)', color: '#3D3262',
            fontSize: 13, cursor: 'pointer', fontFamily: 'inherit'
          }}
        >
          Ask
        </button>
      </div>
    </div>
  )
}
