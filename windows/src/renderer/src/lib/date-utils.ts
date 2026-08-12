import { format, subDays, parseISO, differenceInCalendarDays } from 'date-fns'

export function todayDateString(): string {
  return format(new Date(), 'yyyy-MM-dd')
}

export function todayDisplayString(): string {
  return format(new Date(), 'EEEE, MMMM d')
}

export function greetingForHour(hour = new Date().getHours()): string {
  if (hour < 12) return 'Good morning'
  if (hour < 17) return 'Good afternoon'
  return 'Good evening'
}

export function formatDate(date: string): string {
  return format(parseISO(date), 'EEEE, MMMM d')
}

export function last7DateStrings(): string[] {
  const today = new Date()
  return Array.from({ length: 7 }, (_, i) => format(subDays(today, 6 - i), 'yyyy-MM-dd'))
}

export function last7DayLabels(): string[] {
  const today = new Date()
  return Array.from({ length: 7 }, (_, i) => format(subDays(today, 6 - i), 'EEE'))
}

export function last7DaysRange(): [string, string] {
  const today = new Date()
  return [format(subDays(today, 6), 'yyyy-MM-dd'), format(today, 'yyyy-MM-dd')]
}

export function currentStreak(loggedDates: string[]): number {
  if (loggedDates.length === 0) return 0

  const dates = new Set(loggedDates)
  const today = format(new Date(), 'yyyy-MM-dd')
  const yesterday = format(subDays(new Date(), 1), 'yyyy-MM-dd')

  let current = dates.has(today) ? today : dates.has(yesterday) ? yesterday : null
  if (!current) return 0

  let streak = 0
  let d = parseISO(current)
  while (dates.has(format(d, 'yyyy-MM-dd'))) {
    streak++
    d = subDays(d, 1)
  }
  return streak
}

export function formatMinutes(min: number): string {
  if (min < 60) return `${min}m`
  const h = Math.floor(min / 60)
  const m = min % 60
  return m > 0 ? `${h}h ${m}m` : `${h}h`
}

export function formatMlCompact(ml: number): string {
  if (ml >= 1000) return `${(ml / 1000).toFixed(1)}L`
  return `${ml}ml`
}
