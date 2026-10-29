import type { Seat } from './types'

const dollars = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' })
const dateTime = new Intl.DateTimeFormat(undefined, {
  weekday: 'short', month: 'short', day: 'numeric', year: 'numeric', hour: 'numeric', minute: '2-digit',
})

export const money = (cents: number) => dollars.format(cents / 100)
export const when = (iso: string) => dateTime.format(new Date(iso))
export const seatLabel = (seat: Pick<Seat, 'row' | 'number'>) => `${seat.row}${seat.number}`
export const seatLabels = (seats: Pick<Seat, 'row' | 'number'>[]) => seats.map(seatLabel).join(', ')

/** "mm:ss" for a countdown. */
export function countdown(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000))
  const minutes = Math.floor(total / 60)
  const seconds = total % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}
