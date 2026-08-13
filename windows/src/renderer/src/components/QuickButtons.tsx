interface Props {
  values: number[]
  label?: string
  color: string
  onSelect: (value: number) => void
}

export default function QuickButtons({ values, label, color, onSelect }: Props) {
  return (
    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap' }}>
      {values.map(v => (
        <button
          key={v}
          onClick={() => onSelect(v)}
          className="quick-btn"
          style={{
            border: 'none', borderRadius: 12, padding: '6px 14px',
            background: `${color}30`, color, fontSize: 13,
            cursor: 'pointer', fontFamily: 'inherit', fontWeight: 500
          }}
        >
          +{v}{label || ''}
        </button>
      ))}
    </div>
  )
}
