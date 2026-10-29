import type { CSSProperties } from 'react'
import { money, seatLabel } from '../format'
import type { Seat } from '../types'

interface Props {
  seats: Seat[]
  selected: Set<number>
  mine: Set<number>
  disabled: boolean
  onToggle: (seat: Seat) => void
}

const STATUS_TEXT: Record<Seat['status'], string> = { AVAILABLE: 'available', HELD: 'held', BOOKED: 'booked' }

export default function SeatMap({ seats, selected, mine, disabled, onToggle }: Props) {
  const rows = new Map<string, Seat[]>()
  for (const seat of seats) {
    rows.set(seat.row, [...(rows.get(seat.row) ?? []), seat])
  }
  const sortedRows = [...rows.entries()].sort(([a], [b]) => a.localeCompare(b))
  const columns = Math.max(...sortedRows.map(([, rowSeats]) => rowSeats.length), 1)

  // "Rows A–B $75.00 · Rows C–E $45.00", so prices are visible even when the row price column is hidden.
  const rowsByPrice = new Map<number, string[]>()
  for (const [row, rowSeats] of sortedRows) {
    const price = rowSeats[0].priceCents
    rowsByPrice.set(price, [...(rowsByPrice.get(price) ?? []), row])
  }
  const priceKey = [...rowsByPrice.entries()]
    .sort(([a], [b]) => b - a)
    .map(([price, rows]) => `${rows.length > 1 ? `Rows ${rows[0]}–${rows[rows.length - 1]}` : `Row ${rows[0]}`} ${money(price)}`)
    .join(' · ')

  return (
    <div className="seat-map">
      <div className="stage">Stage</div>
      <div className="seat-grid" style={{ '--columns': columns } as CSSProperties}>
        {sortedRows.map(([row, rowSeats]) => {
          const ordered = [...rowSeats].sort((a, b) => a.number - b.number)
          return [
            <span key={`${row}-label`} className="row-label" aria-hidden="true">{row}</span>,
            ...ordered.map((seat) => {
              const isSelected = selected.has(seat.id)
              const isMine = mine.has(seat.id)
              const state = isSelected ? 'selected' : seat.status.toLowerCase()
              return (
                <button
                  key={seat.id}
                  type="button"
                  className={`seat ${state}${isMine ? ' mine' : ''}`}
                  disabled={disabled || seat.status !== 'AVAILABLE'}
                  aria-pressed={isSelected}
                  aria-label={`Row ${seat.row} seat ${seat.number}, ${money(seat.priceCents)}, ${
                    isMine ? 'held by you' : isSelected ? 'selected' : STATUS_TEXT[seat.status]}`}
                  title={`${seatLabel(seat)} · ${money(seat.priceCents)}`}
                  onClick={() => onToggle(seat)}
                >
                  {seat.number}
                </button>
              )
            }),
            <span key={`${row}-price`} className="row-price">{money(ordered[0].priceCents)}</span>,
          ]
        })}
      </div>
      <p className="price-key">{priceKey}</p>
      <ul className="legend" aria-label="Seat colours">
        <li><span className="swatch available" /> Available</li>
        <li><span className="swatch selected" /> Selected</li>
        <li><span className="swatch held" /> Held</li>
        <li><span className="swatch booked" /> Booked</li>
      </ul>
    </div>
  )
}
