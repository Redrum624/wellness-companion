import type { Entry, WaterData, FoodData, SleepData, EmotionData, HobbyData, BadHabitsData } from '../types/entry'
import { parseData } from '../types/entry'
import { DailyGoals } from './daily-goals'
import { formatMlCompact } from './date-utils'
import type { CategoryKey } from '../styles/theme'

export function computeSummary(category: CategoryKey, entries: Entry[]): string {
  switch (category) {
    case 'water': {
      const totalMl = entries.reduce((sum, e) => {
        const d = parseData<WaterData>(e)
        return sum + (d?.type === 'drink' ? d.ml : 0)
      }, 0)
      return formatMlCompact(totalMl)
    }
    case 'food': {
      const logged = new Set(entries.map(e => parseData<FoodData>(e)?.mealType).filter(Boolean)).size
      return `${logged}/${DailyGoals.FOOD_MEALS} meals`
    }
    case 'bathroom':
      return `${entries.length} breaks`
    case 'health': {
      const count = entries.length
      return `${count}/${DailyGoals.HEALTH_ENERGY_CHECKINS} logs`
    }
    case 'sleep': {
      const sleeps = entries.map(e => parseData<SleepData>(e)).filter(Boolean) as SleepData[]
      const completed = sleeps.find(s => s.wakeTime)
      if (completed) {
        const h = Math.floor(completed.totalHours)
        const m = Math.round((completed.totalHours - h) * 60)
        return `${h}h ${m}m`
      }
      if (sleeps.length > 0) return `Bedtime ${sleeps[0].bedtime}`
      return `Goal: ${DailyGoals.SLEEP_IDEAL_HOURS}h`
    }
    case 'emotions': {
      const count = entries.length
      const last = entries.length > 0 ? parseData<EmotionData>(entries[0]) : null
      if (last) {
        const name = last.emotion.charAt(0).toUpperCase() + last.emotion.slice(1)
        return `${name} (${count}/${DailyGoals.EMOTIONS_CHECKINS})`
      }
      return `0/${DailyGoals.EMOTIONS_CHECKINS} check-ins`
    }
    case 'interactions':
      return `${entries.length}/${DailyGoals.INTERACTIONS_MIN} entries`
    case 'chores':
      return `${entries.length}/${DailyGoals.CHORES_TASKS_MIN} done`
    case 'hobbies': {
      if (entries.length === 0) return `0/${DailyGoals.HOBBIES_DAILY_MIN} min`
      const totalMin = entries.reduce((sum, e) => {
        const d = parseData<HobbyData>(e)
        return sum + (d?.durationMin ?? 0)
      }, 0)
      return `${totalMin} min`
    }
    case 'badhabits': {
      if (entries.length === 0) return 'Clean day'
      const totals = { alcohol: 0, weed: 0, tobacco: 0, selfharm: 0 }
      entries.forEach(e => {
        const d = parseData<BadHabitsData>(e)
        if (d && d.substance in totals) totals[d.substance] += d.count || 0
      })
      const parts: string[] = []
      if (totals.alcohol) parts.push(`🍷${totals.alcohol}`)
      if (totals.weed) parts.push(`🌿${totals.weed}`)
      if (totals.tobacco) parts.push(`🚬${totals.tobacco}`)
      if (totals.selfharm) parts.push(`🩹${totals.selfharm}`)
      return parts.join(' ') || 'Clean day'
    }
    default:
      return 'No data'
  }
}
