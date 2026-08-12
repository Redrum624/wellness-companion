import { useState, useMemo } from 'react'
import PageLayout from '../components/PageLayout'
import { categoryColors } from '../styles/theme'
import { useDateNav } from '../hooks/useDateNav'
import { useEntries } from '../hooks/useEntries'
import { useDatabase } from '../hooks/useDatabase'
import { DailyGoals } from '../lib/daily-goals'
import type { ChoreData, ChoreTask } from '../types/entry'
import { parseData } from '../types/entry'

const colors = categoryColors.chores

export default function ChoresPage() {
  const { date, goTo } = useDateNav()
  const db = useDatabase()
  const { entries, refresh } = useEntries(date, 'chores')
  const [newTask, setNewTask] = useState('')

  // Flatten all tasks from all entries
  const allTasks = useMemo(() => {
    const tasks: { entryId: string; task: ChoreTask; index: number }[] = []
    entries.forEach(e => {
      const d = parseData<ChoreData>(e)
      d?.tasks.forEach((t, i) => tasks.push({ entryId: e.id, task: t, index: i }))
    })
    return tasks
  }, [entries])

  const completedCount = allTasks.filter(t => t.task.completed).length

  const addTask = async () => {
    if (!newTask.trim()) return
    const task: ChoreTask = { name: newTask.trim(), completed: false }
    const data: ChoreData = { tasks: [task] }
    await db.insertEntry('chores', date, JSON.stringify(data))
    setNewTask('')
    refresh()
  }

  const toggleTask = async (entryId: string, taskIndex: number) => {
    const entry = entries.find(e => e.id === entryId)
    if (!entry) return
    const d = parseData<ChoreData>(entry)
    if (!d) return
    d.tasks[taskIndex].completed = !d.tasks[taskIndex].completed
    if (d.tasks[taskIndex].completed) d.tasks[taskIndex].completedAt = Date.now()
    await db.updateEntry(entryId, JSON.stringify(d))
    refresh()
  }

  const progress = allTasks.length > 0 ? completedCount / allTasks.length : 0

  return (
    <PageLayout categoryKey="chores" title="✅ Chores" date={date} onDateChange={goTo}>
      <div style={{ fontSize: 13, color: `${colors.text}88`, marginBottom: 4 }}>
        {completedCount}/{allTasks.length} tasks done (goal: {DailyGoals.CHORES_TASKS_MIN}+)
      </div>

      {/* Progress bar */}
      <div style={{ height: 6, borderRadius: 3, background: 'rgba(255,255,255,0.3)', marginBottom: 16 }}>
        <div style={{ height: '100%', borderRadius: 3, background: `${colors.text}50`, width: `${progress * 100}%`, transition: 'width 0.3s' }} />
      </div>

      {/* Add task */}
      <div style={{ display: 'flex', gap: 8, marginBottom: 16 }}>
        <input
          value={newTask}
          onChange={e => setNewTask(e.target.value)}
          placeholder="Add a task..."
          onKeyDown={e => { if (e.key === 'Enter') addTask() }}
          style={{
            flex: 1, border: 'none', borderRadius: 10, padding: '8px 12px',
            background: 'rgba(255,255,255,0.3)', color: colors.text,
            fontSize: 13, fontFamily: 'inherit', outline: 'none'
          }}
        />
        <button onClick={addTask} style={{
          border: 'none', borderRadius: 10, padding: '8px 14px',
          background: 'rgba(255,255,255,0.4)', color: colors.text,
          fontSize: 13, cursor: 'pointer', fontFamily: 'inherit'
        }}>+ Add</button>
      </div>

      {/* Task list */}
      {allTasks.map(({ entryId, task, index }) => (
        <div
          key={`${entryId}-${index}`}
          onClick={() => toggleTask(entryId, index)}
          style={{
            display: 'flex', alignItems: 'center', gap: 10,
            padding: '8px 12px', borderRadius: 10,
            background: 'rgba(255,255,255,0.3)', marginBottom: 4,
            cursor: 'pointer', fontSize: 13, color: colors.text,
            opacity: task.completed ? 0.6 : 1,
            textDecoration: task.completed ? 'line-through' : 'none'
          }}
        >
          <input
            type="checkbox"
            checked={task.completed}
            readOnly
            style={{ accentColor: colors.text }}
          />
          {task.name}
        </div>
      ))}
    </PageLayout>
  )
}
