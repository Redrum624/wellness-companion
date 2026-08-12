import { categoryColors, type CategoryKey } from '../styles/theme'

interface Props {
  categoryKey: CategoryKey
  icon: string
  name: string
  summary: string
  streak: number
  onClick: () => void
}

export default function CategoryCard({ categoryKey, icon, name, summary, streak, onClick }: Props) {
  const colors = categoryColors[categoryKey]

  return (
    <button
      onClick={onClick}
      style={{
        position: 'relative',
        overflow: 'hidden',
        border: `1px solid ${colors.text}1A`,
        borderRadius: 20,
        padding: '14px 12px',
        cursor: 'pointer',
        textAlign: 'center',
        background: `linear-gradient(180deg, ${colors.cardBg}EB 0%, ${colors.cardBg}B3 100%)`,
        transition: 'transform 0.15s',
        width: '100%'
      }}
      onMouseDown={e => (e.currentTarget.style.transform = 'scale(0.95)')}
      onMouseUp={e => (e.currentTarget.style.transform = 'scale(1)')}
      onMouseLeave={e => (e.currentTarget.style.transform = 'scale(1)')}
    >
      {/* Decorative circle */}
      <div style={{
        position: 'absolute', top: -12, right: -12,
        width: 60, height: 60, borderRadius: '50%',
        background: 'rgba(255,255,255,0.18)'
      }} />

      {/* Emoji with tinted circle backdrop */}
      <div style={{
        width: 42, height: 42, borderRadius: '50%',
        background: `${colors.text}14`,
        display: 'flex', alignItems: 'center', justifyContent: 'center',
        margin: '0 auto', fontSize: 22
      }}>
        {icon}
      </div>

      <div style={{ fontSize: 13, fontWeight: 600, color: colors.text, marginTop: 5 }}>{name}</div>
      <div style={{ fontSize: 11, color: `${colors.text}88`, marginTop: 2, lineHeight: '14px' }}>{summary}</div>
      {streak > 0 && (
        <div style={{ fontSize: 10, color: `${colors.text}66`, marginTop: 3 }}>
          🔥 {streak}
        </div>
      )}
    </button>
  )
}
