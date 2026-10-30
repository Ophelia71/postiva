import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { BankTransferPanel, type BankAccount } from '../components/BankTransferPanel'
import { PageHeader } from '../components/PageUi'
import { api } from '../lib/api'
import { usePreferences } from '../lib/preferences'

const CARD = 'rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm'

type Offer = { available: boolean; amountVnd: number; aiLimit: number; freeAiLimit: number; periodDays: number; refundPolicyUrl: string | null; method: 'PAYOS' | 'BANK_TRANSFER' | null; bank: BankAccount | null }
type Order = { id: string; orderCode: number; amountVnd: number; aiLimit: number; periodDays: number; status: string; grantStatus: string; checkoutUrl: string | null; expiresAt: string; createdAt: string; paidAt: string | null; provider: 'PAYOS' | 'BANK_TRANSFER'; buyerConfirmedAt: string | null }
type Usage = { plan: 'FREE' | 'PRO'; endsAt: string; limit: number; remaining: number }
type Refund = { id: string; orderId: string; amountVnd: number; status: string; reason: string; createdAt: string; providerRef: string | null }

const money = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
const dateTime = (value: string) => new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))

// One plain status per order, instead of the payment and the plan as two technical columns.
function orderLabel(order: Order, t: (vi: string, en: string) => string) {
  if (order.status === 'PAID') {
    if (order.grantStatus === 'GRANTED') return t('Đã thanh toán · Pro đã bật', 'Paid · Pro active')
    if (order.grantStatus === 'NOT_APPLICABLE') return t('Đã hoàn tiền', 'Refunded')
    return t('Đã thanh toán · đang bật Pro', 'Paid · turning Pro on')
  }
  if (order.provider === 'BANK_TRANSFER' && order.status === 'PENDING') {
    return order.buyerConfirmedAt ? t('Đang chờ Postiva xác nhận tiền', 'Waiting for Postiva to confirm') : t('Chờ chuyển khoản', 'Waiting for transfer')
  }
  if (order.status === 'PENDING' || order.status === 'CREATING') return t('Chờ thanh toán', 'Waiting for payment')
  if (order.status === 'REVIEW') return t('Đang kiểm tra', 'Being checked')
  if (order.status === 'EXPIRED') return t('Hết hạn', 'Expired')
  return t('Đã hủy', 'Cancelled')
}

const REFUND_STATUS_LABELS: Record<string, string> = {
  REQUESTED: 'Đã gửi yêu cầu', APPROVED: 'Đã duyệt', REJECTED: 'Bị từ chối', PROCESSING: 'Đang hoàn tiền',
  SUCCEEDED: 'Đã hoàn tiền', FAILED: 'Chưa hoàn được',
}

export function BillingPage() {
  const { t } = usePreferences()
  const [offer, setOffer] = useState<Offer | null>(null)
  const [usage, setUsage] = useState<Usage | null>(null)
  const [orders, setOrders] = useState<Order[]>([])
  const [selected, setSelected] = useState<Order | null>(null)
  const [refunds, setRefunds] = useState<Refund[]>([])
  const [refundReason, setRefundReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  // The order the buyer just started: once its QR page is ready we send them there.
  const redirectFor = useRef<string | null>(null)
  // Orders seen while still unpaid: only these may announce "payment received" when they turn paid.
  const watching = useRef(new Set<string>())
  const selectedId = selected?.id
  const selectedStatus = selected?.status
  const selectedGrantStatus = selected?.grantStatus
  const selectedProvider = selected?.provider

  const loadUsage = useCallback(() => {
    // The plan line is a convenience; the page still works if it fails to load.
    void api.get<Usage>('/usage').then((response) => setUsage(response.data)).catch(() => undefined)
  }, [])

  const refresh = useCallback(async () => {
    const [offerResponse, ordersResponse] = await Promise.all([
      api.get<Offer>('/billing/offer'), api.get<Order[]>('/billing/orders'),
    ])
    setOffer(offerResponse.data)
    setOrders(ordersResponse.data)
    loadUsage()
    setSelected((current) => ordersResponse.data.find((item) => item.id === current?.id) ?? ordersResponse.data[0] ?? null)
    return ordersResponse.data
  }, [loadUsage])

  const showOrder = useCallback((order: Order) => {
    setSelected(order)
    setOrders((existing) => existing.some((item) => item.id === order.id)
      ? existing.map((item) => item.id === order.id ? order : item) : [order, ...existing])
  }, [])

  // First visit, or the buyer coming back from the payOS page (?orderCode=…&cancel=…).
  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const returnedCode = params.get('orderCode')
    const cancelled = params.get('cancel') === 'true'
    if (returnedCode) window.history.replaceState(null, '', window.location.pathname)
    void refresh().then(async (list) => {
      const order = list.find((item) => String(item.orderCode) === returnedCode)
      if (!order) return
      setSelected(order)
      if (cancelled) {
        if (order.status === 'PENDING') await api.post(`/billing/orders/${order.id}/cancel`).catch(() => undefined)
        setNotice(t('Bạn đã hủy thanh toán. Chưa có khoản nào bị trừ.', 'Payment cancelled. Nothing was charged.'))
        await refresh()
        return
      }
      watching.current.add(order.id)
      setNotice(t('Đang xác nhận thanh toán. Gói Pro sẽ bật ngay khi Postiva nhận được tiền.', 'Confirming your payment. Pro turns on as soon as it arrives.'))
      const response = await api.post<Order>(`/billing/orders/${order.id}/refresh`).catch(() => null)
      if (response) showOrder(response.data)
    }).catch(() => setError(t('Không thể tải thông tin thanh toán.', 'Could not load billing details.')))
  }, [refresh, showOrder, t])

  useEffect(() => {
    if (!selectedId) { setRefunds([]); return }
    void api.get<Refund[]>(`/billing/orders/${selectedId}/refund-requests`).then((response) => setRefunds(response.data)).catch(() => setRefunds([]))
  }, [selectedId])

  // Follow an open order until the money arrives and Pro is on.
  useEffect(() => {
    if (!selectedId || !selectedStatus || !['CREATING', 'PENDING', 'PAID', 'REVIEW'].includes(selectedStatus)
      || selectedGrantStatus === 'GRANTED') return
    const timer = window.setInterval(() => {
      void api.get<Order>(`/billing/orders/${selectedId}`).then((response) => showOrder(response.data)).catch(() => undefined)
    }, selectedProvider === 'BANK_TRANSFER' ? 30000 : 4000)
    return () => window.clearInterval(timer)
  }, [selectedId, selectedStatus, selectedGrantStatus, selectedProvider, showOrder])

  useEffect(() => {
    if (!selected) return
    const done = selected.status === 'PAID' && selected.grantStatus === 'GRANTED'
    if (!done && ['CREATING', 'PENDING', 'PAID'].includes(selected.status)) watching.current.add(selected.id)
    if (done && watching.current.delete(selected.id)) {
      setNotice(t('Thanh toán thành công. Gói Pro đã được bật.', 'Payment received. Pro is now active.'))
      loadUsage()
    }
    if (redirectFor.current === selected.id && selected.status === 'PENDING' && selected.checkoutUrl) {
      redirectFor.current = null
      window.location.assign(selected.checkoutUrl)
    }
  }, [selected, loadUsage, t])

  async function buy() {
    setBusy(true); setError(''); setNotice('')
    try {
      const response = await api.post<Order>('/billing/orders', null, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
      const order = response.data
      if (order.status === 'PENDING' && order.checkoutUrl) {
        // Straight to the payOS page with the QR code; it sends the buyer back here afterwards.
        window.location.assign(order.checkoutUrl)
        return
      }
      showOrder(order)
      if (order.provider === 'BANK_TRANSFER') {
        setNotice(t(`Chuyển khoản theo thông tin của đơn #${order.orderCode}, rồi bấm "Tôi đã chuyển khoản".`, `Transfer using the details of order #${order.orderCode}, then press "I have transferred".`))
        window.setTimeout(() => document.getElementById('order-details')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100)
        setBusy(false)
        return
      }
      redirectFor.current = order.id
      if (order.status === 'CREATING') setNotice(t('Đang chuẩn bị mã thanh toán…', 'Preparing the payment code…'))
      setBusy(false)
    } catch {
      setError(t('Chưa tạo được đơn thanh toán. Vui lòng thử lại.', 'Could not create the order. Please retry.'))
      setBusy(false)
    }
  }

  async function cancel() {
    if (!selected) return
    setBusy(true); setError('')
    try {
      await api.post(`/billing/orders/${selected.id}/cancel`)
      await refresh()
      setNotice(t('Đã hủy đơn. Chưa có khoản nào bị trừ.', 'Order cancelled. Nothing was charged.'))
    } catch { setError(t('Chưa hủy được đơn. Vui lòng thử lại.', 'Could not cancel the order. Please retry.')) }
    finally { setBusy(false) }
  }

  async function markTransferred() {
    if (!selected) return
    setBusy(true); setError('')
    try {
      const response = await api.post<Order>(`/billing/orders/${selected.id}/transferred`)
      showOrder(response.data)
      setNotice(t('Cảm ơn bạn. Gói Pro sẽ bật ngay khi Postiva thấy tiền vào tài khoản.', 'Thank you. Pro turns on as soon as Postiva sees the money arrive.'))
    } catch { setError(t('Chưa gửi được. Vui lòng thử lại.', 'Could not send. Please retry.')) }
    finally { setBusy(false) }
  }

  async function requestRefund() {
    if (!selected) return
    setBusy(true); setError('')
    try {
      await api.post(`/billing/orders/${selected.id}/refund-requests`, { reason: refundReason })
      const response = await api.get<Refund[]>(`/billing/orders/${selected.id}/refund-requests`)
      setRefunds(response.data); setRefundReason('')
      setNotice(t('Đã gửi yêu cầu hoàn tiền. Postiva sẽ trả lời trong vòng 3 ngày làm việc.', 'Refund request sent. Postiva replies within 3 working days.'))
    } catch { setError(t('Chưa gửi được yêu cầu hoàn tiền.', 'Could not submit the refund request.')) }
    finally { setBusy(false) }
  }

  const openOrder = orders.find((order) => order.status === 'PENDING' && (order.checkoutUrl || order.provider === 'BANK_TRANSFER'))
  const bankTransfer = offer?.method === 'BANK_TRANSFER'

  return <div className="space-y-6">
    <PageHeader
      title={t('Gói Pro', 'Pro plan')}
      subtitle={t('Gói Free đã có lượt tạo nội dung bằng AI mỗi kỳ. Nâng lên Pro khi bạn cần thêm lượt. Trả theo từng kỳ, không tự động gia hạn.', 'Free already includes AI generations each period. Upgrade when you need more. Pay per period; no automatic renewal.')}
    />
    {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {notice && <p role="status" className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">{notice}</p>}
    {/* Plan on one side, orders and their details on the other once there is room; both sides end level. */}
    <div className="grid gap-6 lg:grid-cols-2">
    <section className={CARD}>
      <h2 className="text-lg font-bold">Pro</h2>
      {usage && <p className="mt-2 text-sm">{usage.plan === 'PRO'
        ? <>{t('Bạn đang dùng', 'You are on')} <strong>Pro</strong> {t('đến', 'until')} {dateTime(usage.endsAt)} · {t('còn', '')} {usage.remaining}/{usage.limit} {t('lượt AI', 'AI generations left')}</>
        : <>{t('Bạn đang dùng', 'You are on')} <strong>Free</strong> · {t('còn', '')} {usage.remaining}/{usage.limit} {t('lượt AI kỳ này', 'AI generations left this period')}</>}</p>}
      {offer ? <>
        <p className="mt-5 text-3xl font-bold tracking-tight">{money(offer.amountVnd)}<span className="ml-2 text-sm font-medium text-[var(--muted-text)]">/ {offer.periodDays} {t('ngày', 'days')}</span></p>
        {/* Free stays for trying Postiva out; Pro is the same app with ten times the AI. */}
        <div className="mt-4 grid grid-cols-2 overflow-hidden rounded-lg border border-[var(--app-border)] text-sm">
          <div className="p-3">
            <p className="font-semibold">Free</p>
            <p className="mt-1 text-2xl font-bold">{offer.freeAiLimit}</p>
            <p className="text-[var(--muted-text)]">{t('lượt AI mỗi kỳ', 'AI uses per period')}</p>
          </div>
          <div className="border-l border-[var(--app-border)] bg-indigo-50 p-3">
            <p className="font-semibold text-indigo-800">Pro</p>
            <p className="mt-1 text-2xl font-bold text-indigo-800">{offer.aiLimit}</p>
            <p className="text-[var(--muted-text)]">{t('lượt AI mỗi kỳ', 'AI uses per period')}</p>
          </div>
        </div>
        <ul className="mt-4 space-y-1.5 text-sm text-slate-700">
          <li>{t('Mọi tính năng khác (Daily, đăng 3 kênh, báo cáo) dùng được ở cả hai gói.', 'Everything else (Daily, three channels, reports) works on both plans.')}</li>
          <li>{t('Thanh toán bằng chuyển khoản: quét mã QR trong app ngân hàng.', 'Pay by bank transfer: scan the QR code in your banking app.')}</li>
          <li>{bankTransfer
            ? t('Pro được bật khi Postiva thấy tiền vào tài khoản, thường trong vài giờ (giờ hành chính).', 'Pro turns on once Postiva sees the money arrive, usually within a few business hours.')
            : t('Pro bật ngay khi nhận được tiền.', 'Pro turns on as soon as the money arrives.')} {t('Gia hạn sớm sẽ nối thêm kỳ mới, không mất ngày nào.', 'Renewing early adds a new period; no days are lost.')}</li>
        </ul>
        <div className="mt-5 flex flex-wrap items-center gap-3">
          {openOrder
            ? openOrder.checkoutUrl
              ? <a href={openOrder.checkoutUrl} className="inline-flex min-h-11 items-center rounded-lg bg-indigo-600 px-5 font-semibold text-white">{t('Tiếp tục thanh toán', 'Continue payment')}</a>
              : <button type="button" onClick={() => { setSelected(openOrder); window.setTimeout(() => document.getElementById('order-details')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100) }} className="min-h-11 rounded-lg bg-indigo-600 px-5 font-semibold text-white">{t('Xem thông tin chuyển khoản', 'Show transfer details')}</button>
            : <button type="button" disabled={!offer.available || busy} onClick={() => void buy()} className="min-h-11 rounded-lg bg-indigo-600 px-5 font-semibold text-white disabled:cursor-not-allowed disabled:bg-slate-400">{busy ? t('Đang tạo đơn…', 'Creating the order…') : usage?.plan === 'PRO' ? t('Gia hạn Pro', 'Renew Pro') : t('Nâng lên Pro', 'Upgrade to Pro')}</button>}
          <Link to="/refund-policy" className="text-sm font-semibold text-indigo-700 underline underline-offset-4">{t('Chính sách hoàn tiền', 'Refund policy')}</Link>
        </div>
        {!offer.available && <p className="mt-3 text-sm text-slate-600">{t('Thanh toán trực tuyến đang tạm đóng. Bạn vẫn dùng Free bình thường; cần Pro ngay thì liên hệ postiva84@gmail.com.', 'Online payment is paused. Free keeps working; for Pro now, contact postiva84@gmail.com.')}</p>}
      </> : <p className="mt-2 text-sm">{t('Đang tải…', 'Loading…')}</p>}
    </section>
    <div className="flex flex-col gap-6">
    <section className={`${CARD} flex-1`}>
      <h2 className="text-lg font-bold">{t('Lịch sử thanh toán', 'Payment history')}</h2>
      {orders.length === 0 ? <p className="mt-3 text-sm text-slate-600">{t('Chưa có thanh toán nào.', 'No payments yet.')}</p> : <ul className="mt-3 space-y-2">{orders.map((order) => <li key={order.id}>
        <button type="button" onClick={() => setSelected(order)} className={`flex min-h-12 w-full flex-wrap items-center justify-between gap-x-3 gap-y-1 rounded-lg border px-3 py-2 text-left text-sm ${selected?.id === order.id ? 'border-indigo-500 bg-indigo-50' : 'border-slate-200'}`}>
          <span><span className="font-semibold">#{order.orderCode}</span> · {money(order.amountVnd)} · {orderLabel(order, t)}</span>
          <span className="text-slate-600">{dateTime(order.createdAt)}</span>
        </button>
      </li>)}</ul>}
    </section>
    {selected && <section id="order-details" className={`${CARD} scroll-mt-20 text-sm`}>
      <h2 className="text-lg font-bold">{t('Đơn', 'Order')} #{selected.orderCode}</h2>
      <p className="mt-2">{money(selected.amountVnd)} · Pro {selected.periodDays} {t('ngày', 'days')}, {selected.aiLimit} {t('lượt AI', 'AI generations')}</p>
      <p className="mt-1 font-semibold">{orderLabel(selected, t)}</p>
      {selected.status === 'PAID' && ['PENDING', 'FAILED'].includes(selected.grantStatus) && <p className="mt-2 text-slate-700">{t('Postiva đã nhận tiền và đang bật Pro cho bạn. Không cần thanh toán lại.', 'Payment received; Pro is being turned on. Do not pay again.')}</p>}
      {selected.status === 'REVIEW' && <p className="mt-2 text-slate-700">{t('Khoản thanh toán cần kiểm tra thêm. Postiva sẽ liên hệ nếu cần; không cần thanh toán lại.', 'This payment needs a check. Postiva will contact you if needed; do not pay again.')}</p>}
      {selected.status === 'PENDING' && selected.provider === 'BANK_TRANSFER' && offer?.bank && <BankTransferPanel
        bank={offer.bank} orderCode={selected.orderCode} amountVnd={selected.amountVnd} buyerConfirmed={Boolean(selected.buyerConfirmedAt)}
        busy={busy} onTransferred={() => void markTransferred()} onCancel={() => void cancel()} />}
      {selected.status === 'PENDING' && selected.provider !== 'BANK_TRANSFER' && <div className="mt-4 flex flex-wrap gap-3">
        {selected.checkoutUrl && <a href={selected.checkoutUrl} className="inline-flex min-h-11 items-center rounded-lg bg-indigo-600 px-4 font-semibold text-white">{t('Tiếp tục thanh toán', 'Continue payment')}</a>}
        <button type="button" disabled={busy} onClick={() => void cancel()} className="min-h-11 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50">{t('Hủy đơn này', 'Cancel this order')}</button>
      </div>}
      {selected.status === 'PAID' && <div className="mt-6 border-t border-slate-200 pt-4">
        <h3 className="font-bold">{t('Yêu cầu hoàn tiền', 'Refund request')}</h3>
        {refunds.map((refund) => <p key={refund.id} className="mt-2">{dateTime(refund.createdAt)} · {money(refund.amountVnd)} · {REFUND_STATUS_LABELS[refund.status] ?? refund.status}</p>)}
        {refunds.length === 0 && <><label htmlFor="refund-reason" className="mt-3 block">{t('Lý do (ít nhất 10 ký tự)', 'Reason (at least 10 characters)')}</label>
          <textarea id="refund-reason" value={refundReason} onChange={(event) => setRefundReason(event.target.value)} maxLength={1000} rows={3} className="mt-1 w-full rounded-lg border border-slate-300 p-2" />
          <button type="button" disabled={busy || refundReason.trim().length < 10} onClick={() => void requestRefund()} className="mt-2 min-h-11 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50">{t('Gửi yêu cầu', 'Submit request')}</button></>}
      </div>}
    </section>}
    </div>
    </div>
  </div>
}
