interface Props {
  value: string
  onChange: (date: string) => void
  color: string
}

export default function DatePicker({ value, onChange, color }: Props) {
  return (
    <input
      type="date"
      value={value}
      onChange={e => onChange(e.target.value)}
      style={{
        border: 'none', borderRadius: 10, padding: '6px 10px',
        background: 'rgba(255,255,255,0.3)', color,
        fontSize: 12, fontFamily: 'inherit', outline: 'none',
        cursor: 'pointer'
      }}
    />
  )
}
