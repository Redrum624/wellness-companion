import type { CategoryKey } from '../styles/theme'

export interface Entry {
  id: string
  category: CategoryKey
  timestamp: number
  date: string
  data: string
  version: number
  modified_at: number
  synced: number
}

// Category-specific JSON data shapes (stored in entries.data)
export interface WaterData { ml: number; type: 'drink' | 'refill'; bottleCapacity: number }
export interface FoodData { mealType: string; description: string; photoPath?: string }
export interface BathroomData { type?: string; note?: string }
export interface HealthData { energyLevel?: number; dailyRating?: number; symptoms: string[]; note?: string }
export interface SleepData { bedtime: string; wakeTime?: string | null; wakeUps: string[]; totalHours: number; qualityScore: number }
export interface EmotionData { emotion: string; note?: string }
export interface InteractionData { people: string[]; qualityRating: number; journalText: string; promptUsed?: string }
export interface ChoreTask { name: string; category?: string; completed: boolean; timeSpentMin?: number; completedAt?: number }
export interface ChoreData { tasks: ChoreTask[] }
export interface HobbyData { hobbyName: string; durationMin: number; color: string }
export interface IdeaData { title: string; body: string; tags: string[] }
export interface CycleData { flow: 'light' | 'medium' | 'heavy'; note?: string }
export type BadHabitSubstance = 'alcohol' | 'weed' | 'tobacco' | 'selfharm'
export interface BadHabitsData { substance: BadHabitSubstance; count: number; level?: number; note?: string }

// Helper types
export interface ChoreTemplate { id: string; name: string; category?: string; recurrence?: string; created_at: number }
export interface Hobby { id: string; name: string; color: string; created_at: number }
export interface Person { id: string; name: string; created_at: number }
export interface DateCount { date: string; count: number }

// Parse entry data safely
export function parseData<T>(entry: Entry): T | null {
  try { return JSON.parse(entry.data) as T } catch { return null }
}
