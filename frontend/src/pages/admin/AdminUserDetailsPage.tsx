import { ArrowLeft, History, LockKeyhole, RefreshCw, UnlockKeyhole } from 'lucide-react'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import { AccountStatusBadge, AdminError, AdminLoading, AdminPagination } from '../../components/AdminUi'
import { Button } from '../../components/UiPrimitives'
import { useAdminResource } from '../../hooks/useAdminResource'
import { adminDate, adminPageNumber, type AdminAudit, type AdminPage, type AdminUser } from '../../lib/adminApi'
import { api } from '../../lib/api'
import { getErrorMessage } from '../../lib/errors'
import { AdminUserPlanSection } from './AdminUserPlanSection'

export function AdminUserDetailsPage() {
  const { userId } = useParams()
  const [params, setParams] = useSearchParams()
  const auditPage = adminPageNumber(params.get('auditPage'))
  const detail = useAdminResource<AdminUser>(`/admin/users/${userId}`)
  const audit = useAdminResource<AdminPage<AdminAudit>>(`/admin/users/${userId}/audit?page=${auditPage}&size=10`)
  const [action, setAction] = useState<'lock' | 'unlock' | null>(null)
  const [notice, setNotice] = useState('')
  const backParams = new URLSearchParams(params)
  backParams.delete('auditPage')

  function changeAuditPage(page: number) {
    const next = new URLSearchParams(params)
    if (page) next.set('auditPage', String(page))
    else next.delete('auditPage')
    setParams(next)
  }

  function changed(updated: AdminUser) {
    setAction(null)
    setNotice(updated.status === 'LOCKED' ? 'Đã khóa tài khoản và hủy các lịch chưa gửi.' : 'Đã mở khóa tài khoản. Các lịch bị hủy không tự đăng lại.')
    detail.reload()
    changeAuditPage(0)
    audit.reload()
  }

  const user = detail.data
  return <section className="space-y-6">
    <Link to={`/app/admin/users${backParams.size ? `?${backParams}` : ''}`} className="inline-flex min-h-11 items-center gap-2 rounded-xl px-2 text-sm font-semibold text-[var(--accent-strong)] hover:bg-[var(--soft-bg)]"><ArrowLeft size={16} aria-hidden="true" />Danh sách người dùng</Link>
    {notice && <p role="status" className="rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">{notice}</p>}
    {detail.loading ? <AdminLoading /> : detail.error ? <AdminError message={detail.error} onRetry={detail.reload} /> : user && <>
      <header className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0"><p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[var(--accent-strong)]">Chi tiết tài khoản</p><h1 className="text-2xl font-bold [overflow-wrap:anywhere] sm:text-3xl">{user.fullName}</h1><p className="mt-2 text-sm text-[var(--muted-text)] [overflow-wrap:anywhere]">{user.email}</p></div>
        <div className="flex flex-wrap gap-3">
        <Button type="button" variant="secondary" onClick={() => { detail.reload(); audit.reload() }}><RefreshCw size={16} aria-hidden="true" />Làm mới</Button>
        <Button type="button" variant={user.status === 'ACTIVE' ? 'danger' : 'primary'} onClick={() => setAction(user.status === 'ACTIVE' ? 'lock' : 'unlock')}>
          {user.status === 'ACTIVE' ? <LockKeyhole size={16} aria-hidden="true" /> : <UnlockKeyhole size={16} aria-hidden="true" />}
          {user.status === 'ACTIVE' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}
        </Button>
        </div>
      </header>
      <div className="grid gap-6 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.3fr)]">
        <div className="min-w-0 space-y-6">
        <section className="min-w-0 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 sm:p-6">
          <h2 className="mb-6 font-semibold">Thông tin tài khoản</h2>
          <dl className="space-y-5 text-sm">
            <Info label="Trạng thái"><AccountStatusBadge status={user.status} /></Info>
            <Info label="Vai trò">Người dùng</Info>
            <Info label="Mã tài khoản"><span className="font-mono text-xs [overflow-wrap:anywhere]">{user.id}</span></Info>
            <Info label="Ngày tham gia">{adminDate(user.createdAt)}</Info>
            <Info label="Cập nhật gần nhất">{adminDate(user.updatedAt)}</Info>
          </dl>
          {user.status === 'LOCKED' && <div className="mt-6 space-y-3 rounded-xl border border-rose-200 bg-rose-50 p-4 text-rose-900">
            <h3 className="text-sm font-semibold">Thông tin khóa hiện tại</h3>
            <p className="whitespace-pre-wrap text-sm [overflow-wrap:anywhere]">{user.lockReason}</p>
            <p className="text-xs">Thời điểm: {adminDate(user.lockedAt)}</p>
            <p className="text-xs [overflow-wrap:anywhere]">Mã quản trị viên: {user.lockedBy}</p>
          </div>}
        </section>
        <AdminUserPlanSection userId={user.id} locked={user.status === 'LOCKED'} />
        </div>
        <section className="min-w-0 overflow-hidden rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)]">
          <h2 className="flex items-center gap-2 border-b border-[var(--app-border)] p-5 font-semibold"><History size={18} aria-hidden="true" />Nhật ký quản trị</h2>
          {audit.loading ? <AdminLoading /> : audit.error ? <div className="p-5"><AdminError message={audit.error} onRetry={audit.reload} /></div> : audit.data && <>
            {!audit.data.items.length ? <div className="space-y-2 p-7 text-sm"><p className="font-semibold">{auditPage > 0 ? 'Trang này không có bản ghi' : 'Chưa có thao tác quản trị'}</p><p className="text-[var(--muted-text)]">Các lần khóa và mở tài khoản sẽ được lưu tại đây.</p>{auditPage > 0 && <Button variant="secondary" type="button" onClick={() => changeAuditPage(0)}>Về trang đầu</Button>}</div> : <ol className="divide-y divide-[var(--app-border)]">
              {audit.data.items.map((entry) => <li key={entry.id} className="space-y-3 p-5">
                <div className="flex flex-wrap items-center justify-between gap-2"><h3 className="text-sm font-semibold">{entry.action === 'LOCK_USER' ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}</h3><time dateTime={entry.createdAt} className="text-xs tabular-nums text-[var(--muted-text)]">{adminDate(entry.createdAt)}</time></div>
                <p className="text-xs text-[var(--muted-text)] [overflow-wrap:anywhere]">Thực hiện bởi {entry.actorName || entry.actorUserId}</p>
                <p className="whitespace-pre-wrap rounded-xl bg-[var(--app-bg)] p-3 text-sm leading-6 [overflow-wrap:anywhere]">{entry.reason}</p>
                <AccountStatusBadge status={entry.newStatus} />
              </li>)}
            </ol>}
            {audit.data.items.length > 0 && <AdminPagination data={audit.data} onPageChange={changeAuditPage} />}
          </>}
        </section>
      </div>
      {action && <StatusChangeDialog user={user} action={action} onClose={() => setAction(null)} onChanged={changed} />}
    </>}
  </section>
}

function Info({ label, children }: { label: string; children: React.ReactNode }) {
  return <div><dt className="mb-1.5 text-xs text-[var(--muted-text)]">{label}</dt><dd>{children}</dd></div>
}

function StatusChangeDialog({ user, action, onClose, onChanged }: {
  user: AdminUser; action: 'lock' | 'unlock'; onClose: () => void; onChanged: (user: AdminUser) => void
}) {
  const dialog = useRef<HTMLDialogElement>(null)
  const textarea = useRef<HTMLTextAreaElement>(null)
  const inFlight = useRef(false)
  const titleId = useId()
  const reasonId = useId()
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const isLock = action === 'lock'
  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    if (!reason.trim()) {
      setError('Vui lòng nhập lý do trước khi xác nhận.')
      textarea.current?.focus()
      return
    }
    inFlight.current = true
    setPending(true)
    setError('')
    try {
      const response = await api.patch<AdminUser>(`/admin/users/${user.id}/${action}`, { reason: reason.trim() })
      onChanged(response.data)
    } catch (failure) {
      setError(getErrorMessage(failure, 'Không thể cập nhật trạng thái. Vui lòng tải lại để kiểm tra và thử lại.'))
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  return <dialog ref={dialog} aria-labelledby={titleId} aria-describedby={`${titleId}-description`} onCancel={(event) => { if (inFlight.current) event.preventDefault(); else onClose() }} className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-6 text-[var(--app-text)] shadow-xl">
    <form onSubmit={submit} className="space-y-5" aria-busy={pending}>
      <div><h2 id={titleId} className="text-xl font-bold">{isLock ? 'Khóa tài khoản' : 'Mở khóa tài khoản'}</h2><p className="mt-2 text-sm font-semibold [overflow-wrap:anywhere]">{user.fullName} · {user.email}</p></div>
      <p id={`${titleId}-description`} className="text-sm leading-6 text-[var(--muted-text)]">{isLock ? 'Người dùng sẽ không thể sử dụng dịch vụ. Các lịch đăng chưa gửi sẽ bị hủy; nội dung và lịch sử vẫn được giữ.' : 'Người dùng có thể sử dụng dịch vụ trở lại. Các lịch đã bị hủy sẽ không tự đăng bù.'}</p>
      <div>
        <label htmlFor={reasonId} className="text-sm font-semibold">Lý do <span aria-hidden="true">*</span></label>
        <textarea ref={textarea} id={reasonId} value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} rows={4} disabled={pending} aria-required="true" aria-invalid={Boolean(error) && !reason.trim()} aria-describedby={`${reasonId}-hint${error ? ` ${reasonId}-error` : ''}`} className="mt-2 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-3 text-base outline-none" />
        <p id={`${reasonId}-hint`} className="mt-1 text-xs text-[var(--muted-text)]">Lý do được lưu trong nhật ký quản trị. Tối đa 1.000 ký tự.</p>
        {error && <p id={`${reasonId}-error`} role="alert" className="mt-2 text-sm text-rose-800">{error}</p>}
      </div>
      <div className="flex flex-wrap justify-end gap-3">
        <Button variant="secondary" type="button" disabled={pending} onClick={onClose}>Hủy</Button>
        <Button variant={isLock ? 'danger' : 'primary'} type="submit" disabled={pending}>{pending ? 'Đang xử lý…' : isLock ? 'Xác nhận khóa' : 'Xác nhận mở khóa'}</Button>
      </div>
    </form>
  </dialog>
}
