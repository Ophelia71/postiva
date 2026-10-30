import { useCallback, useEffect, useState } from 'react'
import { api } from '../../lib/api'

type Order = { id: string; orderCode: number; userId: string; amountVnd: number; status: string; grantStatus: string; providerReference: string | null; reviewReason: string | null; paidAt: string | null; createdAt: string }
type Refund = { id: string; orderId: string; amountVnd: number; status: string; reason: string; providerRef: string | null; createdAt: string }
type Event = { event_kind: string; provider_reference: string | null; amount_vnd: number | null; decision: string; created_at: string }
type Audit = { actor_user_id: string; action: string; reason: string; created_at: string }
const money = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)

export function AdminBillingPage() {
  const [orders, setOrders] = useState<Order[]>([])
  const [refunds, setRefunds] = useState<Refund[]>([])
  const [status, setStatus] = useState('')
  const [selected, setSelected] = useState<Order | null>(null)
  const [events, setEvents] = useState<Event[]>([])
  const [audit, setAudit] = useState<Audit[]>([])
  const [reason, setReason] = useState('')
  const [providerReference, setProviderReference] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const selectedId = selected?.id

  const refresh = useCallback(async () => {
    const [orderResponse, refundResponse] = await Promise.all([
      api.get<Order[]>(`/admin/billing/orders${status ? `?status=${status}` : ''}`),
      api.get<Refund[]>('/admin/billing/refunds'),
    ])
    setOrders(orderResponse.data); setRefunds(refundResponse.data)
    setSelected((current) => orderResponse.data.find((item) => item.id === current?.id) ?? null)
  }, [status])

  useEffect(() => { void refresh().catch(() => setError('Không thể tải dữ liệu thanh toán.')) }, [refresh])
  useEffect(() => {
    if (!selectedId) { setEvents([]); setAudit([]); return }
    void Promise.all([
      api.get<Event[]>(`/admin/billing/orders/${selectedId}/events`),
      api.get<Audit[]>(`/admin/billing/orders/${selectedId}/audit`),
    ]).then(([eventsResponse, auditResponse]) => {
      setEvents(eventsResponse.data); setAudit(auditResponse.data)
    }).catch(() => setError('Không thể tải nhật ký của đơn.'))
  }, [selectedId])

  async function reconcile(order: Order) {
    setBusy(true); setError(''); setNotice('')
    try {
      await api.post(`/admin/billing/orders/${order.id}/reconcile`)
      await refresh()
      setNotice(`Đã đối chiếu đơn #${order.orderCode} với payOS.`)
    } catch { setError('Đối soát payOS thất bại; kiểm tra kết nối và trạng thái nhà cung cấp.') }
    finally { setBusy(false) }
  }

  async function act(refund: Refund, action: 'approve' | 'reject' | 'processing' | 'settle' | 'fail') {
    setBusy(true); setError(''); setNotice('')
    try {
      await api.post(`/admin/billing/refunds/${refund.id}/${action}`, action === 'settle'
        ? { reason, providerReference } : { reason })
      await refresh(); setReason(''); setProviderReference('')
      setNotice('Đã ghi nhận quyết định và nhật ký quản trị.')
    } catch { setError('Không cập nhật được yêu cầu hoàn; kiểm tra trạng thái, lý do và mã chứng từ.') }
    finally { setBusy(false) }
  }

  return <div className="space-y-6">
    <header><h1 className="text-2xl font-bold">Thanh toán và hoàn tiền</h1>
      <p className="mt-2 text-sm text-slate-600">Đối soát trạng thái với payOS; chỉ xác nhận hoàn sau khi đã kiểm tra chứng từ ngân hàng/cổng bên ngoài.</p></header>
    {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-red-800">{error}</p>}
    {notice && <p role="status" className="rounded-lg bg-blue-50 p-3 text-blue-900">{notice}</p>}
    <section className="rounded-xl border border-slate-200 bg-white p-5">
      <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-bold">Đơn hàng gần đây</h2>
        <label className="text-sm">Trạng thái <select value={status} onChange={(event) => setStatus(event.target.value)} className="ml-2 min-h-11 rounded-lg border border-slate-300 px-2">
          <option value="">Tất cả</option>{['CREATING', 'PENDING', 'PAID', 'CANCELLED', 'EXPIRED', 'REVIEW'].map((value) => <option key={value} value={value}>{value}</option>)}
        </select></label></div>
      {orders.length === 0 ? <p className="mt-3 text-sm text-slate-600">Không có đơn phù hợp.</p> : <div className="mt-3 overflow-x-auto"><table className="w-full min-w-[700px] text-left text-sm"><thead><tr className="border-b"><th className="p-2">Đơn</th><th className="p-2">Người dùng</th><th className="p-2">Số tiền</th><th className="p-2">Trạng thái</th><th className="p-2">Thời gian</th><th className="p-2">Thao tác</th></tr></thead><tbody>
        {orders.map((order) => <tr key={order.id} className="border-b border-slate-100"><td className="p-2"><button type="button" className="font-semibold text-indigo-700 underline" onClick={() => setSelected(order)}>#{order.orderCode}</button></td><td className="p-2 font-mono text-xs">{order.userId}</td><td className="p-2">{money(order.amountVnd)}</td><td className="p-2">{order.status} / {order.grantStatus}</td><td className="p-2">{new Date(order.createdAt).toLocaleString()}</td><td className="p-2"><button type="button" disabled={busy} onClick={() => void reconcile(order)} className="min-h-11 rounded-lg border px-3 font-semibold disabled:opacity-50">Đối soát</button></td></tr>)}
      </tbody></table></div>}
    </section>
    {selected && <section className="rounded-xl border border-slate-200 bg-white p-5 text-sm">
      <h2 className="text-lg font-bold">Đơn #{selected.orderCode}</h2>
      <p className="mt-2">{selected.status} / {selected.grantStatus} · {money(selected.amountVnd)}</p>
      <p className="mt-1 break-all">Giao dịch: {selected.providerReference ?? 'Chưa có'} · Lý do rà soát: {selected.reviewReason ?? 'Không có'}</p>
      <div className="mt-4 grid gap-5 md:grid-cols-2"><div><h3 className="font-bold">Sự kiện payOS</h3>{events.length === 0 && <p className="mt-2">Chưa có sự kiện.</p>}{events.map((item, index) => <p key={`${item.created_at}-${index}`} className="mt-2 rounded-lg bg-slate-50 p-2">{item.event_kind} · {item.decision} · {item.provider_reference ?? '—'} · {item.amount_vnd == null ? '—' : money(item.amount_vnd)}</p>)}</div>
        <div><h3 className="font-bold">Nhật ký quản trị</h3>{audit.length === 0 && <p className="mt-2">Chưa có thao tác.</p>}{audit.map((item, index) => <p key={`${item.created_at}-${index}`} className="mt-2 rounded-lg bg-slate-50 p-2">{item.action} · {item.reason}<span className="block font-mono text-xs">{item.actor_user_id}</span></p>)}</div></div>
    </section>}
    <section className="rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-lg font-bold">Yêu cầu hoàn tiền</h2>
      <p className="mt-2 text-sm text-slate-600">Phê duyệt → xử lý ở ngân hàng/cổng → kiểm tra tiền đã hoàn → nhập mã chứng từ để xác nhận. Không dùng nút xác nhận trước khi tiền đã hoàn.</p>
      {/* Both boxes take the row's height, so the two fields line up. */}
      <div className="mt-4 grid gap-3 sm:grid-cols-2"><label className="flex flex-col text-sm">Lý do / ghi chú vận hành<textarea value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} rows={2} className="mt-1 w-full flex-1 resize-none rounded-lg border border-slate-300 p-2" /></label>
        <label className="flex flex-col text-sm">Mã chứng từ hoàn tiền<input value={providerReference} onChange={(event) => setProviderReference(event.target.value)} maxLength={160} className="mt-1 min-h-11 w-full flex-1 rounded-lg border border-slate-300 px-2" /></label></div>
      {refunds.length === 0 ? <p className="mt-3 text-sm">Chưa có yêu cầu.</p> : <ul className="mt-4 space-y-3">{refunds.map((refund) => <li key={refund.id} className="rounded-lg border border-slate-200 p-3 text-sm">
        <p className="font-semibold">{money(refund.amountVnd)} · {refund.status} · đơn {orders.find((order) => order.id === refund.orderId)?.orderCode ?? refund.orderId}</p>
        <p className="mt-1">{refund.reason}</p>
        {refund.providerRef && <p className="mt-1">Chứng từ: {refund.providerRef}</p>}
        <div className="mt-2 flex flex-wrap gap-2">
          {refund.status === 'REQUESTED' && <><Action label="Duyệt" disabled={busy || reason.trim().length < 10} onClick={() => void act(refund, 'approve')} /><Action label="Từ chối" disabled={busy || reason.trim().length < 10} onClick={() => void act(refund, 'reject')} /></>}
          {refund.status === 'APPROVED' && <Action label="Bắt đầu hoàn ngoài hệ thống" disabled={busy || reason.trim().length < 10} onClick={() => void act(refund, 'processing')} />}
          {refund.status === 'PROCESSING' && <><Action label="Xác nhận đã hoàn" disabled={busy || reason.trim().length < 10 || providerReference.trim().length < 6} onClick={() => void act(refund, 'settle')} /><Action label="Hoàn thất bại" disabled={busy || reason.trim().length < 10} onClick={() => void act(refund, 'fail')} /></>}
        </div>
      </li>)}</ul>}
    </section>
  </div>
}

function Action({ label, disabled, onClick }: { label: string; disabled: boolean; onClick: () => void }) {
  return <button type="button" disabled={disabled} onClick={onClick} className="min-h-11 rounded-lg border border-slate-300 px-3 font-semibold disabled:opacity-50">{label}</button>
}
