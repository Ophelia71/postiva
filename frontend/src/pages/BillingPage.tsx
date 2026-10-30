import { BarChart3, CalendarDays, Check, Clock, Share2, Sparkles, type LucideIcon } from 'lucide-react'
import { useCallback, useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import { BankTransferPanel, type BankAccount } from '../components/BankTransferPanel'
import { PageHeader } from '../components/PageUi'
import { api } from '../lib/api'
import { usePreferences } from '../lib/preferences'

const CARD = 'rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm'

type Feature = { icon: string; text: string }
type Plan = { code: string; name: string; headline: string; description: string; priceVnd: number; aiLimit: number; periodDays: number; features: Feature[]; purchasable: boolean }
type Offer = { available: boolean; amountVnd: number; periodDays: number; refundPolicyUrl: string | null; method: 'PAYOS' | 'BANK_TRANSFER' | null; bank: BankAccount | null; autoConfirm: boolean; plans: Plan[] }
type Order = { id: string; orderCode: number; amountVnd: number; aiLimit: number; periodDays: number; status: string; grantStatus: string; checkoutUrl: string | null; expiresAt: string; createdAt: string; paidAt: string | null; provider: 'PAYOS' | 'BANK_TRANSFER' }
type Usage = { plan: string; endsAt: string; limit: number; remaining: number }
type Refund = { id: string; orderId: string; amountVnd: number; status: string; reason: string; createdAt: string; providerRef: string | null }
type T = (vi: string, en: string) => string

const money = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
const dateTime = (value: string) => new Intl.DateTimeFormat('vi-VN', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value))
const isOpen = (order: Order) => order.status === 'PENDING' || order.status === 'CREATING'

// One plain status per order, instead of the payment and the plan as two technical columns.
function orderLabel(order: Order, t: T) {
  if (order.status === 'PAID') {
    if (order.grantStatus === 'GRANTED') return t('Đã thanh toán · Pro đã bật', 'Paid · Pro active')
    if (order.grantStatus === 'NOT_APPLICABLE') return t('Đã hoàn tiền', 'Refunded')
    return t('Đã thanh toán · đang bật Pro', 'Paid · turning Pro on')
  }
  if (order.provider === 'BANK_TRANSFER' && order.status === 'PENDING') return t('Chờ chuyển khoản', 'Waiting for transfer')
  if (isOpen(order)) return t('Chờ thanh toán', 'Waiting for payment')
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
  // The history row the buyer opened, for its details and refund request.
  const [openedId, setOpenedId] = useState<string | null>(null)
  const [refunds, setRefunds] = useState<Refund[]>([])
  const [refundReason, setRefundReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  // The order the buyer just started: once its payOS page is ready we send them there.
  const redirectFor = useRef<string | null>(null)
  // Orders seen while still unpaid: only these may announce "payment received" when they turn paid.
  const watching = useRef(new Set<string>())

  // At most one unpaid order at a time: it gets the big checkout panel at the top.
  const pending = orders.find(isOpen) ?? null
  const opened = orders.find((order) => order.id === openedId && !isOpen(order)) ?? null
  const pendingId = pending?.id
  const pendingProvider = pending?.provider

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
    return ordersResponse.data
  }, [loadUsage])

  const showOrder = useCallback((order: Order) => {
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
    if (!opened) { setRefunds([]); return }
    void api.get<Refund[]>(`/billing/orders/${opened.id}/refund-requests`).then((response) => setRefunds(response.data)).catch(() => setRefunds([]))
  }, [opened])

  // Follow the unpaid order until the money arrives; its QR code disappears the moment it is paid.
  useEffect(() => {
    if (!pendingId) return
    watching.current.add(pendingId)
    const timer = window.setInterval(() => {
      void api.get<Order>(`/billing/orders/${pendingId}`).then((response) => showOrder(response.data)).catch(() => undefined)
    }, 4000)
    return () => window.clearInterval(timer)
  }, [pendingId, pendingProvider, showOrder])

  // A paid order may still be turning Pro on; keep asking until it is.
  const granting = orders.find((order) => order.status === 'PAID' && ['PENDING', 'FAILED'].includes(order.grantStatus))?.id
  useEffect(() => {
    if (!granting) return
    const timer = window.setInterval(() => {
      void api.get<Order>(`/billing/orders/${granting}`).then((response) => showOrder(response.data)).catch(() => undefined)
    }, 4000)
    return () => window.clearInterval(timer)
  }, [granting, showOrder])

  useEffect(() => {
    for (const order of orders) {
      if (order.status === 'PAID' && order.grantStatus === 'GRANTED' && watching.current.delete(order.id)) {
        setNotice(t('Thanh toán thành công. Gói Pro đã được bật.', 'Payment received. Pro is now active.'))
        loadUsage()
      }
      if (redirectFor.current === order.id && order.status === 'PENDING' && order.checkoutUrl) {
        redirectFor.current = null
        window.location.assign(order.checkoutUrl)
      }
    }
  }, [orders, loadUsage, t])

  async function buy() {
    setBusy(true); setError(''); setNotice('')
    try {
      const response = await api.post<Order>('/billing/orders', null, { headers: { 'Idempotency-Key': crypto.randomUUID() } })
      const order = response.data
      if (order.provider !== 'BANK_TRANSFER' && order.status === 'PENDING' && order.checkoutUrl) {
        // Straight to the payOS page with the QR code; it sends the buyer back here afterwards.
        window.location.assign(order.checkoutUrl)
        return
      }
      if (order.provider !== 'BANK_TRANSFER') redirectFor.current = order.id
      showOrder(order)
      window.scrollTo({ top: 0, behavior: 'smooth' })
      if (order.status === 'CREATING') setNotice(t('Đang chuẩn bị mã thanh toán…', 'Preparing the payment code…'))
    } catch {
      setError(t('Chưa tạo được đơn thanh toán. Vui lòng thử lại.', 'Could not create the order. Please retry.'))
    } finally { setBusy(false) }
  }

  async function cancel(order: Order) {
    setBusy(true); setError('')
    try {
      await api.post(`/billing/orders/${order.id}/cancel`)
      await refresh()
      setNotice(t('Đã hủy đơn. Chưa có khoản nào bị trừ.', 'Order cancelled. Nothing was charged.'))
    } catch { setError(t('Chưa hủy được đơn. Vui lòng thử lại.', 'Could not cancel the order. Please retry.')) }
    finally { setBusy(false) }
  }

  async function requestRefund(order: Order) {
    setBusy(true); setError('')
    try {
      await api.post(`/billing/orders/${order.id}/refund-requests`, { reason: refundReason })
      const response = await api.get<Refund[]>(`/billing/orders/${order.id}/refund-requests`)
      setRefunds(response.data); setRefundReason('')
      setNotice(t('Đã gửi yêu cầu hoàn tiền. Postiva sẽ trả lời trong vòng 3 ngày làm việc.', 'Refund request sent. Postiva replies within 3 working days.'))
    } catch { setError(t('Chưa gửi được yêu cầu hoàn tiền.', 'Could not submit the refund request.')) }
    finally { setBusy(false) }
  }

  return <div className="space-y-6">
    <PageHeader
      title={t('Gói dịch vụ', 'Plans')}
      subtitle={t('Chọn gói phù hợp với shop của bạn. Trả theo từng kỳ, không tự động gia hạn.', 'Pick the plan that fits your shop. Pay per period; no automatic renewal.')}
    />
    {error && <p role="alert" className="rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {notice && <p role="status" className="rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">{notice}</p>}

    {pending && offer && <CheckoutPanel order={pending} offer={offer} plans={offer.plans} busy={busy} onCancel={() => void cancel(pending)} t={t} />}

    {/* One card per plan, side by side like a pricing page; a new plan is simply one more card. */}
    {offer ? <section aria-label={t('Các gói', 'Plans')} className="grid gap-5 md:grid-cols-[repeat(auto-fit,minmax(280px,1fr))]">
      {offer.plans.map((plan) => <PlanCard key={plan.code} plan={plan} usage={usage} busy={busy} blocked={Boolean(pending)} onBuy={() => void buy()} t={t} />)}
    </section> : <p className="text-sm text-slate-600">{t('Đang tải…', 'Loading…')}</p>}
    {offer && !offer.available && <p className="text-sm text-slate-600">{t('Thanh toán trực tuyến đang tạm đóng. Bạn vẫn dùng Free bình thường; cần Pro ngay thì liên hệ postiva84@gmail.com.', 'Online payment is paused. Free keeps working; for Pro now, contact postiva84@gmail.com.')}</p>}

    <section className={`${CARD} p-5 sm:p-6`}>
      <h2 className="text-lg font-bold">{t('Lịch sử thanh toán', 'Payment history')}</h2>
      {orders.length === 0 ? <p className="mt-3 text-sm text-slate-600">{t('Chưa có thanh toán nào.', 'No payments yet.')}</p> : <ul className="mt-4 divide-y divide-slate-100 rounded-lg border border-slate-200">
        {orders.map((order) => <li key={order.id}>
          <button type="button" onClick={() => setOpenedId(openedId === order.id ? null : order.id)} disabled={isOpen(order)} aria-expanded={openedId === order.id}
            className="flex min-h-12 w-full flex-wrap items-center justify-between gap-x-4 gap-y-1 px-4 py-3 text-left text-sm hover:bg-slate-50 disabled:cursor-default disabled:hover:bg-transparent">
            <span><span className="font-semibold">#{order.orderCode}</span> · Pro {order.periodDays} {t('ngày', 'days')} · {money(order.amountVnd)}</span>
            <span className="flex items-center gap-4"><span className="font-medium">{orderLabel(order, t)}</span><span className="text-slate-500">{dateTime(order.createdAt)}</span></span>
          </button>
          {opened?.id === order.id && <OrderDetails order={order} refunds={refunds} reason={refundReason} onReason={setRefundReason} busy={busy} onRefund={() => void requestRefund(order)} t={t} />}
        </li>)}
      </ul>}
    </section>
  </div>
}

/** The order waiting for money, given the most room on the page: summary on one side, how to pay on the other. */
function CheckoutPanel({ order, offer, plans, busy, onCancel, t }: { order: Order; offer: Offer; plans: Plan[]; busy: boolean; onCancel: () => void; t: T }) {
  const plan = plans.find((item) => item.code === 'PRO')
  const bankTransfer = order.provider === 'BANK_TRANSFER'
  return <section aria-label={t('Đơn đang chờ thanh toán', 'Order waiting for payment')} className={`${CARD} grid gap-6 p-5 sm:p-7 lg:grid-cols-[minmax(0,320px)_minmax(0,1fr)] lg:gap-10`}>
    <div>
      <p className="text-sm font-semibold text-indigo-700">{t('Đơn đang chờ thanh toán', 'Waiting for payment')}</p>
      <h2 className="mt-1 text-2xl font-bold">{plan?.name ?? 'Pro'} · {order.periodDays} {t('ngày', 'days')}</h2>
      <p className="mt-3 text-3xl font-bold tracking-tight">{money(order.amountVnd)}</p>
      <dl className="mt-5 space-y-2 text-sm">
        <div className="flex justify-between gap-3"><dt className="text-slate-500">{t('Mã đơn', 'Order')}</dt><dd className="font-medium">#{order.orderCode}</dd></div>
        <div className="flex justify-between gap-3"><dt className="text-slate-500">{t('Lượt AI mỗi kỳ', 'AI uses per period')}</dt><dd className="font-medium">{order.aiLimit}</dd></div>
        <div className="flex justify-between gap-3"><dt className="text-slate-500">{t('Giữ đơn đến', 'Open until')}</dt><dd className="font-medium">{dateTime(order.expiresAt)}</dd></div>
      </dl>
      {order.amountVnd !== offer.amountVnd && <p className="mt-4 text-sm text-slate-600">{t(`Đơn này tạo lúc giá còn ${money(order.amountVnd)}. Hủy đơn để mua theo giá hiện tại ${money(offer.amountVnd)}.`, `This order was created at ${money(order.amountVnd)}. Cancel it to buy at today's ${money(offer.amountVnd)}.`)}</p>}
      <button type="button" disabled={busy} onClick={onCancel} className="mt-5 min-h-11 rounded-lg border border-slate-300 px-4 text-sm font-semibold disabled:opacity-50">{t('Hủy đơn này', 'Cancel this order')}</button>
    </div>
    <div className="min-w-0 border-t border-slate-100 pt-6 lg:border-l lg:border-t-0 lg:pl-10 lg:pt-0">
      {bankTransfer && offer.bank
        ? <BankTransferPanel bank={offer.bank} orderCode={order.orderCode} amountVnd={order.amountVnd} autoConfirm={offer.autoConfirm} />
        : order.checkoutUrl
          ? <div className="space-y-3 text-sm"><p>{t('Mở trang thanh toán để quét mã QR và hoàn tất.', 'Open the payment page to scan the QR code and finish.')}</p><a href={order.checkoutUrl} className="inline-flex min-h-11 items-center rounded-lg bg-indigo-600 px-5 font-semibold text-white">{t('Tiếp tục thanh toán', 'Continue payment')}</a></div>
          : <p className="text-sm text-slate-600">{t('Đang chuẩn bị mã thanh toán…', 'Preparing the payment code…')}</p>}
    </div>
  </section>
}

// Pictures for the feature keys the backend sends; an unknown key falls back to a tick.
const FEATURE_ICONS: Record<string, LucideIcon> = { ai: Sparkles, channels: Share2, daily: CalendarDays, schedule: Clock, reports: BarChart3 }

/** One plan as a pricing card: what it is, the action, the price, then what it includes. */
function PlanCard({ plan, usage, busy, blocked, onBuy, t }: { plan: Plan; usage: Usage | null; busy: boolean; blocked: boolean; onBuy: () => void; t: T }) {
  const current = usage?.plan === plan.code
  const featured = plan.code !== 'FREE'
  // A plan without a period is the one-time trial: its AI uses never come back.
  const trial = plan.periodDays === 0
  const features: Feature[] = [{ icon: 'ai', text: trial
    ? t(`${plan.aiLimit} lượt tạo bài bằng AI để dùng thử`, `${plan.aiLimit} AI generations to try it out`)
    : t(`${plan.aiLimit} lượt tạo bài bằng AI mỗi ${plan.periodDays} ngày`, `${plan.aiLimit} AI generations every ${plan.periodDays} days`) }, ...plan.features]
  const button = 'flex min-h-12 w-full items-center justify-center gap-2 rounded-full px-5 text-sm font-semibold'

  let action
  if (current && plan.purchasable && !blocked) {
    action = <button type="button" disabled={busy} onClick={onBuy} className={`${button} border border-indigo-300 bg-white text-indigo-700 hover:bg-indigo-50 disabled:opacity-60`}>{t('Gia hạn thêm một kỳ', 'Add another period')}</button>
  } else if (current) {
    action = <p className={`${button} border border-slate-300 text-slate-600`}>{t('Gói hiện tại', 'Your current plan')}</p>
  } else if (plan.purchasable && blocked) {
    action = <p className={`${button} border border-slate-300 text-slate-600`}>{t('Đang chờ thanh toán đơn ở trên', 'Waiting for the order above')}</p>
  } else if (plan.purchasable) {
    action = <button type="button" disabled={busy} onClick={onBuy} className={`${button} bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-60`}><Sparkles size={16} aria-hidden="true" />{busy ? t('Đang tạo đơn…', 'Creating the order…') : t(`Nâng lên ${plan.name}`, `Upgrade to ${plan.name}`)}</button>
  } else {
    // Keeps the prices of all cards on one line.
    action = <div className="min-h-12" aria-hidden="true" />
  }

  return <article className={`flex flex-col rounded-3xl border p-6 sm:p-8 ${featured ? 'border-indigo-300 bg-gradient-to-b from-indigo-50/70 to-[var(--panel-bg)]' : 'border-[var(--app-border)] bg-[var(--panel-bg)]'}`}>
    <h2 className="text-lg font-bold">{plan.name}</h2>
    <p className="mt-4 text-2xl font-bold tracking-tight sm:text-3xl">{plan.headline}</p>
    <p className="mt-2 text-sm text-slate-600 sm:text-base">{plan.description}</p>
    <div className="mt-6">{action}</div>
    {/* Always one line tall, so every card's price sits on the same line. */}
    <p className="mt-2 h-4 text-center text-xs text-slate-500">
      {current && usage && <>{t('Còn', 'Left:')} {usage.remaining} {t(trial ? 'lượt dùng thử' : 'lượt', trial ? 'trial uses' : 'uses')}{!trial && <> · {t('đến', 'until')} {dateTime(usage.endsAt)}</>}</>}
    </p>
    <p className="mt-6 text-4xl font-bold tracking-tight">{plan.priceVnd > 0 ? money(plan.priceVnd) : '0 ₫'}<span className="ml-2 text-base font-medium text-slate-500">{trial ? t('· dùng thử', '· trial') : `/ ${plan.periodDays} ${t('ngày', 'days')}`}</span></p>
    <ul className="mt-6 space-y-3.5 text-sm sm:text-base">
      {features.map((feature) => {
        const Icon = FEATURE_ICONS[feature.icon] ?? Check
        return <li key={feature.text} className="flex gap-3"><Icon size={19} className={`mt-0.5 shrink-0 ${featured ? 'text-indigo-600' : 'text-slate-500'}`} aria-hidden="true" /><span>{feature.text}</span></li>
      })}
    </ul>
    {featured && <p className="mt-auto pt-8 text-xs leading-5 text-slate-500">{t('Trả bằng chuyển khoản ngân hàng, không tự động gia hạn.', 'Paid by bank transfer, never renewed automatically.')}{' '}
      <Link to="/refund-policy" className="font-semibold underline underline-offset-2">{t('Chính sách hoàn tiền', 'Refund policy')}</Link></p>}
  </article>
}

function OrderDetails({ order, refunds, reason, onReason, busy, onRefund, t }: { order: Order; refunds: Refund[]; reason: string; onReason: (value: string) => void; busy: boolean; onRefund: () => void; t: T }) {
  return <div className="space-y-2 border-t border-slate-100 bg-slate-50/60 px-4 py-4 text-sm">
    <p>{money(order.amountVnd)} · Pro {order.periodDays} {t('ngày', 'days')}, {order.aiLimit} {t('lượt AI', 'AI generations')}{order.paidAt && <> · {t('trả lúc', 'paid')} {dateTime(order.paidAt)}</>}</p>
    {order.status === 'PAID' && ['PENDING', 'FAILED'].includes(order.grantStatus) && <p className="text-slate-700">{t('Postiva đã nhận tiền và đang bật Pro cho bạn. Không cần thanh toán lại.', 'Payment received; Pro is being turned on. Do not pay again.')}</p>}
    {order.status === 'REVIEW' && <p className="text-slate-700">{t('Khoản thanh toán cần kiểm tra thêm. Postiva sẽ liên hệ nếu cần; không cần thanh toán lại.', 'This payment needs a check. Postiva will contact you if needed; do not pay again.')}</p>}
    {order.status === 'PAID' && <div className="pt-2">
      <h3 className="font-bold">{t('Yêu cầu hoàn tiền', 'Refund request')}</h3>
      {refunds.map((refund) => <p key={refund.id} className="mt-2">{dateTime(refund.createdAt)} · {money(refund.amountVnd)} · {REFUND_STATUS_LABELS[refund.status] ?? refund.status}</p>)}
      {refunds.length === 0 && <><label htmlFor={`refund-${order.id}`} className="mt-2 block">{t('Lý do (ít nhất 10 ký tự)', 'Reason (at least 10 characters)')}</label>
        <textarea id={`refund-${order.id}`} value={reason} onChange={(event) => onReason(event.target.value)} maxLength={1000} rows={3} className="mt-1 w-full max-w-xl rounded-lg border border-slate-300 bg-white p-2" />
        <button type="button" disabled={busy || reason.trim().length < 10} onClick={onRefund} className="mt-2 block min-h-11 rounded-lg border border-slate-300 bg-white px-4 font-semibold disabled:opacity-50">{t('Gửi yêu cầu', 'Submit request')}</button></>}
    </div>}
  </div>
}
