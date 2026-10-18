import { ArrowUpRight, RefreshCw, Search, Users } from 'lucide-react'
import { type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { AccountStatusBadge, AdminError, AdminLoading, AdminPagination } from '../../components/AdminUi'
import { Button, Field } from '../../components/UiPrimitives'
import { useAdminResource } from '../../hooks/useAdminResource'
import { adminDate, adminPageNumber, type AdminPage, type AdminUser } from '../../lib/adminApi'

export function AdminUsersPage() {
  const [params, setParams] = useSearchParams()
  const query = params.get('q') ?? ''
  const status = ['ACTIVE', 'LOCKED'].includes(params.get('status') ?? '') ? params.get('status')! : ''
  const page = adminPageNumber(params.get('page'))
  const request = new URLSearchParams({ q: query, page: String(page), size: '20' })
  if (status) request.set('status', status)
  const users = useAdminResource<AdminPage<AdminUser>>(`/admin/users?${request}`)

  function changeParam(name: string, value: string) {
    const next = new URLSearchParams(params)
    if (value) next.set(name, value)
    else next.delete(name)
    if (name !== 'page') next.delete('page')
    setParams(next)
  }

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    changeParam('q', String(new FormData(event.currentTarget).get('q') ?? '').trim())
  }

  function reset() { setParams(new URLSearchParams()) }

  return (
    <section className="space-y-6">
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[var(--accent-strong)]">Quản trị tài khoản</p>
          <h1 className="text-2xl font-bold sm:text-3xl">Người dùng</h1>
          <p className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted-text)]">Tra cứu tài khoản, kiểm tra trạng thái và theo dõi các thay đổi quản trị.</p>
        </div>
        <Button type="button" variant="secondary" onClick={users.reload} disabled={users.loading}>
          <RefreshCw size={16} aria-hidden="true" />Làm mới
        </Button>
      </header>

      <form onSubmit={search} className="grid items-end gap-4 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 sm:grid-cols-[minmax(0,1fr)_auto] xl:grid-cols-[minmax(0,1fr)_220px_auto]">
        <Field key={query} label="Tên hoặc email" name="q" defaultValue={query} maxLength={255} placeholder="Nhập tên hoặc địa chỉ email" autoComplete="off" />
        <div className="text-sm font-semibold">
          <label htmlFor="user-status-filter">Trạng thái</label>
          <select id="user-status-filter" value={status} onChange={(event) => changeParam('status', event.target.value)} className="mt-1.5 min-h-11 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm font-normal">
            <option value="">Tất cả trạng thái</option>
            <option value="ACTIVE">Đang hoạt động</option>
            <option value="LOCKED">Đã khóa</option>
          </select>
        </div>
        <Button type="submit" className="sm:col-span-2 xl:col-span-1"><Search size={16} aria-hidden="true" />Tìm kiếm</Button>
      </form>

      <div className="overflow-hidden rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm" aria-busy={users.loading}>
        <div className="flex min-h-16 flex-wrap items-center justify-between gap-2 border-b border-[var(--app-border)] px-5 py-4">
          <h2 className="flex items-center gap-2 text-sm font-semibold"><Users size={17} aria-hidden="true" />Danh sách người dùng</h2>
          <p className="text-xs text-[var(--muted-text)]" aria-live="polite">{users.data ? `${users.data.totalElements} tài khoản${query || status ? ' phù hợp' : ''}` : 'Đang tra cứu…'}</p>
        </div>
        {users.loading ? <AdminLoading /> : users.error ? <div className="p-5"><AdminError message={users.error} onRetry={users.reload} /></div> : users.data && (
          users.data.items.length === 0 ? <div className="space-y-3 p-10 text-center">
            <p className="font-semibold">{page > 0 ? 'Trang này không có người dùng' : 'Không tìm thấy người dùng'}</p>
            <p className="text-sm text-[var(--muted-text)]">Thử từ khóa khác hoặc xóa bộ lọc để xem lại danh sách.</p>
            <Button type="button" variant="secondary" onClick={reset}>Xem tất cả người dùng</Button>
          </div> : <>
            <div className="hidden md:block">
              <table className="w-full text-left text-sm">
                <caption className="sr-only">Tài khoản người dùng và trạng thái hiện tại</caption>
                <thead className="bg-[var(--soft-bg)] text-xs text-[var(--muted-text)]">
                  <tr><th scope="col" className="px-5 py-3 font-semibold">Người dùng</th><th scope="col" className="px-4 py-3 font-semibold">Trạng thái</th><th scope="col" className="px-4 py-3 font-semibold">Ngày tham gia</th><th scope="col" className="px-5 py-3"><span className="sr-only">Thao tác</span></th></tr>
                </thead>
                <tbody>
                  {users.data.items.map((user) => <tr key={user.id} className="border-t border-[var(--app-border)] hover:bg-[var(--soft-bg)]/40">
                    <td className="max-w-72 px-5 py-4"><p className="font-semibold [overflow-wrap:anywhere]">{user.fullName}</p><p className="mt-1 text-xs text-[var(--muted-text)] [overflow-wrap:anywhere]">{user.email}</p></td>
                    <td className="px-4 py-4"><AccountStatusBadge status={user.status} /></td>
                    <td className="px-4 py-4 text-xs tabular-nums text-[var(--muted-text)]">{adminDate(user.createdAt)}</td>
                    <td className="px-5 py-4 text-right"><UserDetailsLink user={user} search={params.toString()} /></td>
                  </tr>)}
                </tbody>
              </table>
            </div>
            <ul className="divide-y divide-[var(--app-border)] md:hidden">
              {users.data.items.map((user) => <li key={user.id} className="space-y-3 p-5">
                <div><p className="font-semibold [overflow-wrap:anywhere]">{user.fullName}</p><p className="mt-1 text-sm text-[var(--muted-text)] [overflow-wrap:anywhere]">{user.email}</p></div>
                <div className="flex flex-wrap items-center justify-between gap-3"><AccountStatusBadge status={user.status} /><UserDetailsLink user={user} search={params.toString()} /></div>
              </li>)}
            </ul>
            <AdminPagination data={users.data} onPageChange={(next) => changeParam('page', String(next))} />
          </>
        )}
      </div>
    </section>
  )
}

function UserDetailsLink({ user, search }: { user: AdminUser; search: string }) {
  return <Link to={`/app/admin/users/${user.id}${search ? `?${search}` : ''}`} aria-label={`Xem chi tiết ${user.fullName}`} className="inline-flex min-h-11 items-center justify-center gap-1.5 rounded-xl px-3 text-sm font-semibold text-[var(--accent-strong)] hover:bg-[var(--soft-bg)]">
    Chi tiết<ArrowUpRight size={16} aria-hidden="true" />
  </Link>
}
