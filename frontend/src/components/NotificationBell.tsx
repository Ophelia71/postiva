import { Bell } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import type { AppChannel } from '../lib/appDataApi'
import { expiringChannels, expiryText } from './TokenExpiryNotice'

/** The bell in the app's menu: lists what needs the shop's attention, today only tokens that are about to expire. */
export function NotificationBell({ channels, placement, collapsed = false }: {
  channels: AppChannel[] | null
  placement: 'above' | 'below'
  collapsed?: boolean
}) {
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

  const compact = placement === 'below'
  return (
    <div ref={rootRef} className="relative">
      <button
        type="button"
        onClick={() => setOpen((value) => !value)}
        aria-expanded={open}
        aria-haspopup="dialog"
        aria-label={items.length > 0 ? `Thông báo, ${items.length} thông báo mới` : 'Thông báo'}
        className={compact
          ? 'relative flex h-11 w-11 items-center justify-center rounded-full text-[var(--app-text)] hover:bg-[var(--soft-bg)]'
          : `relative flex min-h-11 w-full items-center rounded-xl text-sm font-semibold hover:bg-[var(--soft-bg)] ${collapsed ? 'justify-center' : 'gap-3 px-3'}`}
      >
        <Bell size={18} aria-hidden="true" />
        {!compact && !collapsed && <span>Thông báo</span>}
        {items.length > 0 && (
          <span aria-hidden="true" className={`absolute flex h-5 min-w-5 items-center justify-center rounded-full bg-rose-600 px-1 text-[11px] font-bold text-white ${compact ? 'right-0 top-0' : collapsed ? 'right-1 top-0' : 'right-3 top-1/2 -translate-y-1/2'}`}>
            {items.length}
          </span>
        )}
      </button>

      {open && (
        <div
          role="dialog"
          aria-label="Thông báo"
          className={`absolute z-30 w-72 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-2 shadow-xl shadow-slate-200/40 ${placement === 'above' ? 'bottom-12 left-0' : 'right-0 top-12'}`}
        >
          <p className="px-3 py-2 text-sm font-bold">Thông báo</p>
          {items.length === 0 ? (
            <p className="px-3 pb-3 text-sm text-[var(--muted-text)]">Chưa có thông báo nào.</p>
          ) : (
            <ul>
              {items.map((item) => (
                <li key={item.channel.id} className="rounded-md px-3 py-2 text-sm">
                  <p className={item.days <= 0 ? 'font-semibold text-rose-700' : 'font-semibold'}>{expiryText(item)}</p>
                  <Link to="/app/connections" onClick={() => setOpen(false)} className="mt-1 inline-flex min-h-11 items-center font-semibold underline underline-offset-4">
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
