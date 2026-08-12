import { useState, useCallback } from 'react'
import { format, addDays, subDays } from 'date-fns'

export function useDateNav() {
  const [date, setDate] = useState(format(new Date(), 'yyyy-MM-dd'))

  const goToday = useCallback(() => setDate(format(new Date(), 'yyyy-MM-dd')), [])
  const goPrev = useCallback(() => setDate(d => format(subDays(new Date(d), 1), 'yyyy-MM-dd')), [])
  const goNext = useCallback(() => setDate(d => format(addDays(new Date(d), 1), 'yyyy-MM-dd')), [])
  const goTo = useCallback((d: string) => setDate(d), [])

  const isToday = date === format(new Date(), 'yyyy-MM-dd')

  return { date, isToday, goToday, goPrev, goNext, goTo }
}
