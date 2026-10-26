import { type ReactNode } from 'react'
import { Link } from 'react-router-dom'

export function EmptyState({
  icon,
  title,
  description,
  actionLabel,
  actionTo,
}: {
  icon: ReactNode
  title: string
  description: string
  actionLabel?: string
  actionTo?: string
}) {
  return (
    <div className="flex flex-col items-center px-6 py-10 text-center">
      <span className="flex h-11 w-11 items-center justify-center rounded-full bg-[var(--soft-bg)] text-[#505db8]" aria-hidden="true">
        {icon}
      </span>
      <p className="mt-4 text-base font-bold">{title}</p>
      <p className="mt-1 max-w-sm text-sm text-[var(--muted-text)]">{description}</p>
      {actionLabel && actionTo && (
        <Link
          to={actionTo}
          className="mt-5 inline-flex min-h-11 items-center justify-center rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0]"
        >
          {actionLabel}
        </Link>
      )}
    </div>
  )
}
