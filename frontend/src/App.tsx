import {
  BarChart3,
  CalendarDays,
  ChevronDown,
  Link2,
  LogOut,
  PlusCircle,
} from 'lucide-react'
import { useEffect, useState } from 'react'
import { Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { api } from './lib/api'
import {
  AUTH_EXPIRED_EVENT,
  clearAuthSession,
  getAuthSession,
  isAuthSession,
  saveAuthSession,
  type AuthSession,
} from './lib/auth'
import { PreferencesProvider, usePreferences } from './lib/preferences'
import { AuthPage } from './pages/AuthPage'
import { ConnectionsPage } from './pages/ConnectionsPage'
import { CreatePostPage } from './pages/CreatePostPage'
import { HomePage } from './pages/HomePage'
import { ReportsPage } from './pages/ReportsPage'
import { SchedulePage } from './pages/SchedulePage'

const navItems = [
  { to: '/app/schedule', label: 'Lịch đăng', icon: CalendarDays },
  { to: '/app/reports', label: 'Báo cáo', icon: BarChart3 },
  { to: '/app/connections', label: 'Kết nối', icon: Link2 },
]

type UserResponse = {
  id: string
  fullName: string
  email: string
  role: AuthSession['role']
}

function App() {
  const [session, setSession] = useState<AuthSession | null>(() => getAuthSession())
  const [isSessionReady, setIsSessionReady] = useState(false)

  useEffect(() => {
    let ignore = false

    async function restoreSession() {
      const storedSession = getAuthSession()
      if (!storedSession) {
        if (!ignore) {
          setSession(null)
          setIsSessionReady(true)
        }
        return
      }

      try {
        const response = await api.get<UserResponse>('/auth/me')
        const refreshedSession: AuthSession = {
          ...storedSession,
          userId: response.data.id,
          email: response.data.email,
          fullName: response.data.fullName,
          role: response.data.role,
        }

        if (!isAuthSession(refreshedSession)) {
          throw new Error('Phiên đăng nhập không hợp lệ')
        }
        saveAuthSession(refreshedSession)
        if (!ignore) {
          setSession(refreshedSession)
        }
      } catch {
        if (!ignore && !getAuthSession()) {
          setSession(null)
        }
      } finally {
        if (!ignore) {
          setIsSessionReady(true)
        }
      }
    }

    void restoreSession()

    return () => {
      ignore = true
    }
  }, [])

  useEffect(() => {
    const handleExpiredSession = () => setSession(null)
    window.addEventListener(AUTH_EXPIRED_EVENT, handleExpiredSession)
    return () => window.removeEventListener(AUTH_EXPIRED_EVENT, handleExpiredSession)
  }, [])

  function handleAuthenticated(nextSession: AuthSession) {
    saveAuthSession(nextSession)
    setSession(nextSession)
  }

  function handleLogout() {
    clearAuthSession()
    setSession(null)
  }

  return (
    <PreferencesProvider>
      <Routes>
          <Route path="/" element={<HomePage />} />
          <Route
            path="/app/*"
            element={
              !isSessionReady ? (
                <SessionLoading />
              ) : session ? (
                    <AppShell session={session} onLogout={handleLogout} />
              ) : (
                <AuthPage onAuthenticated={handleAuthenticated} />
              )
            }
          />
        </Routes>
    </PreferencesProvider>
  )
}

function SessionLoading() {
  return (
    <main className="flex min-h-svh items-center justify-center bg-[#f2f4fb] px-4 text-sm font-semibold text-[#344260]">
      Đang kiểm tra phiên đăng nhập...
    </main>
  )
}

function AppShell({ session, onLogout }: { session: AuthSession; onLogout: () => void }) {
  const [userMenuOpen, setUserMenuOpen] = useState(false)
  const { t } = usePreferences()
  const profileName = session.fullName
  const profileEmail = session.email

  return (
    <div className="app-shell min-h-svh bg-[var(--app-bg)] text-[var(--app-text)]">
      <aside className="fixed inset-y-0 left-0 z-20 flex w-[272px] flex-col border-r border-[var(--app-border)] bg-[var(--panel-bg)] p-3">
        <div className="flex h-20 items-center gap-3 px-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#6672d8] text-white">
            <PlusCircle size={18} />
          </div>
          <div>
            <h1 className="text-lg font-bold tracking-tight">Postiva</h1>
            <p className="text-xs font-semibold text-[var(--muted-text)]">Quản lý nội dung</p>
          </div>
        </div>

        <nav className="flex-1 space-y-2 py-5">
          {navItems.map((item) => {
            const Icon = item.icon

            return (
              <NavLink
                key={item.to}
                to={item.to}
                className={({ isActive }) =>
                  [
                    'relative flex h-12 items-center gap-3 rounded-xl px-4 text-sm font-semibold transition',
                    isActive
                      ? 'bg-[#d8ddfb] text-[#344260]'
                      : 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]',
                  ].join(' ')
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon size={17} />
                    <span>{item.label}</span>
                    {isActive && (
                      <span className="ml-auto h-1.5 w-1.5 rounded-full bg-[#6672d8]" />
                    )}
                  </>
                )}
              </NavLink>
            )
          })}
        </nav>

        <div className="relative border-t border-[var(--app-border)] pt-3">
          <button
            type="button"
            onClick={() => setUserMenuOpen((value) => !value)}
            className="flex h-12 w-full items-center gap-3 rounded-xl px-2 text-left text-sm hover:bg-[var(--soft-bg)]"
            title={profileName}
          >
            <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#d8ddfb] text-xs font-bold text-[#344260]">
              {profileName.charAt(0)}
            </span>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-bold">{profileName}</span>
              <span className="block truncate text-xs text-[var(--muted-text)]">{profileEmail}</span>
            </span>
            <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-[var(--soft-bg)]">
              <ChevronDown size={12} className={`text-[var(--muted-text)] transition ${userMenuOpen ? 'rotate-180' : ''}`} />
            </span>
          </button>

          {userMenuOpen && (
            <div className="absolute bottom-14 left-0 z-30 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-2 shadow-xl shadow-slate-200/20">
              <div className="border-b border-[var(--app-border)] px-3 py-3">
                <p className="truncate text-sm font-bold">{profileName}</p>
                <p className="mt-1 truncate text-xs text-[var(--muted-text)]">{profileEmail}</p>
              </div>
              <button
                type="button"
                onClick={onLogout}
                className="mt-2 flex h-10 w-full items-center gap-2 rounded-md px-3 text-sm font-semibold text-[var(--app-text)] hover:bg-[var(--soft-bg)]"
              >
                <LogOut size={16} />
                {t('Đăng xuất')}
              </button>
            </div>
          )}
        </div>
      </aside>

      <main className="pl-[272px]">
        <div className="mx-auto min-h-svh max-w-[1320px] px-10 py-8">
          <div className="mb-8 flex items-center justify-end">
            <div className="flex items-center gap-3">
              <NavLink
                to="/app/create"
                className="flex h-11 items-center justify-center gap-2 rounded-full bg-[#6672d8] px-4 text-sm font-semibold text-white hover:-translate-y-0.5 hover:bg-[#505db8]"
              >
                <PlusCircle size={16} />
                {t('Tạo bài mới')}
              </NavLink>
            </div>
          </div>
          <Routes>
            <Route path="/" element={<Navigate to="/app/reports" replace />} />
            <Route path="/products" element={<Navigate to="/app/reports" replace />} />
            <Route path="/schedule" element={<SchedulePage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/connections" element={<ConnectionsPage />} />
            <Route path="/create" element={<CreatePostPage />} />
          </Routes>
        </div>
      </main>
    </div>
  )
}

export default App
