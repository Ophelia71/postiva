import { Link } from 'react-router-dom'
import type { AppChannel } from '../lib/appDataApi'

/** Warn this many days before a token the shop gave an expiry date for stops working. */
export const TOKEN_WARN_DAYS = 7
const DAY_MS = 24 * 60 * 60 * 1000

export type ExpiringChannel = { channel: AppChannel; days: number }

/** Channels whose token expires within a week or already has, the most urgent first. */
export function expiringChannels(channels: AppChannel[] | null | undefined, now = Date.now()): ExpiringChannel[] {
  return (channels ?? [])
    .flatMap((channel) => {
      if (!channel.tokenExpiresAt) return []
      const expires = new Date(channel.tokenExpiresAt).getTime()
      if (Number.isNaN(expires)) return []
      const days = Math.ceil((expires - now) / DAY_MS)
      return days <= TOKEN_WARN_DAYS ? [{ channel, days }] : []
    })
    .sort((a, b) => a.days - b.days)
}

export function expiryText({ channel, days }: ExpiringChannel) {
  return `Token của ${channel.badge} ${channel.name} ${days <= 0 ? 'đã hết hạn.' : `hết hạn sau ${days} ngày.`}`
}

/** One line above the page when a connected channel's access token is about to expire, or already has. */
export function TokenExpiryNotice({ channels }: { channels: AppChannel[] | null }) {
  const due = expiringChannels(channels)
  if (due.length === 0) return null

  const first = due[0]
  const expired = first.days <= 0
  const others = due.length - 1
  return (
    <div role="status" className="mb-6 flex flex-col gap-3 rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p className="text-[var(--muted-text)]">
        <span className={`font-semibold ${expired ? 'text-rose-700' : 'text-[var(--app-text)]'}`}>{expiryText(first)}</span>
        {' '}{expired ? 'Bài hẹn giờ trên kênh này sẽ không đăng được.' : 'Hãy lấy token mới và kết nối lại để bài vẫn đăng đúng giờ.'}
        {others > 0 && ` Còn ${others} kênh khác cũng sắp hết hạn.`}
      </p>
      <Link to="/app/connections" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-full border border-[var(--app-border)] px-4 font-semibold hover:bg-[var(--soft-bg)]">
        Kết nối lại
      </Link>
    </div>
  )
}
