import { Box } from 'lucide-react'
import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { usePreferences } from '../lib/preferences'

export function PageState({
  title,
  description,
}: {
  title: string
  description?: string
}) {
  return (
    <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-8 text-center shadow-sm">
      <p className="text-lg font-bold">{title}</p>
      {description && <p className="mt-2 text-sm text-[var(--muted-text)]">{description}</p>}
    </div>
  )
}

export function PageHeader({
  title,
  subtitle,
  actionLabel,
}: {
  title: string
  subtitle?: string
  actionLabel?: string
}) {
  return (
    <div className="mb-7 flex items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-[var(--muted-text)]">{subtitle}</p>}
      </div>
      {actionLabel && (
        <Link
          to="/app/create"
          className="inline-flex h-10 items-center gap-2 rounded-lg bg-blue-600 px-5 text-sm font-bold text-white shadow-sm shadow-blue-200 hover:bg-blue-700"
        >
          <Box size={16} />
          {actionLabel}
        </Link>
      )}
    </div>
  )
}

export function StatCard({
  icon,
  value,
  label,
  tone,
}: {
  icon: ReactNode
  value: string
  label: string
  tone: 'blue' | 'violet' | 'amber' | 'emerald' | 'rose'
}) {
  const tones = {
    blue: 'bg-blue-50 text-blue-700',
    violet: 'bg-violet-100 text-violet-700',
    amber: 'bg-amber-50 text-amber-600',
    emerald: 'bg-emerald-50 text-emerald-600',
    rose: 'bg-rose-50 text-rose-600',
  }

  return (
    <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
      <div className={`mb-5 flex h-9 w-9 items-center justify-center rounded-lg ${tones[tone]}`}>
        {icon}
      </div>
      <p className="text-2xl font-bold">{value}</p>
      <p className="mt-1 text-sm text-[var(--muted-text)]">{label}</p>
    </div>
  )
}

export function PlatformBadge({ platform }: { platform: string }) {
  const className =
    platform === 'TikTok'
      ? 'bg-slate-950 text-white'
      : platform === 'Instagram'
        ? 'bg-pink-100 text-pink-700'
        : 'bg-blue-100 text-blue-700'

  return (
    <span className={`w-fit rounded-md px-2 py-1 text-xs font-bold ${className}`}>
      {platform}
    </span>
  )
}

export function StatusBadge({ status }: { status: string }) {
  const { t } = usePreferences()
  const className =
    status === 'Published'
      ? 'bg-emerald-50 text-emerald-700'
      : status === 'Failed'
        ? 'bg-rose-50 text-rose-700'
        : status === 'Canceled'
          ? 'bg-slate-100 text-slate-500'
          : 'bg-blue-50 text-blue-700'

  return (
    <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${className}`}>
      {translateStatus(status, t)}
    </span>
  )
}

function translateStatus(status: string, t: (vi: string) => string) {
  switch (status) {
    case 'Published':
      return t('Đã đăng')
    case 'Failed':
      return t('Thất bại')
    case 'Canceled':
      return t('Đã hủy')
    case 'Scheduled':
      return t('Đã lên lịch')
    case 'Draft':
      return 'Bản nháp'
    case 'Planned':
      return 'Đã lên kế hoạch'
    case 'Publishing':
      return 'Đang đăng'
    default:
      return status
  }
}
