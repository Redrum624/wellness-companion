interface Props {
  value: number
  onChange: (value: number) => void
  color: string
}

export default function StarRating({ value, onChange, color }: Props) {
  return (
    <div style={{ display: 'flex', gap: 4 }}>
      {[1, 2, 3, 4, 5].map(star => (
        <button
          key={star}
          onClick={() => onChange(star)}
          style={{
            border: 'none', background: 'none', cursor: 'pointer',
            fontSize: 24, padding: 2,
            opacity: star <= value ? 1 : 0.25,
            filter: star <= value ? 'none' : 'grayscale(1)'
          }}
        >
          ⭐
        </button>
      ))}
    </div>
  )
}
