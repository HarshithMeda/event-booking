import { useCallback, useEffect, useState } from 'react'
import { onSessionExpired } from './api'
import { clearToken, getToken, msUntilExpiry, saveToken, tokenClaims } from './auth'
import { navigate, useRoute } from './router'
import BookingsPage from './pages/BookingsPage'
import EventPage from './pages/EventPage'
import EventsPage from './pages/EventsPage'
import LoginPage from './pages/LoginPage'

const EXPIRED_MESSAGE = 'Your session has expired. Please log in again.'

function initialToken(): string | null {
  const token = getToken()
  if (token && msUntilExpiry(token) <= 0) {
    clearToken()
    return null
  }
  return token
}

export default function App() {
  const route = useRoute()
  const [token, setToken] = useState<string | null>(initialToken)
  const [notice, setNotice] = useState<string | null>(null)

  const logOut = useCallback((message: string | null) => {
    clearToken()
    setToken(null)
    setNotice(message)
  }, [])

  // Any API call that gets 401 with a token sends the user back to the login screen.
  useEffect(() => onSessionExpired(() => logOut(EXPIRED_MESSAGE)), [logOut])

  // Also log out when the token's 60 minutes run out, even if the user isn't clicking anything.
  useEffect(() => {
    if (!token) return
    const timer = window.setTimeout(() => logOut(EXPIRED_MESSAGE), Math.max(0, msUntilExpiry(token)))
    return () => window.clearTimeout(timer)
  }, [token, logOut])

  if (!token) {
    return (
      <LoginPage
        notice={notice}
        onLoggedIn={(newToken) => {
          saveToken(newToken)
          setToken(newToken)
          setNotice(null)
        }}
      />
    )
  }

  const email = tokenClaims(token)?.sub
  return (
    <>
      <header className="topbar">
        <div className="topbar-inner">
          <a href="#/" className="brand">🎟️ Event Booking</a>
          <nav className="nav">
            <a href="#/" className={route.name !== 'bookings' ? 'active' : ''}>Events</a>
            <a href="#/bookings" className={route.name === 'bookings' ? 'active' : ''}>My bookings</a>
            <button
              type="button"
              className="link-button"
              title={email ? `Logged in as ${email}` : undefined}
              onClick={() => {
                logOut(null)
                navigate('/')
              }}
            >
              Log out
            </button>
          </nav>
        </div>
      </header>
      <main className="container">
        {route.name === 'events' && <EventsPage />}
        {route.name === 'event' && <EventPage key={route.id} eventId={route.id} />}
        {route.name === 'bookings' && <BookingsPage />}
      </main>
    </>
  )
}
