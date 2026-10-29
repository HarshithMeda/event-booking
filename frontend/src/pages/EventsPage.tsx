import { useEffect, useState } from 'react'
import { api, ApiError } from '../api'
import { when } from '../format'
import type { EventSummary } from '../types'

export default function EventsPage() {
  const [events, setEvents] = useState<EventSummary[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api.events()
      .then((page) => setEvents(page.content.filter((e) => new Date(e.startsAt).getTime() > Date.now())))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load events.'))
  }, [])

  return (
    <>
      <h1>Upcoming events</h1>
      {error && <p className="alert error" role="alert">{error}</p>}
      {!events && !error && <p className="muted">Loading events…</p>}
      {events?.length === 0 && <p className="muted">No upcoming events right now.</p>}
      <ul className="event-list">
        {events?.map((event) => (
          <li key={event.id}>
            <a className="card event-card" href={`#/events/${event.id}`}>
              <span className="event-name">{event.name}</span>
              <span className="muted">📍 {event.venue}</span>
              <span className="muted">🗓️ {when(event.startsAt)}</span>
              <span className="event-cta">Choose seats →</span>
            </a>
          </li>
        ))}
      </ul>
    </>
  )
}
