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
 * plus the same details as text for anyone paying from another device. It disappears by itself
 * once the order is paid: the page shows it only while the order waits for money.
 */
export function BankTransferPanel({ bank, orderCode, amountVnd, autoConfirm }: {
  bank: BankAccount; orderCode: number; amountVnd: number; autoConfirm: boolean
}) {
  const content = transferContent(orderCode)
  const qr = `https://img.vietqr.io/image/${encodeURIComponent(bank.bankCode)}-${encodeURIComponent(bank.accountNo)}-compact2.png`
    + `?amount=${amountVnd}&addInfo=${encodeURIComponent(content)}&accountName=${encodeURIComponent(bank.accountName)}`

  return <div className="grid gap-6 md:grid-cols-[260px_minmax(0,1fr)] md:items-start">
    <img src={qr} alt={`Mã QR chuyển ${money(amountVnd)} tới ${bank.accountName}`} width={260} height={312} className="mx-auto w-[260px] rounded-xl border border-slate-200 bg-white" />
    <div className="min-w-0 space-y-3 text-sm">
      <p>Quét mã bằng app ngân hàng, hoặc chuyển khoản theo thông tin dưới đây. <strong>Ghi đúng nội dung</strong> để Postiva nhận ra đơn của bạn.</p>
      <dl className="divide-y divide-slate-100 rounded-lg border border-slate-200">
        <Row label="Ngân hàng" value={BANK_NAMES[bank.bankCode] ?? bank.bankCode} />
        <Row label="Số tài khoản" value={bank.accountNo} copy />
        <Row label="Chủ tài khoản" value={bank.accountName} />
        <Row label="Số tiền" value={money(amountVnd)} copy={String(amountVnd)} />
        <Row label="Nội dung" value={content} copy strong />
      </dl>
      <p role="status" className="flex items-center gap-2 text-slate-600">
        <span className="h-2 w-2 shrink-0 animate-pulse rounded-full bg-indigo-500" aria-hidden="true" />
        {autoConfirm
          ? 'Đang chờ tiền vào. Chuyển xong, mã này tự biến mất và gói Pro bật trong khoảng 1 phút.'
          : 'Đang chờ tiền vào. Mã này tự biến mất và gói Pro bật khi Postiva thấy tiền, thường trong vài giờ (giờ hành chính).'}
      </p>
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
