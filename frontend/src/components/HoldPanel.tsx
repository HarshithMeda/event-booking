import { useEffect, useRef } from 'react'
import { countdown, money, seatLabels } from '../format'
import { useNow } from '../router'
import type { Booking } from '../types'

interface Props {
  booking: Booking
  busy: boolean
  onConfirm: () => void
  onCancel: () => void
  onExpired: () => void
}

export default function HoldPanel({ booking, busy, onConfirm, onCancel, onExpired }: Props) {
  const now = useNow(1000)
  const remaining = new Date(booking.heldUntil).getTime() - now
  const expired = remaining <= 0

  // Tell the page once when time runs out.
  const reported = useRef(false)
  useEffect(() => {
    if (expired && !reported.current) {
      reported.current = true
      onExpired()
    }
  }, [expired, onExpired])

  return (
    <section className={`card hold-panel${remaining < 60_000 ? ' urgent' : ''}`} aria-live="polite">
      <h2>Seats held for you</h2>
      <p>
        <strong>{seatLabels(booking.seats)}</strong> · {money(booking.totalCents)}
      </p>
      <p className="timer" aria-label={`Time left: ${countdown(remaining)}`}>
        ⏳ <span>{countdown(remaining)}</span> left to confirm
      </p>
      <div className="actions">
        <button type="button" className="button primary" disabled={busy || expired} onClick={onConfirm}>
          Confirm booking
        </button>
        <button type="button" className="button" disabled={busy || expired} onClick={onCancel}>
          Cancel hold
        </button>
      </div>
    </section>
  )
}
