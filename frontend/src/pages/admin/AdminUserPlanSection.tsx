import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { AdminError, AdminLoading } from '../../components/AdminUi'
import { Button } from '../../components/UiPrimitives'
import { useAdminResource } from '../../hooks/useAdminResource'
import { adminDate } from '../../lib/adminApi'
import { api } from '../../lib/api'
import { getErrorMessage } from '../../lib/errors'

type Grant = { id: string; action: 'GRANT_PRO' | 'REVOKE_PRO'; days: number | null; reason: string; createdAt: string; actorName: string; startsAt: string | null; endsAt: string | null; revoked: boolean }
type Plan = { currentPlan: 'FREE' | 'PRO'; proUntil: string | null; aiLimit: number | null; aiUsed: number | null; hasAdminGrantedPro: boolean; proAiLimit: number; history: Grant[] }

const DAY_CHOICES = [30, 90, 180, 365]

/** The user's plan, with the admin's two levers: give Pro for a number of days, or take back what admins gave. */
export function AdminUserPlanSection({ userId, locked }: { userId: string; locked: boolean }) {
  const plan = useAdminResource<Plan>(`/admin/users/${userId}/plan`)
  const [dialog, setDialog] = useState<'grant' | 'revoke' | null>(null)
  const [notice, setNotice] = useState('')
  const data = plan.data

  function done(message: string) {
    setDialog(null)
    setNotice(message)
    plan.reload()
  }

  return <section className="min-w-0 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 sm:p-6">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <h2 className="font-semibold">Gói dịch vụ</h2>
      {data && <div className="flex flex-wrap gap-2">
        {data.hasAdminGrantedPro && <Button type="button" variant="secondary" onClick={() => setDialog('revoke')}>Thu hồi Pro đã cấp</Button>}
        <Button type="button" disabled={locked} title={locked ? 'Mở khóa tài khoản trước khi cấp Pro' : undefined} onClick={() => setDialog('grant')}>{data.proUntil ? 'Gia hạn Pro' : 'Cấp Pro'}</Button>
      </div>}
    </div>
    {notice && <p role="status" className="mt-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">{notice}</p>}
    {plan.loading ? <AdminLoading /> : plan.error ? <div className="mt-4"><AdminError message={plan.error} onRetry={plan.reload} /></div> : data && <>
      <dl className="mt-5 grid gap-5 text-sm sm:grid-cols-3">
        <div><dt className="mb-1.5 text-xs text-[var(--muted-text)]">Gói hiện tại</dt><dd className="font-semibold">{data.currentPlan === 'PRO' ? 'Pro' : 'Free'}</dd></div>
        <div><dt className="mb-1.5 text-xs text-[var(--muted-text)]">Pro đến</dt><dd>{data.proUntil ? adminDate(data.proUntil) : 'Chưa có'}</dd></div>
        <div><dt className="mb-1.5 text-xs text-[var(--muted-text)]">Lượt AI kỳ này</dt><dd>{data.aiLimit == null ? 'Chưa dùng' : `${data.aiUsed ?? 0} / ${data.aiLimit}`}</dd></div>
      </dl>
      {data.history.length > 0 && <div className="mt-6 border-t border-[var(--app-border)] pt-4">
        <h3 className="text-sm font-semibold">Lịch sử cấp và thu hồi</h3>
        <ol className="mt-3 space-y-3">
          {data.history.map((entry) => <li key={entry.id} className="rounded-xl bg-[var(--app-bg)] p-3 text-sm">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <span className="font-semibold">{entry.action === 'GRANT_PRO' ? `Cấp Pro ${entry.days} ngày` : 'Thu hồi Pro đã cấp'}{entry.revoked && entry.action === 'GRANT_PRO' ? ' · đã thu hồi' : ''}</span>
              <time dateTime={entry.createdAt} className="text-xs tabular-nums text-[var(--muted-text)]">{adminDate(entry.createdAt)}</time>
            </div>
            {entry.startsAt && entry.endsAt && <p className="mt-1 text-xs text-[var(--muted-text)]">Hiệu lực {adminDate(entry.startsAt)} → {adminDate(entry.endsAt)}</p>}
            <p className="mt-2 whitespace-pre-wrap [overflow-wrap:anywhere]">{entry.reason}</p>
            <p className="mt-1 text-xs text-[var(--muted-text)]">Bởi {entry.actorName}</p>
          </li>)}
        </ol>
      </div>}
      {dialog && <PlanDialog userId={userId} mode={dialog} plan={data} onClose={() => setDialog(null)} onDone={done} />}
    </>}
  </section>
}

function PlanDialog({ userId, mode, plan, onClose, onDone }: { userId: string; mode: 'grant' | 'revoke'; plan: Plan; onClose: () => void; onDone: (message: string) => void }) {
  const dialog = useRef<HTMLDialogElement>(null)
  const inFlight = useRef(false)
  const titleId = useId()
  const [days, setDays] = useState(30)
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const isGrant = mode === 'grant'
  useEffect(() => {
    const element = dialog.current
    element?.showModal()
    return () => element?.close()
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    if (!reason.trim()) { setError('Vui lòng nhập lý do.'); return }
    inFlight.current = true
    setPending(true)
    setError('')
    try {
      if (isGrant) {
        const response = await api.post<Plan>(`/admin/users/${userId}/pro-grants`, { days, reason: reason.trim() })
        onDone(`Đã cấp Pro ${days} ngày. Pro có hiệu lực đến ${adminDate(response.data.proUntil)}.`)
      } else {
        await api.post<Plan>(`/admin/users/${userId}/pro-revocations`, { reason: reason.trim() })
        onDone('Đã thu hồi Pro do quản trị viên cấp. Pro đã thanh toán (nếu có) vẫn giữ nguyên.')
      }
    } catch (failure) {
      setError(getErrorMessage(failure, 'Chưa cập nhật được gói. Vui lòng thử lại.'))
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  return <dialog ref={dialog} aria-labelledby={titleId} onCancel={(event) => { if (inFlight.current) event.preventDefault(); else onClose() }} className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg overflow-y-auto rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-6 text-[var(--app-text)] shadow-xl">
    <form onSubmit={submit} className="space-y-5" aria-busy={pending}>
      <h2 id={titleId} className="text-xl font-bold">{isGrant ? (plan.proUntil ? 'Gia hạn Pro' : 'Cấp Pro') : 'Thu hồi Pro đã cấp'}</h2>
      <p className="text-sm leading-6 text-[var(--muted-text)]">{isGrant
        ? `${plan.proAiLimit} lượt AI mỗi kỳ. ${plan.proUntil ? `Kỳ mới nối tiếp sau ngày ${adminDate(plan.proUntil)}.` : 'Có hiệu lực ngay.'}`
        : 'Chỉ dừng phần Pro do quản trị viên cấp. Pro người dùng đã thanh toán không bị ảnh hưởng; muốn hoàn tiền thì dùng trang Thanh toán.'}</p>
      {isGrant && <fieldset>
        <legend className="text-sm font-semibold">Thời hạn</legend>
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-4">
          {DAY_CHOICES.map((value) => <button key={value} type="button" disabled={pending} onClick={() => setDays(value)} aria-pressed={days === value} className={`min-h-11 rounded-lg border text-sm font-semibold ${days === value ? 'border-[#6672d8] bg-[#eef0fd] text-[#344260]' : 'border-[var(--app-border)]'}`}>{value} ngày</button>)}
        </div>
      </fieldset>}
      <label className="block text-sm font-semibold">Lý do
        <textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} rows={3} disabled={pending} placeholder={isGrant ? 'Ví dụ: Khách chuyển khoản trực tiếp ngày 30/10, mã giao dịch …' : ''} className="mt-2 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-3 text-base font-normal outline-none" />
      </label>
      {error && <p role="alert" className="text-sm text-rose-800">{error}</p>}
      <div className="flex flex-wrap justify-end gap-3">
        <Button variant="secondary" type="button" disabled={pending} onClick={onClose}>Hủy</Button>
        <Button variant={isGrant ? 'primary' : 'danger'} type="submit" disabled={pending}>{pending ? 'Đang xử lý…' : isGrant ? `Cấp ${days} ngày` : 'Thu hồi'}</Button>
      </div>
    </form>
  </dialog>
}
