import {
  BarChart3,
  CalendarClock,
  CalendarDays,
  ChevronDown,
  CreditCard,
  KeyRound,
  Link2,
  LogOut,
  PanelLeftClose,
  PanelLeftOpen,
  PenLine,
  PlusCircle,
  Rocket,
  Store,
} from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { api } from './lib/api'
import { DEMO_SESSION, exitDemo, isDemoActive } from './lib/demo'
import { installDemoApi } from './lib/demoApi'
import { DemoBanner } from './components/DemoBanner'
import { identifyWebAnalyticsUser, WebAnalyticsTracker } from './lib/webAnalytics'
import { AdminShell } from './components/AdminShell'
import { IdleSessionGuard } from './components/IdleSessionGuard'
import { readIdleNotice, saveDrafts, startIdleClock, storedSessionIsIdle } from './lib/idleSession'
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
import { DailyPage } from './pages/DailyPage'
import { ShopProfilePage } from './pages/ShopProfilePage'
import { ShopProfileReminder } from './components/ShopProfileReminder'
import { AppIndexRedirect, StartPage } from './pages/StartPage'
import { type AppOverview, normalizeAppOverview, refreshChannelsOnOpen, useAppDataResource } from './lib/appDataApi'
import { getOnboardingState } from './lib/onboarding'
import { isShopProfileComplete, type ShopProfile } from './lib/shopProfile'
import { readSidebarCollapsed, writeSidebarCollapsed } from './lib/sidebarState'

const navItems = [
  { to: '/app/create', label: 'Tạo bài', icon: PenLine },
  { to: '/app/schedule', label: 'Lịch đăng', icon: CalendarDays },
  { to: '/app/daily', label: 'Daily', icon: CalendarClock },
  { to: '/app/reports', label: 'Báo cáo', icon: BarChart3 },
  { to: '/app/connections', label: 'Kết nối kênh', icon: Link2 },
]
// Account settings (shop profile, plan, security) live in the account menu, not here.

// Billing lives in the account menu on small screens; the create action is the primary thumb target.
// Same order as the sidebar, with shorter labels for five tabs on a narrow phone.
const mobileNavItems = [
  { to: '/app/create', label: 'Tạo bài', icon: PenLine },
  { to: '/app/schedule', label: 'Lịch đăng', icon: CalendarDays },
  { to: '/app/daily', label: 'Daily', icon: CalendarClock },
  { to: '/app/reports', label: 'Báo cáo', icon: BarChart3 },
  { to: '/app/connections', label: 'Kết nối', icon: Link2 },
]

installDemoApi(api)

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
  const [authNotice, setAuthNotice] = useState(() => readIdleNotice())

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
      // A session left open (browser closed, laptop asleep) past the idle limit does not come back.
      if (storedSessionIsIdle(storedSession.role)) {
        clearAuthSession()
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

  // Re-read on every navigation: the demo starts from a link on the home page, outside this component's state.
  useLocation()
  const navigate = useNavigate()
  const inDemo = !session && isDemoActive()

  function handleAuthenticated(nextSession: AuthSession) {
    exitDemo()
    startIdleClock()
    saveAuthSession(nextSession)
    identifyWebAnalyticsUser()
    setSession(nextSession)
    setAuthNotice('')
  }

  function handleLogout() {
    exitDemo()
    // A post being written survives signing out; it is offered back on that screen next time.
    saveDrafts()
    clearAuthSession()
    setSession(null)
  }

  function leaveDemo() {
    exitDemo()
    navigate('/')
  }

  function signUpFromDemo() {
    exitDemo()
    navigate('/app?mode=register')
  }

  function handlePasswordChanged(notice?: string) {
    clearAuthSession()
    setSession(null)
    setAuthNotice(notice ?? 'Vui lòng đăng nhập lại.')
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
                <>
                  <IdleSessionGuard key={session.token} role={session.role} onIdleLogout={handlePasswordChanged} />
                  {session.role === 'ADMIN'
                    ? <AdminShell session={session} onLogout={handleLogout} onPasswordChanged={handlePasswordChanged} />
                    : <AppShell session={session} onLogout={handleLogout} onPasswordChanged={handlePasswordChanged} />}
                </>
              ) : inDemo ? (
                <AppShell session={DEMO_SESSION} onLogout={leaveDemo} onPasswordChanged={leaveDemo} demo={{ onSignUp: signUpFromDemo, onExit: leaveDemo }} />
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
      Đang tải...
    </main>
  )
}

type DemoControls = { onSignUp: () => void; onExit: () => void }
type ShellProps = { session: AuthSession; onLogout: () => void; onPasswordChanged: (notice?: string) => void; demo?: DemoControls }

function AccountMenu({ session, onLogout, placement, demo, collapsed = false }: { session: AuthSession; onLogout: () => void; placement: 'above' | 'below'; demo?: DemoControls; collapsed?: boolean }) {
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
        title={collapsed ? session.fullName : undefined}
        className={compact
          ? 'flex h-11 w-11 items-center justify-center rounded-full bg-[#d8ddfb] text-sm font-bold text-[#344260]'
          : `flex min-h-12 w-full items-center rounded-xl text-left text-sm hover:bg-[var(--soft-bg)] ${collapsed ? 'justify-center' : 'gap-3 px-2'}`}
      >
        <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-[#d8ddfb] text-xs font-bold text-[#344260]" aria-hidden="true">
          {session.fullName.charAt(0)}
        </span>
        {!compact && !collapsed && (
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
          <AccountMenuPanel session={session} itemClass={itemClass} demo={demo} onLogout={onLogout} onNavigate={() => setOpen(false)} logoutLabel={demo ? 'Thoát dùng thử' : t('Đăng xuất')} />
        </div>
      )}
    </div>
  )
}

type UsageSummary = { plan: 'FREE' | 'PRO'; remaining: number; limit: number }

/**
 * Body of the account menu: settings only. Rendered while the menu is open, so the plan and onboarding
 * lookups run when someone actually looks, not on every page.
 */
function AccountMenuPanel({ session, itemClass, demo, onLogout, onNavigate, logoutLabel }: {
  session: AuthSession
  itemClass: string
  demo?: DemoControls
  onLogout: () => void
  onNavigate: () => void
  logoutLabel: string
}) {
  const usage = useAppDataResource<UsageSummary>('/usage')
  const overview = useAppDataResource<AppOverview>('/app-data/overview')
  const profile = useAppDataResource<ShopProfile | null>('/shop-profile')
  // The guide stays until the checklist is done; afterwards it would only take up space.
  const onboardingOpen = !overview.data || !isShopProfileComplete(profile.data)
    || !getOnboardingState(normalizeAppOverview(overview.data)).isSetUp

  return (
    <>
      <div className="border-b border-[var(--app-border)] px-3 py-3">
        <p className="truncate text-sm font-bold">{session.fullName}</p>
        <p className="mt-1 truncate text-xs text-[var(--muted-text)]">{session.email}</p>
        {usage.data && (
          <Link
            to="/app/billing"
            onClick={onNavigate}
            className="mt-3 flex items-center justify-between gap-2 rounded-lg bg-[var(--soft-bg)] px-3 py-2 text-xs font-semibold hover:bg-[#e4e7fb]"
          >
            <span className={`rounded-full px-2 py-0.5 font-bold ${usage.data.plan === 'PRO' ? 'bg-[#505db8] text-white' : 'bg-white text-[#344260]'}`}>
              {usage.data.plan === 'PRO' ? 'Pro' : 'Free'}
            </span>
            <span className="text-[var(--muted-text)]">Còn <strong className="text-[var(--app-text)]">{usage.data.remaining}</strong>/{usage.data.limit} lượt AI</span>
          </Link>
        )}
      </div>

      <div className="py-2">
        <Link role="menuitem" to="/app/shop-profile" onClick={onNavigate} className={itemClass}>
          <Store size={16} aria-hidden="true" />
          Hồ sơ shop
        </Link>
        <Link role="menuitem" to="/app/billing" onClick={onNavigate} className={itemClass}>
          <CreditCard size={16} aria-hidden="true" />
          Gói &amp; thanh toán
        </Link>
        {demo ? (
          <button role="menuitem" type="button" onClick={demo.onSignUp} className={itemClass}>
            <KeyRound size={16} aria-hidden="true" />
            Tạo tài khoản miễn phí
          </button>
        ) : (
          <Link role="menuitem" to="/app/security" onClick={onNavigate} className={itemClass}>
            <KeyRound size={16} aria-hidden="true" />
            Bảo mật tài khoản
          </Link>
        )}
        {/* The phone bottom bar has no room for Daily, so it is reachable from here on small screens. */}
        <Link role="menuitem" to="/app/daily" onClick={onNavigate} className={`${itemClass} lg:hidden`}>
          <CalendarClock size={16} aria-hidden="true" />
          Daily
        </Link>
        {onboardingOpen && (
          <Link role="menuitem" to="/app/start" onClick={onNavigate} className={itemClass}>
            <Rocket size={16} aria-hidden="true" />
            Hướng dẫn bắt đầu
          </Link>
        )}
      </div>

      <div className="border-t border-[var(--app-border)] pt-2">
        <button role="menuitem" type="button" onClick={onLogout} className={`${itemClass} hover:text-rose-700`}>
          <LogOut size={16} aria-hidden="true" />
          {logoutLabel}
        </button>
      </div>
    </>
  )
}

function AppShell({ session, onLogout, onPasswordChanged, demo }: ShellProps) {
  const pathname = useLocation().pathname
  // The checklist and the profile page already ask for the profile themselves.
  const isOnboardingPage = pathname.startsWith('/app/start') || pathname.startsWith('/app/shop-profile')
  const [collapsed, setCollapsed] = useState(readSidebarCollapsed)
  // Each visit brings in new posts, likes and views from the connected channels; the demo has none to read.
  const inDemo = Boolean(demo)
  useEffect(() => {
    if (!inDemo) refreshChannelsOnOpen()
  }, [inDemo])
  const toggleSidebar = () => {
    setCollapsed((value) => {
      writeSidebarCollapsed(!value)
      return !value
    })
  }

  // Onboarding is a focused, full-screen flow: no sidebar, no bottom bar, no "Tạo bài mới".
  if (pathname.startsWith('/app/start')) {
    return (
      <div className="app-shell">
        <StartPage
          session={session}
          onLogout={onLogout}
          banner={demo && <DemoBanner onSignUp={demo.onSignUp} onExit={demo.onExit} />}
        />
      </div>
    )
  }

  return (
    <div className="app-shell min-h-svh bg-[var(--app-bg)] text-[var(--app-text)]">
      <aside className={`fixed inset-y-0 left-0 z-20 hidden flex-col border-r border-[var(--app-border)] bg-[var(--panel-bg)] p-3 transition-[width] duration-200 lg:flex ${collapsed ? 'w-[76px]' : 'w-[272px]'}`}>
        <div className={`flex h-20 items-center ${collapsed ? 'justify-center' : 'gap-3 pl-3'}`}>
          {!collapsed && (
            <>
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#6672d8] text-white">
                <PlusCircle size={18} aria-hidden="true" />
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-lg font-bold tracking-tight">Postiva</p>
                <p className="truncate text-xs font-semibold text-[var(--muted-text)]">Quản lý nội dung</p>
              </div>
            </>
          )}
          <button
            type="button"
            onClick={toggleSidebar}
            aria-label={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
            title={collapsed ? 'Mở rộng menu' : 'Thu gọn menu'}
            className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]"
          >
            {collapsed ? <PanelLeftOpen size={18} aria-hidden="true" /> : <PanelLeftClose size={18} aria-hidden="true" />}
          </button>
        </div>

        <nav aria-label="Điều hướng chính" className="flex-1 space-y-2 py-5">
          {navItems.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                title={collapsed ? item.label : undefined}
                className={({ isActive }) =>
                  [
                    'relative flex h-12 items-center rounded-xl text-sm font-semibold transition',
                    collapsed ? 'justify-center' : 'gap-3 px-4',
                    isActive
                      ? 'bg-[#d8ddfb] text-[#344260]'
                      : 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]',
                  ].join(' ')
                }
              >
                {({ isActive }) => (
                  <>
                    <Icon size={17} aria-hidden="true" />
                    <span className={collapsed ? 'sr-only' : ''}>{item.label}</span>
                    {isActive && !collapsed && <span className="ml-auto h-1.5 w-1.5 rounded-full bg-[#6672d8]" />}
                  </>
                )}
              </NavLink>
            )
          })}
        </nav>

        <div className="border-t border-[var(--app-border)] pt-3">
          <AccountMenu session={session} onLogout={onLogout} placement="above" demo={demo} collapsed={collapsed} />
        </div>
      </aside>

      <header className="sticky top-0 z-20 flex h-14 items-center justify-between border-b border-[var(--app-border)] bg-[var(--panel-bg)] px-4 lg:hidden">
        <Link to="/app" className="flex items-center gap-2 text-base font-bold tracking-tight">
          <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#6672d8] text-white">
            <PlusCircle size={16} aria-hidden="true" />
          </span>
          Postiva
        </Link>
        <AccountMenu session={session} onLogout={onLogout} placement="below" demo={demo} />
      </header>

      <main className={`pb-24 transition-[padding] duration-200 lg:pb-0 ${collapsed ? 'lg:pl-[76px]' : 'lg:pl-[272px]'}`}>
        {/* Wide enough to use the room freed by collapsing the sidebar, capped only on very large screens. */}
        <div className="mx-auto min-h-svh max-w-[1920px] px-4 py-5 sm:px-6 lg:px-10 lg:py-8">
          {demo && <DemoBanner onSignUp={demo.onSignUp} onExit={demo.onExit} />}
          {!isOnboardingPage && <ShopProfileReminder />}
          <Routes>
            <Route path="/" element={<AppIndexRedirect />} />
            <Route path="/products" element={<Navigate to="/app/reports" replace />} />
            <Route path="/schedule" element={<SchedulePage />} />
            <Route path="/daily" element={<DailyPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/connections" element={<ConnectionsPage />} />
            <Route path="/create" element={<CreatePostPage />} />
            <Route path="/billing" element={<BillingPage />} />
            <Route path="/shop-profile" element={<ShopProfilePage />} />
            <Route path="/security" element={<ChangePasswordPage onPasswordChanged={onPasswordChanged} />} />
            <Route path="/admin/*" element={<Navigate to="/app/reports" replace />} />
          </Routes>
        </div>
      </main>

      <nav
        aria-label="Điều hướng chính"
        className="fixed inset-x-0 bottom-0 z-20 grid h-[72px] grid-cols-5 border-t border-[var(--app-border)] bg-[var(--panel-bg)] px-2 pb-[env(safe-area-inset-bottom)] lg:hidden"
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
