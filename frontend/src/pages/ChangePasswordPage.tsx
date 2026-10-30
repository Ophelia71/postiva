import { useState, type FormEvent, type ReactNode } from 'react'
import { PageHeader } from '../components/PageUi'
import { api } from '../lib/api'
import { getErrorMessage } from '../lib/errors'
import { PasswordVisibilityField } from '../components/PasswordVisibilityField'

const PRIMARY = 'min-h-11 rounded-lg bg-[#6672d8] px-5 text-sm font-bold text-white hover:bg-[#505db8] disabled:opacity-60'
const SECONDARY = 'min-h-11 rounded-lg border border-[var(--app-border)] px-5 text-sm font-semibold hover:bg-[var(--soft-bg)] disabled:opacity-60'
const DANGER_OUTLINE = 'min-h-11 rounded-lg border border-rose-300 px-5 text-sm font-bold text-rose-700 hover:bg-rose-50'

export function ChangePasswordPage({ onPasswordChanged }: { onPasswordChanged: (notice?: string) => void }) {
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
      onPasswordChanged('Đã đổi mật khẩu. Bạn đăng nhập lại bằng mật khẩu mới.')
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
      onPasswordChanged('Đã đăng xuất khỏi tất cả thiết bị. Bạn đăng nhập lại để tiếp tục.')
    } catch (requestError) {
      setSessionError(getErrorMessage(requestError, 'Chưa đăng xuất được các thiết bị. Vui lòng thử lại.'))
    } finally {
      setRevoking(false)
    }
  }

  return (
    <section>
      <PageHeader title="Bảo mật tài khoản" subtitle="Đổi mật khẩu, đăng xuất khỏi các thiết bị khác hoặc xóa tài khoản." />
      {/* One panel of setting rows: what it is on the left, the controls on the right; no half-empty columns. */}
      <div className="divide-y divide-[var(--app-border)] rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-5 sm:px-8">
        <SettingRow title="Đổi mật khẩu" description="Sau khi đổi, bạn sẽ được đăng xuất trên mọi thiết bị và cần đăng nhập lại.">
          <form onSubmit={(event) => void submit(event)} className="space-y-4">
            <PasswordVisibilityField label="Mật khẩu hiện tại" value={currentPassword} onChange={setCurrentPassword} autoComplete="current-password" disabled={pending} />
            <PasswordVisibilityField label="Mật khẩu mới" value={newPassword} onChange={setNewPassword} autoComplete="new-password" minLength={8} disabled={pending} />
            <PasswordVisibilityField label="Nhập lại mật khẩu mới" value={confirmation} onChange={setConfirmation} autoComplete="new-password" minLength={8} disabled={pending} />
            <p className="text-xs leading-5 text-[var(--muted-text)]">Mật khẩu mới cần ít nhất 8 ký tự.</p>
            {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
            <button type="submit" disabled={pending} className={PRIMARY}>{pending ? 'Đang cập nhật...' : 'Đổi mật khẩu và đăng xuất'}</button>
          </form>
        </SettingRow>
        <SettingRow title="Đăng xuất khỏi mọi thiết bị" description="Dùng khi bạn quên đăng xuất trên máy khác. Bạn cũng sẽ được đăng xuất tại đây.">
          {sessionError && <p role="alert" className="mb-3 rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{sessionError}</p>}
          <button type="button" onClick={() => void revokeSessions()} disabled={revoking} className={SECONDARY}>{revoking ? 'Đang đăng xuất...' : 'Đăng xuất khỏi tất cả thiết bị'}</button>
        </SettingRow>
        <DeleteAccountSection onDeleted={() => onPasswordChanged('Tài khoản và toàn bộ dữ liệu của bạn đã được xóa.')} />
      </div>
    </section>
  )
}

/** A settings row: title and explanation on the left, controls on the right; stacked on phones. */
function SettingRow({ title, description, danger = false, children }: { title: string; description: string; danger?: boolean; children: ReactNode }) {
  return (
    <div className="grid gap-4 py-6 lg:grid-cols-[minmax(0,320px)_minmax(0,1fr)] lg:gap-12">
      <div>
        <h2 className={`text-base font-bold ${danger ? 'text-rose-700' : ''}`}>{title}</h2>
        <p className="mt-1 text-sm leading-6 text-[var(--muted-text)]">{description}</p>
      </div>
      <div className="max-w-xl">{children}</div>
    </div>
  )
}

function DeleteAccountSection({ onDeleted }: { onDeleted: () => void }) {
  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  async function deleteAccount(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    if (!password) {
      setError('Nhập mật khẩu hiện tại để xác nhận.')
      return
    }
    setPending(true)
    setError('')
    try {
      await api.post('/auth/account/delete', { currentPassword: password })
      onDeleted()
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Không thể xóa tài khoản. Vui lòng thử lại.'))
      setPending(false)
    }
  }

  return (
    <SettingRow title="Xóa tài khoản" description="Xóa vĩnh viễn tài khoản và dữ liệu của bạn trên Postiva. Không thể khôi phục." danger>
      {!open ? (
        <button type="button" onClick={() => setOpen(true)} className={DANGER_OUTLINE}>Xóa tài khoản</button>
      ) : (
        <form onSubmit={(event) => void deleteAccount(event)} className="space-y-4 rounded-lg border border-[var(--app-border)] bg-[var(--soft-bg)] p-4">
          <div className="text-sm leading-6">
            <p className="font-semibold">Sẽ xóa vĩnh viễn:</p>
            <ul className="mt-1 list-disc space-y-0.5 pl-5 text-[var(--muted-text)]">
              <li>Các kênh đã kết nối</li>
              <li>Mọi bài viết, bản nháp, lịch đăng, kế hoạch tuần và số liệu tương tác</li>
              <li>Ảnh, video đã tải lên và hồ sơ shop</li>
            </ul>
            <p className="mt-2 text-[var(--muted-text)]">Bài đã đăng trên Facebook, Instagram, Threads vẫn còn trên các nền tảng đó. Thời gian còn lại của gói đang dùng sẽ mất và không được hoàn tiền tự động. Lịch sử thanh toán được giữ ở dạng ẩn danh theo quy định kế toán.</p>
          </div>
          <PasswordVisibilityField label="Mật khẩu hiện tại" value={password} onChange={setPassword} autoComplete="current-password" disabled={pending} />
          {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
          <div className="flex flex-wrap gap-3">
            <button type="submit" disabled={pending} className="min-h-11 rounded-lg bg-rose-600 px-5 text-sm font-bold text-white hover:bg-rose-700 disabled:opacity-60">{pending ? 'Đang xóa...' : 'Xóa vĩnh viễn tài khoản'}</button>
            <button type="button" disabled={pending} onClick={() => { setOpen(false); setPassword(''); setError('') }} className={SECONDARY}>Hủy</button>
          </div>
        </form>
      )}
    </SettingRow>
  )
}
