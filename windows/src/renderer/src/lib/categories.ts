import { categoryColors, type CategoryKey } from '../styles/theme'

export interface CategoryDef {
  key: CategoryKey
  name: string
  icon: string
  path: string
}

export const categories: CategoryDef[] = [
  { key: 'water',        name: 'Water',    icon: '\uD83D\uDCA7', path: '/water' },
  { key: 'food',         name: 'Food',     icon: '\uD83E\uDD6A', path: '/food' },
  { key: 'bathroom',     name: 'Bathroom', icon: '\uD83D\uDEBD', path: '/bathroom' },
  { key: 'health',       name: 'Health',   icon: '\uD83D\uDC9A', path: '/health' },
  { key: 'sleep',        name: 'Sleep',    icon: '\uD83C\uDF19', path: '/sleep' },
  { key: 'emotions',     name: 'Emotions', icon: '\uD83C\uDF3B', path: '/emotions' },
  { key: 'interactions', name: 'Journal',  icon: '\uD83D\uDCAC', path: '/interactions' },
  { key: 'chores',       name: 'Chores',   icon: '\u2705',       path: '/chores' },
  { key: 'hobbies',      name: 'Hobbies',  icon: '\uD83C\uDFA8', path: '/hobbies' },
  { key: 'ideas',        name: 'Ideas',    icon: '\uD83D\uDCA1', path: '/ideas' },
  { key: 'cycle',        name: 'Cycle',    icon: '\uD83E\uDE78', path: '/cycle' },
  { key: 'badhabits',    name: 'Bad Habits', icon: '\u26A0\uFE0F', path: '/badhabits' }
]

export function getCategoryByKey(key: string): CategoryDef | undefined {
  return categories.find(c => c.key === key)
}

export function getCategoryColors(key: CategoryKey) {
  return categoryColors[key]
}
