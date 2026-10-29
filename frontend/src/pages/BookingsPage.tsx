import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import { countdown, money, seatLabels, when } from '../format'
import { useNow } from '../router'
import type { Booking } from '../types'

const STATUS_TEXT: Record<Booking['status'], string> = {
  HELD: 'Held', CONFIRMED: 'Confirmed', CANCELLED: 'Cancelled', EXPIRED: 'Expired',
}

export default function BookingsPage() {
  const [bookings, setBookings] = useState<Booking[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [busyId, setBusyId] = useState<number | null>(null)
  const [confirmCancelId, setConfirmCancelId] = useState<number | null>(null)
  const now = useNow(1000)

  const load = useCallback(() => {
    api.bookings()
      .then(setBookings)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your bookings.'))
  }, [])

  useEffect(load, [load])

  async function act(booking: Booking, action: 'confirm' | 'cancel') {
    setBusyId(booking.id)
    setError(null)
    try {
      await (action === 'confirm' ? api.confirm(booking.id) : api.cancel(booking.id))
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) {
        setError(err instanceof ApiError ? err.message : 'Something went wrong.')
      }
    } finally {
      setBusyId(null)
      setConfirmCancelId(null)
      load()
    }
  }

  return (
    <>
      <h1>My bookings</h1>
      {error && <p className="alert error" role="alert">{error}</p>}
      {!bookings && !error && <p className="muted">Loading bookings…</p>}
      {bookings?.length === 0 && (
        <p className="muted">No bookings yet. <a href="#/">Find an event</a> and choose your seats.</p>
      )}
      <ul className="booking-list">
        {bookings?.map((b) => {
          const remaining = new Date(b.heldUntil).getTime() - now
          const holdActive = b.status === 'HELD' && remaining > 0
          const canCancel = holdActive || b.status === 'CONFIRMED'
          const busy = busyId === b.id
          return (
            <li key={b.id} className="card booking-card">
              <div className="booking-head">
                <a href={`#/events/${b.eventId}`} className="event-name">{b.eventName}</a>
                <span className={`badge ${b.status.toLowerCase()}`}>
                  {b.status === 'HELD' && !holdActive ? 'Hold ended' : STATUS_TEXT[b.status]}
                </span>
              </div>
              <p>Seats <strong>{seatLabels(b.seats)}</strong> · {money(b.totalCents)}</p>
              <p className="muted small">Booked {when(b.createdAt)}</p>
              {holdActive && <p className="timer small">⏳ {countdown(remaining)} left to confirm</p>}
              {(holdActive || canCancel) && (
                <div className="actions">
                  {holdActive && (
                    <button type="button" className="button primary" disabled={busy} onClick={() => act(b, 'confirm')}>
                      Confirm
                    </button>
                  )}
                  {canCancel && confirmCancelId !== b.id && (
                    <button type="button" className="button" disabled={busy} onClick={() => setConfirmCancelId(b.id)}>
                      Cancel booking
                    </button>
                  )}
                  {confirmCancelId === b.id && (
                    <>
                      <span className="small">Cancel and release these seats?</span>
                      <button type="button" className="button danger" disabled={busy} onClick={() => act(b, 'cancel')}>
                        Yes, cancel
                      </button>
                      <button type="button" className="button" disabled={busy} onClick={() => setConfirmCancelId(null)}>
                        Keep it
                      </button>
                    </>
                  )}
                </div>
              )}
            </li>
          )
        })}
      </ul>
    </>
  )
}
