import { useRef, useState, type FormEvent, type ReactNode } from 'react'
import { api } from '../../lib/api'
import { getErrorMessage } from '../../lib/errors'
import { Button } from '../../components/UiPrimitives'
import { PasswordVisibilityField } from '../../components/PasswordVisibilityField'
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

  return <section>
    <div className="mb-6"><h1 className="text-2xl font-bold tracking-tight sm:text-3xl">Bảo mật tài khoản</h1><p className="mt-2 text-sm leading-6 text-[var(--muted-text)]">Xác thực hai bước và mật khẩu của tài khoản quản trị.</p></div>
    {/* Same setting-row layout as the user's security page: no half-empty columns on a wide screen. */}
    <div className="divide-y divide-[var(--app-border)] rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-5 sm:px-8">
      <SettingRow title="Xác thực hai bước" description="Mỗi lần đăng nhập cần thêm mã 6 số từ ứng dụng xác thực trên điện thoại. Giữ mã khôi phục ở nơi an toàn để dùng khi mất điện thoại.">
        <AdminMfaPanel onFinished={onPasswordChanged} />
      </SettingRow>
      <SettingRow title="Đổi mật khẩu" description="Sau khi đổi, mọi phiên đăng nhập sẽ bị đăng xuất, kể cả trên thiết bị khác.">
        <form onSubmit={(event) => void submit(event)} className="space-y-4" aria-busy={pending}>
          <PasswordVisibilityField label="Mật khẩu hiện tại" value={currentPassword} onChange={setCurrentPassword} autoComplete="current-password" disabled={pending} />
          <PasswordVisibilityField label="Mật khẩu mới" value={newPassword} onChange={setNewPassword} autoComplete="new-password" minLength={12} disabled={pending} />
          <PasswordVisibilityField label="Nhập lại mật khẩu mới" value={confirmation} onChange={setConfirmation} autoComplete="new-password" minLength={12} disabled={pending} />
          <p className="text-xs leading-5 text-[var(--muted-text)]">Ít nhất 12 ký tự và khác mật khẩu hiện tại.</p>
          {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>}
          <Button type="submit" disabled={pending}>{pending ? 'Đang đổi mật khẩu…' : 'Đổi mật khẩu'}</Button>
        </form>
      </SettingRow>
    </div>
  </section>
}

function SettingRow({ title, description, children }: { title: string; description: string; children: ReactNode }) {
  return (
    <div className="grid gap-4 py-6 lg:grid-cols-[minmax(0,320px)_minmax(0,1fr)] lg:gap-12">
      <div>
        <h2 className="text-base font-bold">{title}</h2>
        <p className="mt-1 text-sm leading-6 text-[var(--muted-text)]">{description}</p>
      </div>
      <div className="max-w-xl">{children}</div>
    </div>
  )
}
