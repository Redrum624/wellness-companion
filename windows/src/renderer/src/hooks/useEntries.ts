import { useState, useEffect, useCallback } from 'react'
import type { Entry } from '../types/entry'
import { useDatabase } from './useDatabase'

export function useEntries(date: string, category?: string) {
  const db = useDatabase()
  const [entries, setEntries] = useState<Entry[]>([])
  const [loading, setLoading] = useState(true)

  const refresh = useCallback(async () => {
    setLoading(true)
    const result = category
      ? await db.getEntriesByDateAndCategory(date, category)
      : await db.getEntriesByDate(date)
    setEntries(result)
    setLoading(false)
  }, [date, category])

  useEffect(() => { refresh() }, [refresh])

  return { entries, loading, refresh }
}

export function useAllTodayEntries(date: string) {
  const db = useDatabase()
  const [entries, setEntries] = useState<Entry[]>([])

  const refresh = useCallback(async () => {
    const result = await db.getEntriesByDate(date)
    setEntries(result)
  }, [date])

  useEffect(() => { refresh() }, [refresh])

  return { entries, refresh }
}
