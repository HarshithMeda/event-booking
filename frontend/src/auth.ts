// The access token is kept in localStorage for now. It is not a secret of the app:
// it is the user's own login, and the backend verifies it on every request.

const TOKEN_KEY = 'eventBooking.token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function saveToken(token: string): void {
  try {
    localStorage.setItem(TOKEN_KEY, token)
  } catch {
    // Storage blocked (private mode): the user stays logged in until the page reloads.
  }
}

export function clearToken(): void {
  try {
    localStorage.removeItem(TOKEN_KEY)
  } catch {
    // Nothing to clear.
  }
}

/**
 * Reads the token's claims for display and for the logout timer only.
 * This does NOT verify the token; the backend does that on every request.
 */
export function tokenClaims(token: string): { sub?: string; exp?: number } | null {
  try {
    const payload = token.split('.')[1]
    return JSON.parse(atob(payload.replace(/-/g, '+').replace(/_/g, '/')))
  } catch {
    return null
  }
}

/** Milliseconds until the token expires (0 or less means it already has). */
export function msUntilExpiry(token: string): number {
  const exp = tokenClaims(token)?.exp
  return exp ? exp * 1000 - Date.now() : 0
}
