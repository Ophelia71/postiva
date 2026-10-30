import { Clock } from 'lucide-react'
import { useEffect, useRef, useState } from 'react'
import type { UserRole } from '../lib/auth'
import {
  WARNING_MS,
  announceIdleLogout,
  currentWork,
  idleState,
  isIdleLogoutSignal,
  markUnsavedWork,
  recordActivity,
  saveDrafts,
} from '../lib/idleSession'

const ACTIVITY_EVENTS = ['pointerdown', 'pointermove', 'keydown', 'wheel', 'touchstart', 'scroll'] as const
const ACTIVITY_WRITE_MS = 5_000
const HEARTBEAT_MS = 20_000

/** Signs out after inactivity and warns two minutes before; see lib/idleSession for the rules. */
export function IdleSessionGuard({ role, onIdleLogout }: { role: UserRole; onIdleLogout: (notice: string) => void }) {
  const [remaining, setRemaining] = useState<number | null>(null)
  const onIdleLogoutRef = useRef(onIdleLogout)
  useEffect(() => {
    onIdleLogoutRef.current = onIdleLogout
  }, [onIdleLogout])

  useEffect(() => {
    let lastWrite = 0
    let lastHeartbeat = 0
    let done = false
    const onActivity = () => {
      const now = Date.now()
      if (now - lastWrite < ACTIVITY_WRITE_MS) return
      lastWrite = now
      recordActivity(now)
    }
    const check = () => {
      if (done) return
      const now = Date.now()
      const work = currentWork()
      if (now - lastHeartbeat >= HEARTBEAT_MS) {
        lastHeartbeat = now
        // Postiva working for the user counts as activity, so other tabs hold their clock too.
        if (work.busy) recordActivity(now)
        if (work.unsaved) markUnsavedWork(now)
      }
      const { elapsed, limit } = idleState(role, now)
      if (elapsed >= limit) {
        done = true
        onIdleLogoutRef.current(announceIdleLogout())
        return
      }
      setRemaining(limit - elapsed <= WARNING_MS ? limit - elapsed : null)
    }
    // Another tab is signing out: keep this tab's unsaved work before the session disappears.
    const onStorage = (event: StorageEvent) => {
      if (isIdleLogoutSignal(event)) saveDrafts()
    }

    ACTIVITY_EVENTS.forEach((name) => window.addEventListener(name, onActivity, { passive: true, capture: true }))
    window.addEventListener('storage', onStorage)
    // Timers pause while a laptop sleeps; checking on return signs out at once if the limit passed meanwhile.
    document.addEventListener('visibilitychange', check)
    window.addEventListener('focus', check)
    const timer = window.setInterval(check, 1_000)
    check()
    return () => {
      done = true
      ACTIVITY_EVENTS.forEach((name) => window.removeEventListener(name, onActivity, { capture: true }))
      window.removeEventListener('storage', onStorage)
      document.removeEventListener('visibilitychange', check)
      window.removeEventListener('focus', check)
      window.clearInterval(timer)
    }
  }, [role])

  if (remaining === null) return null
  const seconds = Math.ceil(remaining / 1000)
  const countdown = `${Math.floor(seconds / 60)}:${String(seconds % 60).padStart(2, '0')}`
  return (
    <div className="fixed inset-0 z-[70] flex items-center justify-center bg-slate-950/40 px-4" role="alertdialog" aria-modal="true" aria-labelledby="idle-title" aria-describedby="idle-text">
      <div className="w-full max-w-sm rounded-2xl bg-white p-5 shadow-2xl">
        <div className="flex items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#e8ebff] text-[#5369b8]">
            <Clock size={20} aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <h2 id="idle-title" className="text-lg font-bold text-slate-800">Bạn sắp bị đăng xuất</h2>
            <p id="idle-text" className="mt-2 text-sm leading-6 text-slate-600">
              Bạn chưa thao tác một lúc. Postiva sẽ đăng xuất sau <span className="font-bold tabular-nums text-slate-800">{countdown}</span> để bảo vệ tài khoản.
            </p>
          </div>
        </div>
        <div className="mt-5 flex justify-end">
          <button
            type="button"
            autoFocus
            onClick={() => {
              recordActivity()
              setRemaining(null)
            }}
            className="min-h-11 rounded-full bg-[#6672d8] px-5 text-sm font-bold text-white hover:bg-[#505db8]"
          >
            Tiếp tục làm việc
          </button>
        </div>
      </div>
    </div>
  )
}
