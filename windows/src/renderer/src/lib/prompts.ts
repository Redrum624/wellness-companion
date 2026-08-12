import type { Entry } from '../types/entry'
import { categories } from './categories'

export function buildWeeklyPortraitPrompt(entries: Entry[], dateRange: string): string {
  const grouped: Record<string, Entry[]> = {}
  entries.forEach(e => {
    if (!grouped[e.category]) grouped[e.category] = []
    grouped[e.category].push(e)
  })

  let dataSection = ''
  categories.forEach(cat => {
    const catEntries = grouped[cat.key] || []
    if (catEntries.length === 0) {
      dataSection += `\n${cat.name}: No entries logged.`
    } else {
      dataSection += `\n${cat.name} (${catEntries.length} entries):`
      catEntries.slice(0, 10).forEach(e => {
        const data = JSON.parse(e.data)
        dataSection += `\n  - ${e.date}: ${JSON.stringify(data)}`
      })
      if (catEntries.length > 10) dataSection += `\n  ... and ${catEntries.length - 10} more`
    }
  })

  return `/no_think
You are a gentle, supportive wellness companion. Analyze this week's wellness data and write a brief, warm portrait (3-5 paragraphs). Highlight patterns, celebrate wins, and offer gentle suggestions. Use a warm, encouraging tone.

Date range: ${dateRange}
${dataSection}

Write a weekly wellness portrait:`
}

export function buildChatPrompt(question: string, recentEntries: Entry[]): string {
  let context = ''
  const byDate: Record<string, Entry[]> = {}
  recentEntries.forEach(e => {
    if (!byDate[e.date]) byDate[e.date] = []
    byDate[e.date].push(e)
  })

  Object.keys(byDate).sort().reverse().slice(0, 7).forEach(date => {
    context += `\n${date}:`
    byDate[date].forEach(e => {
      const data = JSON.parse(e.data)
      context += `\n  ${e.category}: ${JSON.stringify(data)}`
    })
  })

  return `/no_think
You are a friendly wellness companion assistant. Answer the user's question based on their wellness data. Be specific, reference actual data points, and be encouraging. Keep answers concise (2-3 paragraphs max).

Recent wellness data:${context}

User's question: ${question}

Answer:`
}

export function buildPatternDetectionPrompt(entries: Entry[], dateRange: string): string {
  const grouped: Record<string, Entry[]> = {}
  entries.forEach(e => {
    if (!grouped[e.category]) grouped[e.category] = []
    grouped[e.category].push(e)
  })

  let dataSection = ''
  categories.forEach(cat => {
    const catEntries = grouped[cat.key] || []
    if (catEntries.length > 0) {
      dataSection += `\n${cat.name} (${catEntries.length} entries):`
      catEntries.slice(0, 15).forEach(e => {
        const data = JSON.parse(e.data)
        dataSection += `\n  - ${e.date}: ${JSON.stringify(data)}`
      })
    }
  })

  return `/no_think
You are a wellness data analyst. Find correlations and patterns across wellness categories. Look for:
- Days with high/low energy and what was different (sleep, hydration, hobbies)
- Mood patterns related to social interactions, exercise, or chores
- Sleep quality trends and what affects them
- Consistency patterns (streaks, skipped days)

Be specific — cite dates, numbers, and category connections. Keep it to 3-5 key findings.

Date range: ${dateRange}
${dataSection}

Key patterns found:`
}

export function buildMonthlyDeepDivePrompt(entries: Entry[], dateRange: string): string {
  const byCategory: Record<string, { count: number; samples: string[] }> = {}
  entries.forEach(e => {
    if (!byCategory[e.category]) byCategory[e.category] = { count: 0, samples: [] }
    byCategory[e.category].count++
    if (byCategory[e.category].samples.length < 8) {
      byCategory[e.category].samples.push(`${e.date}: ${e.data}`)
    }
  })

  let summary = ''
  categories.forEach(cat => {
    const info = byCategory[cat.key]
    if (info) {
      summary += `\n${cat.name}: ${info.count} total entries`
      info.samples.forEach(s => { summary += `\n  ${s}` })
    } else {
      summary += `\n${cat.name}: No entries`
    }
  })

  return `/no_think
You are a wellness coach writing a monthly deep-dive report. Structure your report as:

1. **Overview** — Overall wellness score and highlights
2. **Category Breakdown** — Brief analysis of each category with trends
3. **Wins** — What went well this month
4. **Areas for Growth** — Gentle suggestions for improvement
5. **Goals for Next Month** — 2-3 actionable goals

Be warm, specific, and data-driven. Reference actual entries.

Date range: ${dateRange}
${summary}

Monthly Deep Dive Report:`
}
