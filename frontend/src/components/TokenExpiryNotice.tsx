import { Link } from 'react-router-dom'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'

/** Warn this many days before a token the shop gave an expiry date for stops working. */
const WARN_DAYS = 7
const DAY_MS = 24 * 60 * 60 * 1000

function daysLeft(channel: AppChannel, now: number) {
  if (!channel.tokenExpiresAt) return null
  const expires = new Date(channel.tokenExpiresAt).getTime()
  if (Number.isNaN(expires)) return null
  return Math.ceil((expires - now) / DAY_MS)
}

/** One line above the page when a connected channel's access token is about to expire, or already has. */
export function TokenExpiryNotice() {
  const { data, loading, error } = useAppDataResource<AppChannel[]>('/app-data/channels')
  if (loading || error || !data) return null

  const now = Date.now()
  const due = data
    .map((channel) => ({ channel, days: daysLeft(channel, now) }))
    .filter((item): item is { channel: AppChannel; days: number } => item.days !== null && item.days <= WARN_DAYS)
    .sort((a, b) => a.days - b.days)
  if (due.length === 0) return null

  const { channel, days } = due[0]
  const expired = days <= 0
  const others = due.length - 1
  return (
    <div role="status" className="mb-6 flex flex-col gap-3 rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p className="text-[var(--muted-text)]">
        <span className={`font-semibold ${expired ? 'text-rose-700' : 'text-[var(--app-text)]'}`}>
          Token của {channel.badge} {channel.name} {expired ? 'đã hết hạn.' : `hết hạn sau ${days} ngày.`}
        </span>
        {' '}{expired ? 'Bài hẹn giờ trên kênh này sẽ không đăng được.' : 'Hãy lấy token mới và kết nối lại để bài vẫn đăng đúng giờ.'}
        {others > 0 && ` Còn ${others} kênh khác cũng sắp hết hạn.`}
      </p>
      <Link to="/app/connections" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-full border border-[var(--app-border)] px-4 font-semibold hover:bg-[var(--soft-bg)]">
        Kết nối lại
      </Link>
    </div>
  )
}
