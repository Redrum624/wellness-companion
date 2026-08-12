import { useState } from 'react'

interface Props {
  tags: string[]
  onChange: (tags: string[]) => void
  suggestions?: string[]
  placeholder?: string
  color: string
}

export default function TagInput({ tags, onChange, suggestions = [], placeholder, color }: Props) {
  const [input, setInput] = useState('')

  const addTag = (tag: string) => {
    const trimmed = tag.trim()
    if (trimmed && !tags.includes(trimmed)) {
      onChange([...tags, trimmed])
    }
    setInput('')
  }

  const removeTag = (tag: string) => {
    onChange(tags.filter(t => t !== tag))
  }

  const filteredSuggestions = suggestions.filter(
    s => s.toLowerCase().includes(input.toLowerCase()) && !tags.includes(s)
  )

  return (
    <div>
      {/* Tags */}
      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginBottom: tags.length > 0 ? 8 : 0 }}>
        {tags.map(tag => (
          <span
            key={tag}
            style={{
              display: 'inline-flex', alignItems: 'center', gap: 4,
              padding: '3px 10px', borderRadius: 12,
              background: `${color}25`, color, fontSize: 12
            }}
          >
            {tag}
            <button
              onClick={() => removeTag(tag)}
              style={{ border: 'none', background: 'none', color: `${color}80`, cursor: 'pointer', fontSize: 14, padding: 0, fontFamily: 'inherit' }}
            >
              ×
            </button>
          </span>
        ))}
      </div>

      {/* Input */}
      <input
        value={input}
        onChange={e => setInput(e.target.value)}
        onKeyDown={e => { if (e.key === 'Enter' && input.trim()) { e.preventDefault(); addTag(input) } }}
        placeholder={placeholder || 'Type and press Enter'}
        style={{
          border: 'none', borderRadius: 10, padding: '8px 12px',
          background: 'rgba(255,255,255,0.3)', color,
          fontSize: 13, width: '100%', fontFamily: 'inherit',
          outline: 'none'
        }}
      />

      {/* Suggestions */}
      {input && filteredSuggestions.length > 0 && (
        <div style={{ display: 'flex', gap: 4, marginTop: 4, flexWrap: 'wrap' }}>
          {filteredSuggestions.slice(0, 5).map(s => (
            <button
              key={s}
              onClick={() => addTag(s)}
              style={{
                border: 'none', borderRadius: 10, padding: '3px 10px',
                background: 'rgba(255,255,255,0.3)', color, fontSize: 11,
                cursor: 'pointer', fontFamily: 'inherit'
              }}
            >
              {s}
            </button>
          ))}
        </div>
      )}
    </div>
  )
}
