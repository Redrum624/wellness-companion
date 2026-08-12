export const categoryColors = {
  water:        { cardBg: '#D4E9F7', screenBg: '#C8DDF0', text: '#2A5A80' },
  food:         { cardBg: '#D6EDCC', screenBg: '#D2E8C8', text: '#27500A' },
  bathroom:     { cardBg: '#F5D6E3', screenBg: '#F0D0DC', text: '#72243E' },
  health:       { cardBg: '#CCE8DD', screenBg: '#C4E2D6', text: '#085041' },
  sleep:        { cardBg: '#DEDCF7', screenBg: '#CCC8EE', text: '#3C3489' },
  emotions:     { cardBg: '#F5E5C4', screenBg: '#F0DFB8', text: '#633806' },
  interactions: { cardBg: '#F0D6CA', screenBg: '#EDD0C2', text: '#712B13' },
  chores:       { cardBg: '#E2E0D8', screenBg: '#DDD8CE', text: '#444441' },
  hobbies:      { cardBg: '#F0D2E6', screenBg: '#EACCE0', text: '#72243E' },
  ideas:        { cardBg: '#D6DFF5', screenBg: '#CDD8F0', text: '#2A3A6B' },
  cycle:        { cardBg: '#F5D0D6', screenBg: '#F0C8D0', text: '#7A2040' },
  badhabits:    { cardBg: '#E8D6E0', screenBg: '#DDC9D4', text: '#4A2538' }
} as const

export type CategoryKey = keyof typeof categoryColors

export const accentColors = {
  teal: '#5DCAA5',
  coral: '#F0997B',
  wave: '#85B7EB'
}
