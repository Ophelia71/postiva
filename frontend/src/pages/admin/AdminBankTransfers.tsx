import { useCallback, useEffect, useState } from 'react'
import { api } from '../../lib/api'
import { getErrorMessage } from '../../lib/errors'

type Transfer = { id: string; orderCode: number; amountVnd: number; status: string; transferContent: string; createdAt: string; buyerConfirmedAt: string | null; email: string; fullName: string }
const money = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)
const dateTime = (value: string) => new Date(value).toLocaleString('vi-VN', { dateStyle: 'short', timeStyle: 'short' })

/**
 * Bank transfers waiting for the admin. Check the bank app for a payment with the same amount and
 * content, then confirm it with the bank's transaction code; Pro turns on at once.
 */
export function AdminBankTransfers({ onChanged }: { onChanged: () => void }) {
  const [items, setItems] = useState<Transfer[] | null>(null)
  const [references, setReferences] = useState<Record<string, string>>({})
  const [rejecting, setRejecting] = useState<string | null>(null)
  const [reason, setReason] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')

  const load = useCallback(async () => {
    const response = await api.get<Transfer[]>('/admin/billing/bank-transfers')
    setItems(response.data)
  }, [])
  useEffect(() => { void load().catch(() => setError('Không tải được danh sách chuyển khoản.')) }, [load])

  async function confirm(item: Transfer) {
    setBusy(true); setError(''); setNotice('')
    try {
      await api.post(`/admin/billing/orders/${item.id}/confirm-transfer`, { bankReference: (references[item.id] ?? '').trim() })
      setNotice(`Đã xác nhận đơn #${item.orderCode}. Pro đã bật cho ${item.email}.`)
      await load(); onChanged()
    } catch (failure) { setError(getErrorMessage(failure, 'Chưa xác nhận được. Kiểm tra mã giao dịch rồi thử lại.')) }
    finally { setBusy(false) }
  }

  async function reject(item: Transfer) {
    setBusy(true); setError(''); setNotice('')
    try {
      await api.post(`/admin/billing/orders/${item.id}/reject-transfer`, { reason: reason.trim() })
      setNotice(`Đã đóng đơn #${item.orderCode}.`)
      setRejecting(null); setReason('')
      await load(); onChanged()
    } catch (failure) { setError(getErrorMessage(failure, 'Chưa đóng được đơn. Vui lòng thử lại.')) }
    finally { setBusy(false) }
  }

  return <section className="rounded-xl border border-slate-200 bg-white p-5">
    <h2 className="text-lg font-bold">Chuyển khoản chờ xác nhận</h2>
    <p className="mt-2 text-sm text-slate-600">Mở app ngân hàng, tìm giao dịch có đúng <strong>số tiền</strong> và <strong>nội dung</strong> như dưới đây. Thấy tiền rồi thì nhập mã giao dịch của ngân hàng và bấm Đã nhận tiền.</p>
    {error && <p role="alert" className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-800">{error}</p>}
    {notice && <p role="status" className="mt-3 rounded-lg border border-slate-200 bg-slate-50 p-3 text-sm text-slate-700">{notice}</p>}
    {items === null ? <p className="mt-3 text-sm text-slate-600">Đang tải…</p>
      : items.length === 0 ? <p className="mt-3 text-sm text-slate-600">Không có chuyển khoản nào đang chờ.</p>
      : <ul className="mt-4 divide-y divide-slate-100 rounded-lg border border-slate-200">
        {items.map((item) => <li key={item.id} className="grid gap-3 p-4 text-sm lg:grid-cols-[minmax(0,1fr)_minmax(0,420px)] lg:items-center">
          <div className="min-w-0">
            <p className="font-semibold">{money(item.amountVnd)} · nội dung <span className="font-mono">{item.transferContent}</span></p>
            <p className="mt-1 text-slate-600 [overflow-wrap:anywhere]">{item.fullName} · {item.email}</p>
            <p className="mt-1 text-xs text-slate-500">
              Tạo lúc {dateTime(item.createdAt)}
              {item.buyerConfirmedAt ? <> · <strong className="text-slate-700">khách báo đã chuyển lúc {dateTime(item.buyerConfirmedAt)}</strong></> : ' · khách chưa báo đã chuyển'}
              {item.status === 'EXPIRED' && ' · đơn đã quá hạn'}
            </p>
          </div>
          {rejecting === item.id
            ? <div className="flex flex-wrap gap-2">
              <input value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} placeholder="Lý do, ví dụ: không thấy tiền sau 3 ngày" aria-label="Lý do đóng đơn" className="min-h-11 min-w-0 flex-1 rounded-lg border border-slate-300 px-3" />
              <button type="button" disabled={busy || reason.trim().length < 5} onClick={() => void reject(item)} className="min-h-11 rounded-lg border border-rose-300 px-3 font-semibold text-rose-700 disabled:opacity-50">Đóng đơn</button>
              <button type="button" disabled={busy} onClick={() => { setRejecting(null); setReason('') }} className="min-h-11 rounded-lg border border-slate-300 px-3 font-semibold">Thôi</button>
            </div>
            : <div className="flex flex-wrap gap-2">
              <input value={references[item.id] ?? ''} onChange={(event) => setReferences((current) => ({ ...current, [item.id]: event.target.value }))} maxLength={120} placeholder="Mã giao dịch" aria-label={`Mã giao dịch cho đơn ${item.orderCode}`} className="min-h-11 min-w-0 flex-1 rounded-lg border border-slate-300 px-3" />
              <button type="button" disabled={busy || (references[item.id] ?? '').trim().length < 4} onClick={() => void confirm(item)} className="min-h-11 rounded-lg bg-indigo-600 px-4 font-semibold text-white disabled:opacity-50">Đã nhận tiền</button>
              <button type="button" disabled={busy} onClick={() => { setRejecting(item.id); setReason('') }} className="min-h-11 rounded-lg border border-slate-300 px-3 font-semibold">Không thấy tiền</button>
            </div>}
        </li>)}
      </ul>}
  </section>
}
