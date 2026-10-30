import { Check, Copy } from 'lucide-react'
import { useState } from 'react'

export type BankAccount = { bankCode: string; accountNo: string; accountName: string }

// Display names for the bank codes VietQR uses; the code itself is shown for any other bank.
const BANK_NAMES: Record<string, string> = { TPB: 'TPBank', VCB: 'Vietcombank', MB: 'MB Bank', TCB: 'Techcombank', ACB: 'ACB', BIDV: 'BIDV', ICB: 'VietinBank', VPB: 'VPBank' }

/** What the buyer writes in the transfer, so the admin can match the money to the order. Same as the backend. */
const transferContent = (orderCode: number) => `POSTIVA${orderCode}`

const money = (amount: number) => new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount)

/**
 * A VietQR code any Vietnamese banking app can scan, with amount and content filled in,
 * plus the same details as text for anyone paying from another device.
 */
export function BankTransferPanel({ bank, orderCode, amountVnd, buyerConfirmed, busy, onTransferred, onCancel }: {
  bank: BankAccount; orderCode: number; amountVnd: number; buyerConfirmed: boolean; busy: boolean
  onTransferred: () => void; onCancel: () => void
}) {
  const content = transferContent(orderCode)
  const qr = `https://img.vietqr.io/image/${encodeURIComponent(bank.bankCode)}-${encodeURIComponent(bank.accountNo)}-compact2.png`
    + `?amount=${amountVnd}&addInfo=${encodeURIComponent(content)}&accountName=${encodeURIComponent(bank.accountName)}`

  return <div className="mt-4 grid gap-5 sm:grid-cols-[200px_minmax(0,1fr)]">
    <img src={qr} alt={`Mã QR chuyển ${money(amountVnd)} tới ${bank.accountName}`} width={200} height={240} className="mx-auto w-[200px] rounded-lg border border-slate-200 bg-white" />
    <div className="min-w-0 space-y-3 text-sm">
      <p>Quét mã bằng app ngân hàng, hoặc chuyển khoản theo thông tin dưới đây. <strong>Ghi đúng nội dung</strong> để Postiva nhận ra đơn của bạn.</p>
      <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200">
        <Row label="Ngân hàng" value={BANK_NAMES[bank.bankCode] ?? bank.bankCode} />
        <Row label="Số tài khoản" value={bank.accountNo} copy />
        <Row label="Chủ tài khoản" value={bank.accountName} />
        <Row label="Số tiền" value={money(amountVnd)} copy={String(amountVnd)} />
        <Row label="Nội dung" value={content} copy strong />
      </dl>
      {buyerConfirmed
        ? <p role="status" className="rounded-lg border border-slate-200 bg-slate-50 p-3">Postiva đã nhận được báo của bạn. Gói Pro sẽ bật ngay khi kiểm tra thấy tiền vào tài khoản, thường trong vài giờ (giờ hành chính).</p>
        : <div className="flex flex-wrap gap-3">
          <button type="button" disabled={busy} onClick={onTransferred} className="min-h-11 rounded-lg bg-indigo-600 px-5 font-semibold text-white disabled:opacity-60">Tôi đã chuyển khoản</button>
          <button type="button" disabled={busy} onClick={onCancel} className="min-h-11 rounded-lg border border-slate-300 px-4 font-semibold disabled:opacity-50">Hủy đơn này</button>
        </div>}
    </div>
  </div>
}

function Row({ label, value, copy, strong }: { label: string; value: string; copy?: boolean | string; strong?: boolean }) {
  const [copied, setCopied] = useState(false)
  async function copyValue() {
    try {
      await navigator.clipboard.writeText(typeof copy === 'string' ? copy : value)
      setCopied(true)
      window.setTimeout(() => setCopied(false), 1500)
    } catch {
      // Clipboard can be blocked; the value is still on screen to copy by hand.
    }
  }
  return <div className="flex items-center justify-between gap-3 px-3 py-2">
    <dt className="shrink-0 text-slate-500">{label}</dt>
    <dd className={`flex min-w-0 items-center gap-2 text-right ${strong ? 'font-bold' : 'font-medium'}`}>
      <span className="[overflow-wrap:anywhere]">{value}</span>
      {copy && <button type="button" onClick={() => void copyValue()} aria-label={`Sao chép ${label.toLowerCase()}`} className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md text-slate-500 hover:bg-slate-100">
        {copied ? <Check size={15} aria-hidden="true" /> : <Copy size={15} aria-hidden="true" />}
      </button>}
    </dd>
  </div>
}
