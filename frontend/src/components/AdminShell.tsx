import { BarChart3, CircleDollarSign, KeyRound, LogOut, ShieldCheck, Users } from 'lucide-react'
import { useEffect } from 'react'
import { Link, Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom'
import { type AuthSession } from '../lib/auth'
import { AdminUsersPage } from '../pages/admin/AdminUsersPage'
import { AdminUserDetailsPage } from '../pages/admin/AdminUserDetailsPage'
import { AdminSecurityPage } from '../pages/admin/AdminSecurityPage'
import { AdminAiBudgetPage } from '../pages/admin/AdminAiBudgetPage'
import { AdminBillingPage } from '../pages/admin/AdminBillingPage'
import { AdminWebAnalyticsPage } from '../pages/admin/AdminWebAnalyticsPage'
import { Button } from './UiPrimitives'

export function AdminShell({ session, onLogout, onPasswordChanged }: { session: AuthSession; onLogout: () => void; onPasswordChanged: () => void }) {
  const location = useLocation()
  useEffect(() => { document.getElementById('admin-main')?.focus() }, [location.pathname])

  return (
    <div className="app-shell admin-shell min-h-svh bg-[var(--app-bg)] text-[var(--app-text)]">
      <a href="#admin-main" className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-3 focus:z-50 focus:rounded-lg focus:bg-white focus:p-3">Đến nội dung chính</a>
      <aside className="fixed inset-y-0 left-0 hidden w-64 flex-col border-r border-[var(--app-border)] bg-[var(--panel-bg)] p-5 lg:flex">
        <Link to="/app/admin/users" className="flex items-center gap-3 py-4">
          <span className="flex h-11 w-11 items-center justify-center rounded-2xl bg-[var(--accent)] text-white"><ShieldCheck size={22} aria-hidden="true" /></span>
          <span><span className="block text-lg font-bold">Postiva</span><span className="text-xs text-[var(--muted-text)]">Trang quản trị</span></span>
        </Link>
        <nav aria-label="Quản trị" className="mt-7 flex-1">
          <NavLink to="/app/admin/users" className="flex min-h-12 items-center gap-3 rounded-xl bg-[var(--soft-bg)] px-4 text-sm font-semibold text-[var(--accent-strong)]">
            <Users size={18} aria-hidden="true" />Người dùng
          </NavLink>
          <NavLink to="/app/admin/analytics" className={({ isActive }) => `mt-2 flex min-h-12 items-center gap-3 rounded-xl px-4 text-sm font-semibold ${isActive ? 'bg-[var(--soft-bg)] text-[var(--accent-strong)]' : 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]'}`}>
            <BarChart3 size={18} aria-hidden="true" />Phân tích web
          </NavLink>
          <NavLink to="/app/admin/ai-budget" className={({ isActive }) => `mt-2 flex min-h-12 items-center gap-3 rounded-xl px-4 text-sm font-semibold ${isActive ? 'bg-[var(--soft-bg)] text-[var(--accent-strong)]' : 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]'}`}>
            <CircleDollarSign size={18} aria-hidden="true" />AI & ngân sách
          </NavLink>
          <NavLink to="/app/admin/billing" className={({ isActive }) => `mt-2 flex min-h-12 items-center gap-3 rounded-xl px-4 text-sm font-semibold ${isActive ? 'bg-[var(--soft-bg)] text-[var(--accent-strong)]' : 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]'}`}>
            <CircleDollarSign size={18} aria-hidden="true" />Thanh toán
          </NavLink>
          <NavLink to="/app/admin/security" className="mt-2 flex min-h-12 items-center gap-3 rounded-xl px-4 text-sm font-semibold text-[var(--accent-strong)] hover:bg-[var(--soft-bg)]">
            <KeyRound size={18} aria-hidden="true" />Bảo mật
          </NavLink>
        </nav>
        <div className="border-t border-[var(--app-border)] pt-5">
          <p className="break-words text-sm font-semibold">{session.fullName}</p>
          <p className="mt-1 break-words text-xs text-[var(--muted-text)]">{session.email}</p>
          <p className="mt-3 flex items-center gap-1.5 text-xs font-semibold text-[var(--accent-strong)]"><ShieldCheck size={14} aria-hidden="true" />Quản trị viên</p>
          <Button variant="secondary" type="button" className="mt-5 w-full" onClick={onLogout}><LogOut size={16} aria-hidden="true" />Đăng xuất</Button>
        </div>
      </aside>
      <header className="flex flex-wrap items-center justify-between gap-3 border-b border-[var(--app-border)] bg-[var(--panel-bg)] px-4 py-3 lg:hidden">
        <Link to="/app/admin/users" className="flex items-center gap-2 font-bold"><ShieldCheck size={22} aria-hidden="true" />Postiva <span className="text-xs font-medium text-[var(--muted-text)]">Quản trị</span></Link>
        <Link to="/app/admin/security" className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-[var(--accent-strong)]"><KeyRound size={16} aria-hidden="true" />Bảo mật</Link>
        <Link to="/app/admin/ai-budget" className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-[var(--accent-strong)]"><CircleDollarSign size={16} aria-hidden="true" />AI</Link>
        <Link to="/app/admin/billing" className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-[var(--accent-strong)]"><CircleDollarSign size={16} aria-hidden="true" />Thanh toán</Link>
        <Link to="/app/admin/analytics" className="inline-flex min-h-11 items-center gap-2 text-sm font-semibold text-[var(--accent-strong)]"><BarChart3 size={16} aria-hidden="true" />Phân tích</Link>
        <Button variant="secondary" type="button" onClick={onLogout}><LogOut size={16} aria-hidden="true" />Đăng xuất</Button>
      </header>
      <main id="admin-main" tabIndex={-1} className="min-w-0 outline-none lg:ml-64">
        <div className="mx-auto max-w-[1400px] px-4 py-7 sm:px-7 lg:px-10 lg:py-10">
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
