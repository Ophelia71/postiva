import { Sparkles, X } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { DEMO_SIGNUP_EVENT } from '../lib/demo'

/** Fixed notice of the demo plus the sign-up prompt shown when the visitor tries something that needs an account. */
export function DemoBanner({ onSignUp, onExit }: { onSignUp: () => void; onExit: () => void }) {
  const [action, setAction] = useState<string | null>(null)
  const dialogRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const onRequest = (event: Event) => setAction((event as CustomEvent<string>).detail || 'dùng đầy đủ tính năng này')
    window.addEventListener(DEMO_SIGNUP_EVENT, onRequest)
    return () => window.removeEventListener(DEMO_SIGNUP_EVENT, onRequest)
  }, [])

  useEffect(() => {
    if (!action) return
    dialogRef.current?.focus()
    const onKey = (event: KeyboardEvent) => { if (event.key === 'Escape') setAction(null) }
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [action])

  return (
    <>
      <div role="status" className="mb-6 flex flex-wrap items-center gap-3 rounded-lg border border-[#c9cff7] bg-[#eef0fd] px-4 py-3 text-sm text-[#344260]">
        <Sparkles size={16} aria-hidden="true" className="shrink-0" />
        <p className="min-w-0 flex-1">
          <span className="font-bold">Bạn đang dùng thử với dữ liệu mẫu.</span>{' '}
          Cứ bấm thử mọi thứ; chưa có gì được đăng. Tạo tài khoản miễn phí để kết nối kênh và đăng bài thật.
        </p>
        <button type="button" onClick={onExit} className="min-h-11 px-2 font-semibold underline underline-offset-4">Thoát dùng thử</button>
        <button type="button" onClick={onSignUp} className="min-h-11 rounded-full bg-[#505db8] px-5 font-bold text-white hover:bg-[#434fa0]">Tạo tài khoản miễn phí</button>
      </div>

      {action && (
        <div className="fixed inset-0 z-50 flex items-end justify-center bg-slate-900/40 p-4 sm:items-center" onClick={() => setAction(null)}>
          <div
            ref={dialogRef}
            role="dialog"
            aria-modal="true"
            aria-labelledby="demo-signup-title"
            tabIndex={-1}
            onClick={(event) => event.stopPropagation()}
            className="w-full max-w-md space-y-4 rounded-2xl bg-[var(--panel-bg)] p-6 shadow-2xl outline-none"
          >
            <div className="flex items-start justify-between gap-3">
              <h2 id="demo-signup-title" className="text-xl font-bold tracking-tight">Tạo tài khoản để {action}</h2>
              <button type="button" onClick={() => setAction(null)} aria-label="Đóng" className="flex h-11 w-11 shrink-0 items-center justify-center rounded-full hover:bg-[var(--soft-bg)]">
                <X size={18} aria-hidden="true" />
              </button>
            </div>
            <p className="text-sm text-[var(--muted-text)]">
              Bản dùng thử chỉ có dữ liệu mẫu. Tài khoản miễn phí có 30 lượt AI mỗi kỳ, kết nối được Facebook Page, Instagram và Threads, và tự đăng bài đúng giờ.
            </p>
            <div className="flex flex-wrap justify-end gap-3">
              <button type="button" onClick={() => setAction(null)} className="min-h-11 rounded-full border border-[var(--app-border)] px-5 text-sm font-bold hover:bg-[var(--soft-bg)]">Tiếp tục dùng thử</button>
              <button type="button" onClick={onSignUp} className="min-h-11 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0]">Tạo tài khoản miễn phí</button>
            </div>
          </div>
        </div>
      )}
    </>
  )
}
