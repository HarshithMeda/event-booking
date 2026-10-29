import { clearToken, getToken } from './auth'
import type { Booking, EventSummary, Page, Seat, TokenResponse, User } from './types'

/** An error from the API, with the backend's ProblemDetail fields when there are any. */
export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly fieldErrors: Record<string, string>

  constructor(status: number, title: string, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.title = title
    this.fieldErrors = fieldErrors
  }
}

interface ProblemDetail {
  title?: string
  detail?: string
  errors?: Record<string, string>
}

// Called when a request with a token gets 401: the token expired or is no longer valid.
const sessionExpiredListeners = new Set<() => void>()

export function onSessionExpired(listener: () => void): () => void {
  sessionExpiredListeners.add(listener)
  return () => sessionExpiredListeners.delete(listener)
}

async function request<T>(path: string, options: { method?: string; body?: unknown; auth?: boolean } = {}): Promise<T> {
  const { method = 'GET', body, auth = true } = options
  const headers: Record<string, string> = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = auth ? getToken() : null
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    // Same origin: in development Vite proxies /api to the backend; in Docker one server serves both.
    response = await fetch(path, { method, headers, body: body === undefined ? undefined : JSON.stringify(body) })
  } catch {
    throw new ApiError(0, 'Network error', 'Could not reach the server. Check your connection and try again.')
  }

  if (response.status === 401 && token) {
    clearToken()
    sessionExpiredListeners.forEach((listener) => listener())
    throw new ApiError(401, 'Session expired', 'Your session has expired. Please log in again.')
  }
  if (!response.ok) {
    const problem: ProblemDetail = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      problem.title ?? response.statusText,
      problem.detail ?? `Something went wrong (error ${response.status}). Please try again.`,
      problem.errors,
    )
  }
  return (await response.json()) as T
}

export const api = {
  register: (email: string, password: string) =>
    request<User>('/api/auth/register', { method: 'POST', body: { email, password }, auth: false }),
  login: (email: string, password: string) =>
    request<TokenResponse>('/api/auth/login', { method: 'POST', body: { email, password }, auth: false }),
  me: () => request<User>('/api/me'),

  events: (page = 0, size = 50) => request<Page<EventSummary>>(`/api/events?page=${page}&size=${size}`, { auth: false }),
  event: (id: number) => request<EventSummary>(`/api/events/${id}`, { auth: false }),
  seats: (eventId: number) => request<Seat[]>(`/api/events/${eventId}/seats`, { auth: false }),

  bookings: () => request<Booking[]>('/api/bookings'),
  hold: (eventId: number, seatIds: number[]) =>
    request<Booking>('/api/bookings', { method: 'POST', body: { eventId, seatIds } }),
  confirm: (bookingId: number) => request<Booking>(`/api/bookings/${bookingId}/confirm`, { method: 'POST' }),
  cancel: (bookingId: number) => request<Booking>(`/api/bookings/${bookingId}/cancel`, { method: 'POST' }),
}
