import { Eye, EyeOff } from 'lucide-react'
import { useId, useState } from 'react'

type PasswordVisibilityFieldProps = {
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete: string
  minLength?: number
  maxLength?: number
  disabled?: boolean
}

export function PasswordVisibilityField({
  label, value, onChange, autoComplete, minLength, maxLength = 72, disabled = false,
}: PasswordVisibilityFieldProps) {
  const id = useId()
  const [visible, setVisible] = useState(false)

  return <div className="text-sm font-semibold">
    <label htmlFor={id}>{label}</label>
    <div className="relative mt-2">
      <input
        id={id}
        type={visible ? 'text' : 'password'}
        value={value}
        onChange={(event) => onChange(event.target.value)}
        autoComplete={autoComplete}
        minLength={minLength}
        maxLength={maxLength}
        required
        disabled={disabled}
        className="min-h-12 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 pr-12 text-base outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--focus-ring)]"
      />
      <button
        type="button"
        aria-label={`${visible ? 'Ẩn' : 'Hiện'} ${label.toLowerCase()}`}
        aria-pressed={visible}
        onClick={() => setVisible((value) => !value)}
        disabled={disabled}
        className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-[var(--muted-text)] hover:text-[var(--color-primary)] disabled:cursor-not-allowed disabled:opacity-50"
      >
        {visible ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
      </button>
    </div>
  </div>
}
