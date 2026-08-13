import { Star } from 'lucide-react'

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
          className="star-btn"
          style={{
            border: 'none', cursor: 'pointer',
            padding: 2, color
          }}
        >
          <Star size={18} strokeWidth={2} fill={star <= value ? 'currentColor' : 'none'} />
        </button>
      ))}
    </div>
  )
}
