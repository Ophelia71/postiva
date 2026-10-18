import { KeyRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import { getErrorMessage } from '../lib/errors'

export function ChangePasswordPage({ onPasswordChanged }: { onPasswordChanged: () => void }) {
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)
  const [sessionError, setSessionError] = useState('')
  const [revoking, setRevoking] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    if (newPassword !== confirmation) {
      setError('Hai mật khẩu mới chưa khớp.')
      return
    }
    setPending(true)
    setError('')
    try {
      await api.post('/auth/password/change', { currentPassword, newPassword })
      onPasswordChanged()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Không thể đổi mật khẩu. Vui lòng thử lại.'))
    } finally {
      setPending(false)
    }
  }

  async function revokeSessions() {
    if (revoking || !currentPassword) {
      setSessionError('Nhập mật khẩu hiện tại để xác minh yêu cầu.')
      return
    }
    setRevoking(true)
    setSessionError('')
    try {
      await api.post('/auth/sessions/revoke', { currentPassword })
      onPasswordChanged()
    } catch (requestError) {
      setSessionError(getErrorMessage(requestError, 'Không thể thu hồi phiên. Vui lòng thử lại.'))
    } finally {
      setRevoking(false)
    }
  }

  return (
    <section className="mx-auto max-w-2xl rounded-[24px] border border-[var(--app-border)] bg-[var(--panel-bg)] p-6 shadow-sm sm:p-8">
      <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#e8ebff] text-[#5369b8]"><KeyRound size={21} /></div>
      <h1 className="mt-5 text-2xl font-bold tracking-tight">Bảo mật tài khoản</h1>
      <p className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted-text)]">Đổi mật khẩu sẽ thu hồi tất cả phiên đăng nhập hiện tại. Bạn cần đăng nhập lại trên các thiết bị sau khi lưu.</p>
      <form onSubmit={(event) => void submit(event)} className="mt-7 max-w-lg space-y-4">
        <label className="block text-sm font-semibold">Mật khẩu hiện tại
          <input type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} maxLength={72} required disabled={pending} className="mt-2 min-h-12 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 text-base outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--focus-ring)]" />
        </label>
        <label className="block text-sm font-semibold">Mật khẩu mới
          <input type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} minLength={8} maxLength={72} required disabled={pending} className="mt-2 min-h-12 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 text-base outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--focus-ring)]" />
        </label>
        <label className="block text-sm font-semibold">Nhập lại mật khẩu mới
          <input type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} minLength={8} maxLength={72} required disabled={pending} className="mt-2 min-h-12 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 text-base outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--focus-ring)]" />
        </label>
        <p className="text-xs leading-5 text-[var(--muted-text)]">Mật khẩu mới cần ít nhất 8 ký tự và không vượt quá 72 byte UTF-8.</p>
        {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
        <button type="submit" disabled={pending} className="min-h-12 rounded-full bg-[#6672d8] px-6 text-sm font-bold text-white hover:bg-[#505db8] disabled:opacity-60">{pending ? 'Đang cập nhật...' : 'Đổi mật khẩu và đăng xuất'}</button>
      </form>
      <div className="mt-8 max-w-lg border-t border-[var(--app-border)] pt-6">
        <h2 className="text-base font-bold">Phiên đăng nhập</h2>
        <p className="mt-2 text-sm leading-6 text-[var(--muted-text)]">Thu hồi đăng nhập trên tất cả thiết bị. Bạn sẽ được đăng xuất tại đây và cần đăng nhập lại.</p>
        {sessionError && <p role="alert" className="mt-3 rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{sessionError}</p>}
        <button type="button" onClick={() => void revokeSessions()} disabled={revoking} className="mt-4 min-h-11 rounded-full border border-rose-300 px-5 text-sm font-bold text-rose-700 hover:bg-rose-50 disabled:opacity-60">{revoking ? 'Đang thu hồi...' : 'Thu hồi tất cả phiên'}</button>
      </div>
    </section>
  )
}
