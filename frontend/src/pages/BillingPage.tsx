import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api'
import { usePreferences } from '../lib/preferences'

type Offer = { available: boolean; amountVnd: number; aiLimit: number; periodDays: number; refundPolicyUrl: string | null }
type Order = { id: string; orderCode: number; amountVnd: number; aiLimit: number; periodDays: number; status: string; grantStatus: string; checkoutUrl: string | null; expiresAt: string; createdAt: string; paidAt: string | null }
type Refund = { id: string; orderId: string; amountVnd: number; status: string; reason: string; createdAt: string; providerRef: string | null }

const money = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)

export function BillingPage() {
  const { t } = usePreferences()
  const [offer, setOffer] = useState<Offer | null>(null)
  const [orders, setOrders] = useState<Order[]>([])
  const [selected, setSelected] = useState<Order | null>(null)
  const [refunds, setRefunds] = useState<Refund[]>([])
  const [refundReason, setRefundReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const selectedId = selected?.id
  const selectedStatus = selected?.status
  const selectedGrantStatus = selected?.grantStatus

  const refresh = useCallback(async () => {
    const [offerResponse, ordersResponse] = await Promise.all([
      api.get<Offer>('/billing/offer'), api.get<Order[]>('/billing/orders'),
    ])
    setOffer(offerResponse.data)
    setOrders(ordersResponse.data)
    const returnedCode = new URLSearchParams(window.location.search).get('orderCode')
    setSelected((current) => ordersResponse.data.find((item) => item.id === current?.id)
      ?? ordersResponse.data.find((item) => String(item.orderCode) === returnedCode)
      ?? ordersResponse.data[0] ?? null)
  }, [])

  useEffect(() => { void refresh().catch(() => setError(t('Không thể tải thông tin thanh toán.', 'Could not load billing details.'))) }, [refresh, t])
  useEffect(() => {
    if (!selectedId) { setRefunds([]); return }
    void api.get<Refund[]>(`/billing/orders/${selectedId}/refund-requests`).then((response) => setRefunds(response.data)).catch(() => setRefunds([]))
  }, [selectedId])
  useEffect(() => {
    if (!selectedId || !selectedStatus || !['CREATING', 'PENDING', 'PAID', 'REVIEW'].includes(selectedStatus)
      || selectedGrantStatus === 'GRANTED') return
    const timer = window.setInterval(() => {
      void api.get<Order>(`/billing/orders/${selectedId}`).then((response) => {
        setSelected(response.data)
        setOrders((existing) => existing.map((item) => item.id === response.data.id ? response.data : item))
      }).catch(() => undefined)
    }, 4000)
    return () => window.clearInterval(timer)
  }, [selectedId, selectedStatus, selectedGrantStatus])

  async function buy() {
    setBusy(true); setError(''); setNotice('')
    try {
      const response = await api.post<Order>('/billing/orders', null, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
      setSelected(response.data)
      await refresh()
      setNotice(response.data.status === 'PENDING'
        ? t('Kiểm tra số tiền của đơn bên dưới rồi chọn Tiếp tục thanh toán.', 'Check the order amount below, then choose Continue payment.')
        : t('Đang tạo liên kết thanh toán. Trang sẽ tự cập nhật; chưa cần trả tiền lần nữa.', 'Creating the payment link. The page will update; do not pay twice.'))
    } catch { setError(t('Chưa tạo được đơn thanh toán. Vui lòng thử lại.', 'Could not create the order. Please retry.')) }
    finally { setBusy(false) }
  }

  async function cancel() {
    if (!selected) return
    setBusy(true); setError('')
    try {
      await api.post(`/billing/orders/${selected.id}/cancel`)
      await refresh()
      setNotice(t('Đã cập nhật trạng thái đơn từ payOS.', 'Order status updated from payOS.'))
    } catch { setError(t('Chưa hủy được đơn. Vui lòng kiểm tra lại trạng thái.', 'Could not cancel the order. Check its status.')) }
    finally { setBusy(false) }
  }

  async function requestRefund() {
    if (!selected) return
    setBusy(true); setError('')
    try {
      await api.post(`/billing/orders/${selected.id}/refund-requests`, { reason: refundReason })
      const response = await api.get<Refund[]>(`/billing/orders/${selected.id}/refund-requests`)
      setRefunds(response.data); setRefundReason('')
      setNotice(t('Đã ghi nhận yêu cầu hoàn tiền. Bộ phận hỗ trợ sẽ đối chiếu theo chính sách.', 'Refund request recorded for review under the policy.'))
    } catch { setError(t('Chưa gửi được yêu cầu hoàn tiền.', 'Could not submit the refund request.')) }
    finally { setBusy(false) }
  }

  return <div className="mx-auto max-w-4xl space-y-6">
    <header><h1 className="text-2xl font-bold">{t('Gói Pro và thanh toán', 'Pro plan and billing')}</h1>
      <p className="mt-2 text-sm text-slate-600">{t('Thanh toán từng kỳ; không tự động gia hạn hoặc lưu thẻ.', 'Pay per period; no automatic renewal or card storage.')}</p></header>
    {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {notice && <p role="status" className="rounded-lg bg-blue-50 p-3 text-sm text-blue-900">{notice}</p>}
    <section className="rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-lg font-bold">Pro</h2>
      {offer ? <>
        <p className="mt-2 text-sm">{offer.aiLimit} {t('lượt tạo AI trong', 'AI generations over')} {offer.periodDays} {t('ngày', 'days')} · {offer.amountVnd > 0 ? money(offer.amountVnd) : t('Giá chưa công bố', 'Price not published')}</p>
        <p className="mt-2 text-sm text-slate-600">{t('Gia hạn khi kỳ Pro còn hiệu lực sẽ nối thêm kỳ mới; lượt của kỳ hiện tại không được đặt lại sớm.', 'Renewing an active Pro plan adds a future period; the current quota is not reset early.')}</p>
        {offer.refundPolicyUrl && <a className="mt-2 inline-block text-sm font-semibold text-indigo-700 underline" href={offer.refundPolicyUrl} target="_blank" rel="noreferrer">{t('Chính sách hoàn tiền', 'Refund policy')}</a>}
        <div className="mt-4"><button type="button" disabled={!offer.available || busy} onClick={() => void buy()} className="min-h-11 rounded-lg bg-indigo-600 px-5 font-semibold text-white disabled:cursor-not-allowed disabled:bg-slate-400">{t('Mua hoặc gia hạn Pro', 'Buy or renew Pro')}</button></div>
        {!offer.available && <p className="mt-3 text-sm text-slate-600">{t('Gói Pro chưa mở bán. Bạn vẫn dùng Free bình thường.', 'Pro is not on sale yet. Free remains available.')}</p>}
      </> : <p className="mt-2 text-sm">{t('Đang tải giá gói…', 'Loading plan details…')}</p>}
    </section>
    <section className="rounded-xl border border-slate-200 bg-white p-5">
      <h2 className="text-lg font-bold">{t('Lịch sử đơn', 'Order history')}</h2>
      {orders.length === 0 ? <p className="mt-3 text-sm text-slate-600">{t('Chưa có đơn thanh toán.', 'No payment orders yet.')}</p> : <ul className="mt-3 space-y-2">{orders.map((order) => <li key={order.id}>
        <button type="button" onClick={() => setSelected(order)} className={`min-h-12 w-full rounded-lg border px-3 py-2 text-left text-sm ${selected?.id === order.id ? 'border-indigo-500 bg-indigo-50' : 'border-slate-200'}`}>
          <span className="font-semibold">#{order.orderCode}</span> · {money(order.amountVnd)} · {order.status} / {order.grantStatus}
          <span className="ml-2 text-slate-500">{new Date(order.createdAt).toLocaleString()}</span>
        </button>
      </li>)}</ul>}
    </section>
    {selected && <section className="rounded-xl border border-slate-200 bg-white p-5 text-sm">
      <h2 className="text-lg font-bold">{t('Chi tiết đơn', 'Order details')} #{selected.orderCode}</h2>
      <p className="mt-2">{money(selected.amountVnd)} · Pro {selected.aiLimit} {t('lượt AI /', 'AI generations /')} {selected.periodDays} {t('ngày', 'days')}</p>
      <p className="mt-1">{t('Thanh toán:', 'Payment:')} <strong>{selected.status}</strong> · {t('Cấp quyền:', 'Entitlement:')} <strong>{selected.grantStatus}</strong></p>
      {selected.status === 'PAID' && selected.grantStatus === 'GRANTED' && <p role="status" className="mt-2 text-emerald-700">{t('Đã xác minh và cấp Pro. Nếu đang gia hạn, kỳ mới bắt đầu sau kỳ Pro hiện tại.', 'Verified and Pro granted. A renewal starts after the current Pro period.')}</p>}
      {selected.status === 'PAID' && selected.grantStatus === 'NOT_APPLICABLE' && refunds.some((refund) => refund.status === 'SUCCEEDED') && <p role="status" className="mt-2 text-slate-700">{t('Đơn đã hoàn tiền; kỳ Pro liên quan đã dừng.', 'This order was refunded; its Pro period has ended.')}</p>}
      {selected.status === 'PAID' && ['PENDING', 'FAILED'].includes(selected.grantStatus) && <p role="status" className="mt-2 text-amber-700">{t('Đã ghi nhận tiền, đang xử lý cấp quyền. Không thanh toán lại đơn này.', 'Payment recorded; entitlement is being processed. Do not pay again.')}</p>}
      {['CREATING', 'PENDING', 'REVIEW'].includes(selected.status) && <p role="status" className="mt-2 text-slate-600">{t('Chờ backend xác minh. Kết quả chuyển trang của payOS không tự cấp Pro.', 'Waiting for server verification. The payOS return page does not grant Pro.')}</p>}
      <div className="mt-4 flex flex-wrap gap-3">
        {selected.status === 'PENDING' && selected.checkoutUrl && <a href={selected.checkoutUrl} className="inline-flex min-h-11 items-center rounded-lg bg-indigo-600 px-4 font-semibold text-white">{t('Tiếp tục thanh toán', 'Continue payment')}</a>}
        {selected.status === 'PENDING' && <button type="button" disabled={busy} onClick={() => void cancel()} className="min-h-11 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50">{t('Hủy link chưa thanh toán', 'Cancel unpaid link')}</button>}
        <button type="button" onClick={() => void refresh().catch(() => setError(t('Chưa cập nhật được trạng thái.', 'Could not refresh status.')))} className="min-h-11 rounded-lg border border-slate-300 px-4 font-semibold">{t('Cập nhật trạng thái', 'Refresh status')}</button>
      </div>
      {selected.status === 'PAID' && <div className="mt-6 border-t border-slate-200 pt-4">
        <h3 className="font-bold">{t('Yêu cầu hoàn tiền', 'Refund request')}</h3>
        {refunds.map((refund) => <p key={refund.id} className="mt-2">{new Date(refund.createdAt).toLocaleString()} · {money(refund.amountVnd)} · {refund.status}</p>)}
        {refunds.length === 0 && <><label htmlFor="refund-reason" className="mt-3 block">{t('Lý do (ít nhất 10 ký tự)', 'Reason (at least 10 characters)')}</label>
          <textarea id="refund-reason" value={refundReason} onChange={(event) => setRefundReason(event.target.value)} maxLength={1000} rows={3} className="mt-1 w-full rounded-lg border border-slate-300 p-2" />
          <button type="button" disabled={busy || refundReason.trim().length < 10} onClick={() => void requestRefund()} className="mt-2 min-h-11 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50">{t('Gửi yêu cầu', 'Submit request')}</button></>}
      </div>}
    </section>}
  </div>
}
