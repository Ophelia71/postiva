const AUTH_STORAGE_KEY = 'postiva.auth'
export const AUTH_EXPIRED_EVENT = 'postiva:auth-expired'

export type UserRole = 'USER' | 'ADMIN'

export type AuthSession = {
  token: string
  userId: string
  email: string
  fullName: string
  role: UserRole
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
}

function isNonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0
}
