export const AUTH_STORAGE_KEY = 'postiva.auth'
export const AUTH_EXPIRED_EVENT = 'postiva:auth-expired'
/** Fired just before an expired session is cleared, while it still says whose drafts are open. */
export const AUTH_ENDING_EVENT = 'postiva:auth-ending'

export type UserRole = 'USER' | 'ADMIN'

export type AuthSession = {
  token: string
  userId: string
  email: string
  fullName: string
  role: UserRole
  mfaSetupRequired?: boolean
  workspaceId?: string | null
  workspaceName?: string | null
}

export function getAuthSession(): AuthSession | null {
  const rawValue = window.localStorage.getItem(AUTH_STORAGE_KEY)
  if (!rawValue) {
    return null
  }

  try {
    const parsed = JSON.parse(rawValue) as unknown
    if (!isAuthSession(parsed)) {
      clearAuthSession()
      return null
    }
    return parsed
  } catch {
    clearAuthSession()
    return null
  }
}

export function getAuthToken() {
  return getAuthSession()?.token ?? ''
}

export function saveAuthSession(session: AuthSession) {
  if (!isAuthSession(session)) {
    throw new Error('Phiên đăng nhập không hợp lệ')
  }
  window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(session))
}

export function clearAuthSession() {
  window.localStorage.removeItem(AUTH_STORAGE_KEY)
}

export function clearAuthSessionIfTokenMatches(token: string) {
  if (!token || getAuthToken() !== token) return false
  clearAuthSession()
  return true
}

export function isAuthSession(value: unknown): value is AuthSession {
  if (typeof value !== 'object' || value === null) {
    return false
  }

  const session = value as Partial<AuthSession>
  return isNonBlankString(session.token)
    && isNonBlankString(session.userId)
    && isNonBlankString(session.email)
    && isNonBlankString(session.fullName)
    && (session.role === 'USER' || session.role === 'ADMIN')
    && (session.mfaSetupRequired === undefined || typeof session.mfaSetupRequired === 'boolean')
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}
