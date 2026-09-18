export function FacebookBadge({ className = 'h-4 w-4' }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 24 24" aria-hidden="true">
      <circle cx="12" cy="12" r="12" fill="#1877F2" />
      <path
        fill="#fff"
        d="M15.12 8.03h-1.73c-.34 0-.72.45-.72 1.04v1.25h2.45l-.37 2.02h-2.08v6.06h-2.3v-6.06H8.31v-2.02h2.06V9.27c0-1.72 1.2-3.12 2.85-3.12h1.9v1.88Z"
      />
    </svg>
  )
}
