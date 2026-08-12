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

  // Tone note: warmth is wanted here — this is someone reading about her own
  // body and moods. What the earlier prompt got wrong was asking for warmth
  // FOUR times ("gentle", "supportive", "warm", "encouraging") and for substance
  // once, so the model produced praise instead of observation. The rules below
  // keep the warmth and make specificity the way it is expressed.
  return `/no_think
You are her wellness companion. She logged this data herself and is reading this to understand her own week. Write to her directly, as "you".

Write 3-4 short paragraphs of flowing prose. Follow these rules exactly:

1. Ground every observation in the actual data. Name real numbers, days and entries: "you averaged 2.4 L across six days" is useful, "you stayed hydrated" is not. If you cannot point to data for a claim, leave the claim out.
2. Be warm and plain-spoken, like a thoughtful friend who has actually read the numbers. Never clinical, never diagnostic, never a medical opinion.
3. Do not open by praising the week. Do not call the week beautiful, lovely, or a symphony. Do not end with generic encouragement such as "you're doing wonderfully" or "keep going".
4. If the data shows a hard day or a decline, say so plainly and kindly. Do not reframe every dip as secretly fine — being honest about a rough patch is more respectful than smoothing it over.
5. Note one connection worth her attention, if the data supports one — for example how sleep length lines up with the energy she recorded, or which days the mood entries cluster on.
6. Close with one small, concrete thing she might try next week, drawn from a pattern you actually observed. One suggestion, not a list.
7. No emoji, no bullet points, no headings.

Date range: ${dateRange}
${dataSection}

Write her weekly portrait:`
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
You are her wellness companion. Answer her question using her own data, addressing her as "you".

Cite the actual entries you are drawing on — dates and numbers, not impressions. If the data does not answer the question, say so plainly instead of guessing. Warm and direct; no flattery, no emoji, no medical advice. Two or three short paragraphs at most.

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
