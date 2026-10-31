import { Bell } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { AppChannel } from '../lib/appDataApi'
import { expiringChannels, expiryText } from './TokenExpiryNotice'

/** The bell in the app's top bar: lists what needs the shop's attention, today only tokens that are about to expire. */
export function NotificationBell({ channels }: { channels: AppChannel[] | null }) {
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const items = expiringChannels(channels)

  useEffect(() => {
    if (!open) return
    const onPointer = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpen(false)
    }
    document.addEventListener('pointerdown', onPointer)
    document.addEventListener('keydown', onKey)
    return () => {
      document.removeEventListener('pointerdown', onPointer)
      document.removeEventListener('keydown', onKey)
    }
  }, [open])

  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={items.length > 0 ? `Thông báo, ${items.length} thông báo mới` : 'Thông báo'}
        className={`relative flex h-11 items-center gap-2 rounded-full border px-3 text-sm font-semibold transition sm:px-4 ${open ? 'border-[#8594ea] bg-[#eef0fd] text-[#344260]' : 'border-[var(--app-border)] bg-[var(--panel-bg)] text-[var(--app-text)] hover:border-[#8594ea] hover:bg-[#eef0fd]'}`}
      >
        <Bell size={18} aria-hidden="true" />
        <span className="hidden sm:inline">Thông báo</span>
        {items.length > 0 && (
          <span aria-hidden="true" className="flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-600 px-1.5 text-[11px] font-bold text-white">
            {items.length}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Thông báo"
          className="absolute right-0 top-14 z-30 w-80 max-w-[calc(100vw-2rem)] overflow-hidden rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-xl shadow-slate-300/30"
        >
          <p className="flex items-center justify-between border-b border-[var(--app-border)] bg-[var(--soft-bg)] px-4 py-3 text-sm font-bold">
            Thông báo
            {items.length > 0 && <span className="text-xs font-semibold text-[var(--muted-text)]">{items.length} mới</span>}
          </p>
          {items.length === 0 ? (
            <div className="flex flex-col items-center gap-2 px-4 py-8 text-center">
              <span className="flex h-11 w-11 items-center justify-center rounded-full bg-[#eef0fd] text-[#505db8]">
                <Bell size={20} aria-hidden="true" />
              </span>
              <p className="text-sm font-semibold">Chưa có thông báo</p>
              <p className="text-xs text-[var(--muted-text)]">Token kênh sắp hết hạn sẽ hiện ở đây.</p>
            </div>
          ) : (
            <ul className="divide-y divide-[var(--app-border)]">
              {items.map((item) => (
                <li key={item.channel.id} className="px-4 py-3 text-sm">
                  <p className={item.days <= 0 ? 'font-semibold text-rose-700' : 'font-semibold'}>{expiryText(item)}</p>
                  <Link to="/app/connections" onClick={() => setOpen(false)} className="mt-1 inline-flex min-h-11 items-center font-semibold text-[#505db8] underline underline-offset-4">
                    Kết nối lại
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </div>
  )
}
