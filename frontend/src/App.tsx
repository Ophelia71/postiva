import {
  BarChart3,
  CalendarDays,
  ChevronDown,
  CreditCard,
  KeyRound,
  Link2,
  LogOut,
  PenLine,
  PlusCircle,
  Rocket,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { api } from './lib/api'
import { identifyWebAnalyticsUser, WebAnalyticsTracker } from './lib/webAnalytics'
import { AdminShell } from './components/AdminShell'
import {
  AUTH_EXPIRED_EVENT,
  AUTH_STORAGE_KEY,
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
import { PrivacyPolicyPage } from './pages/PrivacyPolicyPage'
import { ForgotPasswordPage } from './pages/ForgotPasswordPage'
import { ResetPasswordPage } from './pages/ResetPasswordPage'
import { ChangePasswordPage } from './pages/ChangePasswordPage'
import { BillingPage } from './pages/BillingPage'
import { AppIndexRedirect, StartPage } from './pages/StartPage'

const navItems = [
  { to: '/app/schedule', label: 'Lịch đăng', icon: CalendarDays },
  { to: '/app/reports', label: 'Báo cáo', icon: BarChart3 },
  { to: '/app/connections', label: 'Kết nối', icon: Link2 },
  { to: '/app/billing', label: 'Gói & thanh toán', icon: CreditCard },
]

// Billing lives in the account menu on small screens; the create action is the primary thumb target.
const mobileNavItems = [
  { to: '/app/schedule', label: 'Lịch đăng', icon: CalendarDays },
  { to: '/app/create', label: 'Tạo bài', icon: PenLine },
  { to: '/app/reports', label: 'Báo cáo', icon: BarChart3 },
  { to: '/app/connections', label: 'Kết nối', icon: Link2 },
]

type UserResponse = {
  id: string
  fullName: string
  email: string
  role: AuthSession['role']
  mfaSetupRequired: boolean
}

function App() {
  return (
    <>
    <WebAnalyticsTracker />
    <Routes>
      <Route path="/privacy-policy" element={<PrivacyPolicyPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />
      <Route path="*" element={<SessionApp />} />
    </Routes>
    </>
  )
}

function SessionApp() {
  const [session, setSession] = useState<AuthSession | null>(() => getAuthSession())
  const [isSessionReady, setIsSessionReady] = useState(false)
  const [authNotice, setAuthNotice] = useState('')

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
        const response = await api.get<UserResponse>('/auth/me', {
          headers: { Authorization: `Bearer ${storedSession.token}` },
        })
        if (ignore || getAuthSession()?.token !== storedSession.token) return
        const refreshedSession: AuthSession = {
          ...storedSession,
          userId: response.data.id,
          email: response.data.email,
          fullName: response.data.fullName,
          role: response.data.role,
          mfaSetupRequired: response.data.mfaSetupRequired,
        }

        if (!isAuthSession(refreshedSession)) {
          throw new Error('Phiên đăng nhập không hợp lệ')
        }
        saveAuthSession(refreshedSession)
        identifyWebAnalyticsUser()
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
    // Remount all account-scoped data when another tab changes the shared session.
    const handleStorage = (event: StorageEvent) => {
      if (event.storageArea === window.localStorage
        && (event.key === AUTH_STORAGE_KEY || event.key === null)) {
        window.location.reload()
      }
    }
    const handlePageShow = (event: PageTransitionEvent) => {
      if (event.persisted) window.location.reload()
    }
    window.addEventListener(AUTH_EXPIRED_EVENT, handleExpiredSession)
    window.addEventListener('storage', handleStorage)
    window.addEventListener('pageshow', handlePageShow)
    return () => {
      window.removeEventListener(AUTH_EXPIRED_EVENT, handleExpiredSession)
      window.removeEventListener('storage', handleStorage)
      window.removeEventListener('pageshow', handlePageShow)
    }
  }, [])

  function handleAuthenticated(nextSession: AuthSession) {
    saveAuthSession(nextSession)
    identifyWebAnalyticsUser()
    setSession(nextSession)
    setAuthNotice('')
  }

  function handleLogout() {
    clearAuthSession()
    setSession(null)
  }

  function handlePasswordChanged() {
    clearAuthSession()
    setSession(null)
    setAuthNotice('Mật khẩu đã được cập nhật hoặc các phiên đã bị thu hồi. Vui lòng đăng nhập lại.')
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
                    session.role === 'ADMIN'
                      ? <AdminShell session={session} onLogout={handleLogout} onPasswordChanged={handlePasswordChanged} />
                      : <AppShell session={session} onLogout={handleLogout} onPasswordChanged={handlePasswordChanged} />
              ) : (
                <AuthPage onAuthenticated={handleAuthenticated} notice={authNotice} />
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

type ShellProps = { session: AuthSession; onLogout: () => void; onPasswordChanged: () => void }

function AccountMenu({ session, onLogout, placement }: { session: AuthSession; onLogout: () => void; placement: 'above' | 'below' }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const { t } = usePreferences()

  useEffect(() => {
    if (!open) return
    const onPointer = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  const itemClass = 'flex min-h-11 w-full items-center gap-2 rounded-md px-3 text-sm font-semibold text-[var(--app-text)] hover:bg-[var(--soft-bg)]'
  const compact = placement === 'below'

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="menu"
        aria-label={`Tài khoản của ${session.fullName}`}
        className={compact
          ? 'flex h-11 w-11 items-center justify-center rounded-full bg-[#d8ddfb] text-sm font-bold text-[#344260]'
          : 'flex min-h-12 w-full items-center gap-3 rounded-xl px-2 text-left text-sm hover:bg-[var(--soft-bg)]'}
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#d8ddfb] text-xs font-bold text-[#344260]" aria-hidden="true">
          {session.fullName.charAt(0)}
        </span>
        {!compact && (
          <>
            <span className="min-w-0 flex-1">
              <span className="block truncate font-bold">{session.fullName}</span>
              <span className="block truncate text-xs text-[var(--muted-text)]">{session.email}</span>
            </span>
            <ChevronDown size={14} aria-hidden="true" className={`shrink-0 text-[var(--muted-text)] transition ${open ? 'rotate-180' : ''}`} />
          </>
        )}
      </button>

      {open && (
        <div
          role="menu"
          className={`absolute z-30 w-64 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-2 shadow-xl shadow-slate-200/40 ${placement === 'above' ? 'bottom-14 left-0' : 'right-0 top-12'}`}
        >
          <div className="border-b border-[var(--app-border)] px-3 py-3">
            <p className="truncate text-sm font-bold">{session.fullName}</p>
            <p className="mt-1 truncate text-xs text-[var(--muted-text)]">{session.email}</p>
          </div>
          <Link role="menuitem" to="/app/start" onClick={() => setOpen(false)} className={`${itemClass} mt-2`}>
            <Rocket size={16} aria-hidden="true" />
            Hướng dẫn bắt đầu
          </Link>
          <Link role="menuitem" to="/app/billing" onClick={() => setOpen(false)} className={itemClass}>
            <CreditCard size={16} aria-hidden="true" />
            Gói &amp; thanh toán
          </Link>
          <Link role="menuitem" to="/app/security" onClick={() => setOpen(false)} className={itemClass}>
            <KeyRound size={16} aria-hidden="true" />
            Bảo mật tài khoản
          </Link>
          <button role="menuitem" type="button" onClick={onLogout} className={itemClass}>
            <LogOut size={16} aria-hidden="true" />
            {t('Đăng xuất')}
          </button>
        </div>
      )}
    </div>
  )
}

function AppShell({ session, onLogout, onPasswordChanged }: ShellProps) {
  const { t } = usePreferences()
  // The "Tạo bài mới" shortcut is pointless while the user is already on the create page.
  const isCreatePage = useLocation().pathname.startsWith('/app/create')

  return (
    <div className="app-shell min-h-svh bg-[var(--app-bg)] text-[var(--app-text)]">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-[272px] flex-col border-r border-[var(--app-border)] bg-[var(--panel-bg)] p-3 lg:flex">
        <div className="flex h-20 items-center gap-3 px-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-[#6672d8] text-white">
            <PlusCircle size={18} aria-hidden="true" />
          </div>
          <div>
            <p className="text-lg font-bold tracking-tight">Postiva</p>
            <p className="text-xs font-semibold text-[var(--muted-text)]">Quản lý nội dung</p>
          </div>
        </div>

        <nav aria-label="Điều hướng chính" className="flex-1 space-y-2 py-5">
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
                    <Icon size={17} aria-hidden="true" />
                    <span>{item.label}</span>
                    {isActive && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-[#6672d8]" />}
                  </>
                )}
              </NavLink>
            )
          })}
        </nav>

        <div className="border-t border-[var(--app-border)] pt-3">
          <AccountMenu session={session} onLogout={onLogout} placement="above" />
        </div>
      </aside>

      <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-[var(--app-border)] bg-[var(--panel-bg)] px-4 lg:hidden">
        <Link to="/app" className="flex items-center gap-2 text-base font-bold tracking-tight">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#6672d8] text-white">
            <PlusCircle size={16} aria-hidden="true" />
          </span>
          Postiva
        </Link>
        <AccountMenu session={session} onLogout={onLogout} placement="below" />
      </header>

      <main className="pb-24 lg:pb-0 lg:pl-[272px]">
        <div className="mx-auto min-h-svh max-w-[1320px] px-4 py-5 sm:px-6 lg:px-10 lg:py-8">
          {!isCreatePage && (
            <div className="mb-8 hidden items-center justify-end lg:flex">
              <NavLink
                to="/app/create"
                className="flex h-11 items-center justify-center gap-2 rounded-full bg-[#6672d8] px-4 text-sm font-semibold text-white hover:-translate-y-0.5 hover:bg-[#505db8]"
              >
                <PlusCircle size={16} aria-hidden="true" />
                {t('Tạo bài mới')}
              </NavLink>
            </div>
          )}
          <Routes>
            <Route path="/" element={<AppIndexRedirect />} />
            <Route path="/start" element={<StartPage session={session} />} />
            <Route path="/products" element={<Navigate to="/app/reports" replace />} />
            <Route path="/schedule" element={<SchedulePage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/connections" element={<ConnectionsPage />} />
            <Route path="/create" element={<CreatePostPage />} />
            <Route path="/billing" element={<BillingPage />} />
            <Route path="/security" element={<ChangePasswordPage onPasswordChanged={onPasswordChanged} />} />
            <Route path="/admin/*" element={<Navigate to="/app/reports" replace />} />
          </Routes>
        </div>
      </main>

      <nav
        aria-label="Điều hướng chính"
        className="fixed inset-x-0 bottom-0 z-20 grid h-[72px] grid-cols-4 border-t border-[var(--app-border)] bg-[var(--panel-bg)] px-2 pb-[env(safe-area-inset-bottom)] lg:hidden"
      >
        {mobileNavItems.map((item) => {
          const Icon = item.icon
          return (
            <NavLink
              key={item.to}
              to={item.to}
              className={({ isActive }) =>
                `flex min-h-11 flex-col items-center justify-center gap-1 rounded-xl text-xs font-semibold ${isActive ? 'text-[#344260]' : 'text-[var(--muted-text)]'}`
              }
            >
              {({ isActive }) => (
                <>
                  <span className={`flex h-8 w-14 items-center justify-center rounded-full ${isActive ? 'bg-[#d8ddfb]' : ''}`}>
                    <Icon size={18} aria-hidden="true" />
                  </span>
                  {item.label}
                </>
              )}
            </NavLink>
          )
        })}
      </nav>
    </div>
  )
}

export default App
