interface Props {
  value: number
  min?: number
  max?: number
  onChange: (value: number) => void
  color: string
  label?: string
}

export default function SliderInput({ value, min = 1, max = 10, onChange, color, label }: Props) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
      {label && <span style={{ fontSize: 12, color: `${color}88`, width: 60 }}>{label}</span>}
      <input
        type="range"
        min={min}
        max={max}
        value={value}
        onChange={e => onChange(Number(e.target.value))}
        className="slider-input"
        style={{ flex: 1, accentColor: color }}
      />
      <span style={{ fontSize: 16, fontWeight: 600, color, width: 30, textAlign: 'center' }}>{value}</span>
    </div>
  )
}
