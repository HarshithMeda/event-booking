import { useEffect, useState } from 'react'

// Hash routes (#/events/3) are handled in the browser, so the server only ever serves index.html at "/".
export type Route =
  | { name: 'events' }
  | { name: 'event'; id: number }
  | { name: 'bookings' }

export function parseRoute(hash: string): Route {
  const path = hash.replace(/^#/, '')
  const event = path.match(/^\/events\/(\d+)$/)
  if (event) return { name: 'event', id: Number(event[1]) }
  if (path === '/bookings') return { name: 'bookings' }
  return { name: 'events' }
}

export function navigate(to: string): void {
  window.location.hash = to
}

export function useRoute(): Route {
  const [route, setRoute] = useState(() => parseRoute(window.location.hash))
  useEffect(() => {
    const onChange = () => {
      setRoute(parseRoute(window.location.hash))
      window.scrollTo(0, 0)
    }
    window.addEventListener('hashchange', onChange)
    return () => window.removeEventListener('hashchange', onChange)
  }, [])
  return route
}

/** Re-renders every `intervalMs` and returns the current time (for countdowns). */
export function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), intervalMs)
    return () => window.clearInterval(timer)
  }, [intervalMs])
  return now
}
