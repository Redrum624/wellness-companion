import type { ReactNode } from 'react'
import type { CategoryKey } from '../styles/theme'
import { categoryColors } from '../styles/theme'
import DatePicker from './DatePicker'
import { CategoryBackground } from './PastelBackground'

interface Props {
  categoryKey: CategoryKey
  title: string
  date: string
  onDateChange: (date: string) => void
  children: ReactNode
}

export default function PageLayout({ categoryKey, title, date, onDateChange, children }: Props) {
  const colors = categoryColors[categoryKey]

  return (
    <div style={{
      position: 'relative',
      minHeight: '100vh',
      background: colors.screenBg,
      overflow: 'auto'
    }}>
      <CategoryBackground categoryKey={categoryKey} />

      <div style={{ position: 'relative', padding: 24 }}>
        <div style={{ maxWidth: 700, margin: '0 auto' }}>
          {/* Header */}
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 20 }}>
            <h2 style={{ color: colors.text, fontWeight: 600, fontSize: 22 }}>{title}</h2>
            <DatePicker value={date} onChange={onDateChange} color={colors.text} />
          </div>
          {children}
        </div>
      </div>
    </div>
  )
}
