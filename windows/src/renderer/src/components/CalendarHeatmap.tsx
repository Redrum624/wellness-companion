import { useMemo } from 'react'
import { format, subDays, startOfWeek, addDays, differenceInWeeks } from 'date-fns'

interface Props {
  dateCounts: Map<string, number>
  onDateClick?: (date: string) => void
}

export default function CalendarHeatmap({ dateCounts, onDateClick }: Props) {
  const today = new Date()
  const startDate = subDays(today, 364)
  const weekStart = startOfWeek(startDate, { weekStartsOn: 1 })
  const totalWeeks = differenceInWeeks(today, weekStart) + 1

  const maxCount = useMemo(() => {
    let max = 1
    dateCounts.forEach(v => { if (v > max) max = v })
    return max
  }, [dateCounts])

  const cellSize = 12
  const gap = 2
  const width = totalWeeks * (cellSize + gap) + 20
  const height = 7 * (cellSize + gap) + 20

  const cells: { x: number; y: number; date: string; count: number }[] = []
  for (let w = 0; w < totalWeeks; w++) {
    for (let d = 0; d < 7; d++) {
      const date = addDays(weekStart, w * 7 + d)
      if (date > today) continue
      const dateStr = format(date, 'yyyy-MM-dd')
      cells.push({
        x: w * (cellSize + gap),
        y: d * (cellSize + gap),
        date: dateStr,
        count: dateCounts.get(dateStr) || 0
      })
    }
  }

  function getColor(count: number): string {
    if (count === 0) return 'rgba(255,255,255,0.3)'
    const intensity = Math.min(count / maxCount, 1)
    const alpha = 0.2 + intensity * 0.6
    return `rgba(93, 202, 165, ${alpha})`
  }

  return (
    <div style={{ overflowX: 'auto', padding: '8px 0' }}>
      <svg width={width} height={height}>
        {cells.map(cell => (
          <rect
            key={cell.date}
            x={cell.x}
            y={cell.y}
            width={cellSize}
            height={cellSize}
            rx={3}
            fill={getColor(cell.count)}
            className="heatmap-cell"
            onClick={() => onDateClick?.(cell.date)}
          >
            <title>{`${cell.date}: ${cell.count} entries`}</title>
          </rect>
        ))}
      </svg>
    </div>
  )
}
