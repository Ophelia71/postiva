import { BarChart3, CircleDollarSign, KeyRound, LogOut, PanelLeftClose, PanelLeftOpen, ShieldCheck, Sparkles, Users } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { type AuthSession } from '../lib/auth'
import { readSidebarCollapsed, writeSidebarCollapsed } from '../lib/sidebarState'
import { AdminUsersPage } from '../pages/admin/AdminUsersPage'
import { AdminUserDetailsPage } from '../pages/admin/AdminUserDetailsPage'
import { AdminSecurityPage } from '../pages/admin/AdminSecurityPage'
import { AdminAiBudgetPage } from '../pages/admin/AdminAiBudgetPage'
import { AdminBillingPage } from '../pages/admin/AdminBillingPage'
import { AdminWebAnalyticsPage } from '../pages/admin/AdminWebAnalyticsPage'

const NAV_ITEMS = [
  { to: '/app/admin/users', label: 'Người dùng', short: 'Người dùng', icon: Users },
  { to: '/app/admin/analytics', label: 'Phân tích web', short: 'Phân tích', icon: BarChart3 },
  { to: '/app/admin/ai-budget', label: 'AI & ngân sách', short: 'AI', icon: Sparkles },
  { to: '/app/admin/billing', label: 'Thanh toán', short: 'Thanh toán', icon: CircleDollarSign },
  { to: '/app/admin/security', label: 'Bảo mật', short: 'Bảo mật', icon: KeyRound },
]

export function AdminShell({ session, onLogout, onPasswordChanged }: { session: AuthSession; onLogout: () => void; onPasswordChanged: () => void }) {
  const location = useLocation()
  const [collapsed, setCollapsed] = useState(readSidebarCollapsed)
  useEffect(() => { document.getElementById('admin-main')?.focus() }, [location.pathname])
  const toggleSidebar = () => {
    setCollapsed((value) => {
      writeSidebarCollapsed(!value)
      return !value
    })
  }

  return (
    <div className="app-shell admin-shell min-h-svh bg-[var(--app-bg)] text-[var(--app-text)]">
      <a href="#admin-main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-white focus:p-3">Đến nội dung chính</a>
      <aside className={`fixed inset-y-0 left-0 z-20 hidden flex-col border-r border-[var(--app-border)] bg-[var(--panel-bg)] p-3 transition-[width] duration-200 lg:flex ${collapsed ? 'w-[76px]' : 'w-[272px]'}`}>
        <div className={`flex h-20 items-center ${collapsed ? 'justify-center' : 'gap-3 pl-3'}`}>
          {!collapsed && (
            <Link to="/app/admin/users" className="flex min-w-0 flex-1 items-center gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#6672d8] text-white"><ShieldCheck size={18} aria-hidden="true" /></span>
              <span className="min-w-0"><span className="block text-lg font-bold tracking-tight">Postiva</span><span className="block truncate text-xs font-semibold text-[var(--muted-text)]">Trang quản trị</span></span>
            </Link>
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
        <nav aria-label="Quản trị" className="flex-1 space-y-2 py-5">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                title={collapsed ? item.label : undefined}
                className={({ isActive }) => [
                  'flex h-12 items-center rounded-xl text-sm font-semibold transition',
                  collapsed ? 'justify-center' : 'gap-3 px-4',
                  isActive ? 'bg-[#d8ddfb] text-[#344260]' : 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]',
                ].join(' ')}
              >
                <Icon size={17} aria-hidden="true" />
                <span className={collapsed ? 'sr-only' : ''}>{item.label}</span>
              </NavLink>
            )
          })}
        </nav>
        <div className="border-t border-[var(--app-border)] pt-3">
          {!collapsed && (
            <div className="px-2 pb-3">
              <p className="truncate text-sm font-semibold">{session.fullName}</p>
              <p className="truncate text-xs text-[var(--muted-text)]">{session.email}</p>
            </div>
          )}
          <button
            type="button"
            onClick={onLogout}
            title={collapsed ? 'Đăng xuất' : undefined}
            className={`flex h-11 w-full items-center rounded-lg border border-[var(--app-border)] text-sm font-semibold hover:bg-[var(--soft-bg)] ${collapsed ? 'justify-center' : 'justify-center gap-2'}`}
          >
            <LogOut size={16} aria-hidden="true" /><span className={collapsed ? 'sr-only' : ''}>Đăng xuất</span>
          </button>
        </div>
      </aside>
      <header className="sticky top-0 z-20 border-b border-[var(--app-border)] bg-[var(--panel-bg)] lg:hidden">
        <div className="flex h-14 items-center justify-between px-4">
          <Link to="/app/admin/users" className="flex items-center gap-2 font-bold"><ShieldCheck size={20} aria-hidden="true" />Postiva <span className="text-xs font-medium text-[var(--muted-text)]">Quản trị</span></Link>
          <button type="button" onClick={onLogout} className="inline-flex h-10 items-center gap-2 rounded-lg border border-[var(--app-border)] px-3 text-sm font-semibold"><LogOut size={16} aria-hidden="true" />Đăng xuất</button>
        </div>
        {/* One scrollable row instead of links wrapping over several lines on a phone. */}
        <nav aria-label="Quản trị" className="flex gap-1 overflow-x-auto px-2 pb-2">
          {NAV_ITEMS.map((item) => (
            <NavLink key={item.to} to={item.to} className={({ isActive }) => `shrink-0 rounded-lg px-3 py-2 text-sm font-semibold ${isActive ? 'bg-[#d8ddfb] text-[#344260]' : 'text-[var(--muted-text)]'}`}>{item.short}</NavLink>
          ))}
        </nav>
      </header>
      <main id="admin-main" tabIndex={-1} className={`min-w-0 outline-none transition-[padding] duration-200 ${collapsed ? 'lg:pl-[76px]' : 'lg:pl-[272px]'}`}>
        <div className="mx-auto max-w-[1920px] px-4 py-6 sm:px-6 lg:px-10 lg:py-8">
          <Routes>
            <Route path="/admin/users" element={session.mfaSetupRequired ? <Navigate to="/app/admin/security" replace /> : <AdminUsersPage />} />
            <Route path="/admin/users/:userId" element={session.mfaSetupRequired ? <Navigate to="/app/admin/security" replace /> : <AdminUserDetailsPage key={location.pathname} />} />
            <Route path="/admin/ai-budget" element={session.mfaSetupRequired ? <Navigate to="/app/admin/security" replace /> : <AdminAiBudgetPage />} />
            <Route path="/admin/billing" element={session.mfaSetupRequired ? <Navigate to="/app/admin/security" replace /> : <AdminBillingPage />} />
            <Route path="/admin/analytics" element={session.mfaSetupRequired ? <Navigate to="/app/admin/security" replace /> : <AdminWebAnalyticsPage />} />
            <Route path="/admin/security" element={<AdminSecurityPage onPasswordChanged={onPasswordChanged} />} />
            <Route path="*" element={<Navigate to={session.mfaSetupRequired ? '/app/admin/security' : '/app/admin/users'} replace />} />
          </Routes>
        </div>
      </main>
    </div>
  )
}
