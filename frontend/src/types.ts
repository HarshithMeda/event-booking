// Shapes of the JSON the backend sends (see the Java DTOs in src/main/java/.../dto).

export type SeatStatus = 'AVAILABLE' | 'HELD' | 'BOOKED'
export type BookingStatus = 'HELD' | 'CONFIRMED' | 'CANCELLED' | 'EXPIRED'

export interface EventSummary {
  id: number
  name: string
  venue: string
  startsAt: string
}

export interface Seat {
  id: number
  row: string
  number: number
  priceCents: number
  status: SeatStatus
}

export interface Booking {
  id: number
  eventId: number
  eventName: string
  status: BookingStatus
  totalCents: number
  heldUntil: string
  createdAt: string
  seats: Seat[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TokenResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
}

export interface User {
  id: number
  email: string
}
