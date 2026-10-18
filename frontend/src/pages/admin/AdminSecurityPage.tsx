import { useRef, useState, type FormEvent } from 'react'
import { api } from '../../lib/api'
import { getErrorMessage } from '../../lib/errors'
import { Button } from '../../components/UiPrimitives'
import { AdminMfaPanel } from './AdminMfaPanel'

export function AdminSecurityPage({ onPasswordChanged }: { onPasswordChanged: () => void }) {
  const inFlight = useRef(false)
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (inFlight.current) return
    if (newPassword !== confirmation) {
      setError('Mật khẩu xác nhận chưa khớp.')
      return
    }
    if (new TextEncoder().encode(newPassword).length > 72) {
      setError('Mật khẩu mới vượt quá 72 byte UTF-8.')
      return
    }
    inFlight.current = true
    setPending(true)
    setError('')
    try {
      await api.post('/admin/security/password', { currentPassword, newPassword })
      setCurrentPassword('')
      setNewPassword('')
      setConfirmation('')
      onPasswordChanged()
    } catch (failure) {
      setError(getErrorMessage(failure, 'Không thể đổi mật khẩu. Vui lòng thử lại.'))
    } finally {
      inFlight.current = false
      setPending(false)
    }
  }

  return <section className="max-w-xl space-y-6">
    <div><p className="text-xs font-bold uppercase tracking-[.18em] text-[var(--accent-strong)]">Tài khoản quản trị</p><h1 className="mt-2 text-3xl font-bold tracking-tight">Bảo mật tài khoản</h1><p className="mt-2 text-sm leading-6 text-[var(--muted-text)]">Đổi mật khẩu sẽ đăng xuất tất cả phiên hiện tại, kể cả trên thiết bị khác.</p></div>
    <AdminMfaPanel onFinished={onPasswordChanged} />
    <form onSubmit={(event) => void submit(event)} className="space-y-5 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 sm:p-7" aria-busy={pending}>
      <PasswordField label="Mật khẩu hiện tại" value={currentPassword} onChange={setCurrentPassword} autoComplete="current-password" disabled={pending} />
      <PasswordField label="Mật khẩu mới" value={newPassword} onChange={setNewPassword} autoComplete="new-password" minLength={12} disabled={pending} />
      <PasswordField label="Nhập lại mật khẩu mới" value={confirmation} onChange={setConfirmation} autoComplete="new-password" minLength={12} disabled={pending} />
      <p className="text-xs leading-5 text-[var(--muted-text)]">Dùng ít nhất 12 ký tự, tối đa 72 byte UTF-8 và khác mật khẩu hiện tại.</p>
      {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>}
      <Button type="submit" disabled={pending}>{pending ? 'Đang đổi mật khẩu…' : 'Đổi mật khẩu'}</Button>
    </form>
  </section>
}

function PasswordField({ label, value, onChange, autoComplete, minLength, disabled }: {
  label: string
  value: string
  onChange: (value: string) => void
  autoComplete: string
  minLength?: number
  disabled: boolean
}) {
  return <label className="block text-sm font-semibold">{label}
    <input type="password" value={value} onChange={(event) => onChange(event.target.value)} autoComplete={autoComplete} minLength={minLength} maxLength={72} required disabled={disabled} className="mt-2 min-h-12 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 text-base outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--focus-ring)]" />
  </label>
}
