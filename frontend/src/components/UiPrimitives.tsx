import { X } from 'lucide-react'
import { forwardRef, useEffect, useId, useRef, type ButtonHTMLAttributes, type ReactNode } from 'react'
import { cn } from '../lib/cn'

type Tone = 'info' | 'success' | 'warning' | 'error'

const buttonVariants = {
  primary: 'bg-[var(--color-primary)] text-white shadow-[var(--shadow-sm)] hover:bg-[var(--color-primary-hover)]',
  secondary: 'border border-[var(--app-border)] bg-[var(--panel-bg)] text-[var(--app-text)] hover:bg-[var(--soft-bg)]',
  subtle: 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)]',
  danger: 'bg-[var(--color-error)] text-white hover:bg-[var(--color-error-hover)]',
}

export function Button({
  variant = 'primary',
  className,
  children,
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: keyof typeof buttonVariants
}) {
  return (
    <button
      {...props}
      className={cn(
        'inline-flex min-h-11 items-center justify-center gap-2 rounded-[var(--radius-control)] px-4 text-sm font-bold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--focus-ring)] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
        buttonVariants[variant],
        className,
      )}
    >
      {children}
    </button>
  )
}

export const IconButton = forwardRef<HTMLButtonElement, ButtonHTMLAttributes<HTMLButtonElement> & {
  label: string
}>(function IconButton({
  label,
  className,
  children,
  ...props
}, ref) {
  return (
    <button
      {...props}
      ref={ref}
      aria-label={label}
      title={label}
      className={cn(
        'inline-flex min-h-11 min-w-11 items-center justify-center rounded-[var(--radius-control)] text-[var(--muted-text)] transition hover:bg-[var(--soft-bg)] hover:text-[var(--app-text)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-[var(--focus-ring)] focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
        className,
      )}
    >
      {children}
    </button>
  )
})

export function StatusMessage({
  tone = 'info',
  children,
  className,
}: {
  tone?: Tone
  children: ReactNode
  className?: string
}) {
  const toneClass = {
    info: 'border-[var(--status-info-border)] bg-[var(--status-info-bg)] text-[var(--status-info-text)]',
    success: 'border-[var(--status-success-border)] bg-[var(--status-success-bg)] text-[var(--status-success-text)]',
    warning: 'border-[var(--status-warning-border)] bg-[var(--status-warning-bg)] text-[var(--status-warning-text)]',
    error: 'border-[var(--status-error-border)] bg-[var(--status-error-bg)] text-[var(--status-error-text)]',
  }[tone]

  return (
    <div
      role={tone === 'error' ? 'alert' : 'status'}
      aria-live={tone === 'error' ? 'assertive' : 'polite'}
      className={cn('rounded-[var(--radius-control)] border px-3 py-2 text-sm font-semibold', toneClass, className)}
    >
      {children}
    </div>
  )
}

export function Field({
  label,
  helperText,
  error,
  className,
  inputClassName,
  ...props
}: React.InputHTMLAttributes<HTMLInputElement> & {
  label: string
  helperText?: string
  error?: string
  inputClassName?: string
}) {
  const id = useId()
  const helperId = helperText ? `${id}-helper` : undefined
  const errorId = error ? `${id}-error` : undefined

  return (
    <div className={cn('text-sm font-semibold', className)}>
      <label htmlFor={id}>{label}</label>
      <input
        {...props}
        id={id}
        aria-invalid={Boolean(error)}
        aria-describedby={[helperId, errorId].filter(Boolean).join(' ') || undefined}
        className={cn(
          'mt-1.5 min-h-11 w-full rounded-[var(--radius-control)] border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm font-normal text-[var(--app-text)] outline-none placeholder:text-[var(--muted-text)] focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--focus-ring)]',
          inputClassName,
        )}
      />
      {helperText && <p id={helperId} className="mt-1 text-xs font-medium text-[var(--muted-text)]">{helperText}</p>}
      {error && <p id={errorId} role="alert" className="mt-1 text-xs font-bold text-[var(--color-error)]">{error}</p>}
    </div>
  )
}

export function ModalFrame({
  title,
  description,
  children,
  onClose,
  className,
}: {
  title: string
  description?: string
  children: ReactNode
  onClose: () => void
  className?: string
}) {
  const titleId = useId()
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const previousActive = document.activeElement instanceof HTMLElement ? document.activeElement : null
    closeRef.current?.focus()
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
      }
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      previousActive?.focus()
    }
  }, [onClose])

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-[var(--scrim)] px-4 py-6" role="dialog" aria-modal="true" aria-labelledby={titleId}>
      <div className={cn('max-h-[90dvh] w-full overflow-auto rounded-[var(--radius-modal)] border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 text-[var(--app-text)] shadow-[var(--shadow-xl)]', className)}>
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 id={titleId} className="text-lg font-bold">{title}</h2>
            {description && <p className="mt-1 text-xs text-[var(--muted-text)]">{description}</p>}
          </div>
          <IconButton ref={closeRef} type="button" onClick={onClose} label="Đóng">
            <X size={17} aria-hidden="true" />
          </IconButton>
        </div>
        {children}
      </div>
    </div>
  )
}
