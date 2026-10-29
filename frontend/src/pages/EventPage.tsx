import { useCallback, useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import HoldPanel from '../components/HoldPanel'
import SeatMap from '../components/SeatMap'
import { money, seatLabels, when } from '../format'
import type { Booking, EventSummary, Seat } from '../types'

const MAX_SEATS = 6
const REFRESH_MS = 10_000

type Message = { kind: 'info' | 'error' | 'success'; text: string }

export default function EventPage({ eventId }: { eventId: number }) {
  const [event, setEvent] = useState<EventSummary | null>(null)
  const [seats, setSeats] = useState<Seat[]>([])
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [hold, setHold] = useState<Booking | null>(null)
  const [message, setMessage] = useState<Message | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  // Reload the seat map, and drop any selected seat that is no longer available.
  const refreshSeats = useCallback(async () => {
    try {
      const fresh = await api.seats(eventId)
      setSeats(fresh)
      const available = new Set(fresh.filter((s) => s.status === 'AVAILABLE').map((s) => s.id))
      setSelected((current) => {
        const kept = [...current].filter((id) => available.has(id))
        return kept.length === current.size ? current : new Set(kept)
      })
    } catch {
      // Keep showing the last seat map; the next refresh will try again.
    }
  }, [eventId])

  // First load: the event, its seats, and any hold the user already has for it (e.g. after a page refresh).
  useEffect(() => {
    Promise.all([api.event(eventId), api.seats(eventId), api.bookings()])
      .then(([ev, seatList, bookings]) => {
        setEvent(ev)
        setSeats(seatList)
        const active = bookings.find((b) =>
          b.eventId === eventId && b.status === 'HELD' && new Date(b.heldUntil).getTime() > Date.now())
        if (active) setHold(active)
      })
      .catch((err) => setLoadError(err instanceof ApiError && err.status === 404
        ? 'This event does not exist.'
        : err instanceof ApiError ? err.message : 'Could not load this event.'))
  }, [eventId])

  // Keep the seat map fresh so other people's bookings show up.
  useEffect(() => {
    const timer = window.setInterval(refreshSeats, REFRESH_MS)
    const onVisible = () => { if (document.visibilityState === 'visible') refreshSeats() }
    document.addEventListener('visibilitychange', onVisible)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', onVisible)
    }
  }, [refreshSeats])

  function toggle(seat: Seat) {
    setMessage(null)
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(seat.id)) {
        next.delete(seat.id)
      } else if (next.size >= MAX_SEATS) {
        setMessage({ kind: 'info', text: `You can choose up to ${MAX_SEATS} seats at a time.` })
      } else {
        next.add(seat.id)
      }
      return next
    })
  }

  async function holdSeats() {
    setBusy(true)
    setMessage(null)
    try {
      const booking = await api.hold(eventId, [...selected])
      setHold(booking)
      setSelected(new Set())
      await refreshSeats()
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        const taken = err.message.split(':')[1]?.trim()
        setMessage({
          kind: 'error',
          text: `Sorry, ${taken ? `seat ${taken} was` : 'one of those seats was'} just taken by someone else. ` +
            'The seat map has been updated, so please pick again.',
        })
        await refreshSeats()
      } else if (!(err instanceof ApiError && err.status === 401)) {
        setMessage({ kind: 'error', text: err instanceof ApiError ? err.message : 'Could not hold the seats.' })
      }
    } finally {
      setBusy(false)
    }
  }

  async function finishHold(action: 'confirm' | 'cancel') {
    if (!hold) return
    setBusy(true)
    setMessage(null)
    try {
      const booking = action === 'confirm' ? await api.confirm(hold.id) : await api.cancel(hold.id)
      // Show the new seat state straight away; the full refresh below follows in the background.
      const changed = new Set(booking.seats.map((s) => s.id))
      const status = action === 'confirm' ? 'BOOKED' : 'AVAILABLE'
      setSeats((current) => current.map((s) => (changed.has(s.id) ? { ...s, status } : s)))
      setHold(null)
      setMessage(action === 'confirm'
        ? { kind: 'success', text: `Booking confirmed! Seats ${seatLabels(booking.seats)} are yours.` }
        : { kind: 'info', text: 'Hold cancelled. The seats are available again.' })
    } catch (err) {
      if (!(err instanceof ApiError && err.status === 401)) {
        setHold(null)
        setMessage({ kind: 'error', text: err instanceof ApiError ? err.message : 'Something went wrong.' })
      }
    } finally {
      await refreshSeats()
      setBusy(false)
    }
  }

  const onExpired = useCallback(() => {
    setHold(null)
    setMessage({ kind: 'info', text: 'Your hold expired, so the seats were released. You can choose seats again.' })
    refreshSeats()
  }, [refreshSeats])

  if (loadError) {
    return (
      <>
        <p className="alert error" role="alert">{loadError}</p>
        <a href="#/">← Back to events</a>
      </>
    )
  }
  if (!event) return <p className="muted">Loading…</p>

  const chosen = seats.filter((s) => selected.has(s.id))
  const total = chosen.reduce((sum, s) => sum + s.priceCents, 0)
  const mine = new Set(hold?.seats.map((s) => s.id) ?? [])

  return (
    <>
      <a href="#/" className="back">← All events</a>
      <h1>{event.name}</h1>
      <p className="muted">📍 {event.venue} · 🗓️ {when(event.startsAt)}</p>

      {message && (
        <p className={`alert ${message.kind}`} role={message.kind === 'error' ? 'alert' : 'status'}>
          {message.text}
          {message.kind === 'success' && <> <a href="#/bookings">See my bookings</a></>}
        </p>
      )}

      {hold && (
        <HoldPanel
          booking={hold}
          busy={busy}
          onConfirm={() => finishHold('confirm')}
          onCancel={() => finishHold('cancel')}
          onExpired={onExpired}
        />
      )}

      <section className="card">
        <SeatMap seats={seats} selected={selected} mine={mine} disabled={busy || !!hold} onToggle={toggle} />
      </section>

      {!hold && (
        <div className="summary-bar">
          <div>
            <strong>{chosen.length === 0 ? 'No seats selected' : `${chosen.length} seat${chosen.length > 1 ? 's' : ''}: ${seatLabels(chosen)}`}</strong>
            <div className="muted">Total {money(total)} · up to {MAX_SEATS} seats</div>
          </div>
          <button type="button" className="button primary" disabled={busy || chosen.length === 0} onClick={holdSeats}>
            {busy ? 'Holding…' : 'Hold seats'}
          </button>
        </div>
      )}
    </>
  )
}
