import { ArrowLeft, Eye, EyeOff, LockKeyhole } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { clearAuthSession } from '../lib/auth'
import { getErrorMessage } from '../lib/errors'

export function ResetPasswordPage() {
  const [token, setToken] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [showNewPassword, setShowNewPassword] = useState(false)
  const [showConfirmation, setShowConfirmation] = useState(false)
  const [complete, setComplete] = useState(false)
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  useEffect(() => {
    const parsedToken = new URLSearchParams(window.location.hash.slice(1)).get('token') ?? ''
    setToken(parsedToken)
    // Keep the one-time secret out of the address bar and browser history after reading it.
    if (window.location.hash) window.history.replaceState(window.history.state, '', window.location.pathname + window.location.search)
  }, [])

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    if (newPassword !== confirmation) {
      setError('Hai mật khẩu chưa khớp.')
      return
    }
    if (!token) {
      setError('Liên kết khôi phục không hợp lệ hoặc đã hết hạn. Hãy yêu cầu liên kết mới.')
      return
    }
    setPending(true)
    setError('')
    try {
      await api.post('/auth/password/reset', { token, newPassword })
      clearAuthSession()
      setToken('')
      setNewPassword('')
      setConfirmation('')
      setComplete(true)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Không thể đặt lại mật khẩu. Hãy yêu cầu liên kết mới.'))
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-[#f2f4fb] px-4 py-10 text-[#344260]">
      <section className="w-full max-w-md rounded-[28px] border border-black/8 bg-white p-7 shadow-[0_30px_100px_rgba(29,34,23,.14)] sm:p-9">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#e8ebff] text-[#5369b8]"><LockKeyhole size={21} /></div>
        <h1 className="mt-6 text-3xl font-semibold tracking-[-.04em]">Đặt mật khẩu mới</h1>
        {complete ? (
          <div role="status" className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-900">Mật khẩu đã được cập nhật. Tất cả phiên đăng nhập cũ đã bị thu hồi; hãy đăng nhập lại.</div>
        ) : (
          <form onSubmit={(event) => void submit(event)} className="mt-5 space-y-4">
            {!token && <p role="alert" className="rounded-xl border border-amber-200 bg-amber-50 px-4 py-3 text-sm leading-6 text-amber-900">Không tìm thấy mã khôi phục trong liên kết. Hãy yêu cầu email mới.</p>}
            <label className="block text-sm font-bold">Mật khẩu mới
              <span className="relative mt-2 block">
                <input type={showNewPassword ? 'text' : 'password'} autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} minLength={8} maxLength={72} required className="h-12 w-full rounded-xl border border-slate-300 px-4 pr-12 text-sm outline-none focus:border-[#8594ea]" />
                <button type="button" aria-label={showNewPassword ? 'Ẩn mật khẩu mới' : 'Hiện mật khẩu mới'} aria-pressed={showNewPassword} onClick={() => setShowNewPassword((value) => !value)} className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-slate-500 hover:text-[#5369b8]">
                  {showNewPassword ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
                </button>
              </span>
            </label>
            <label className="block text-sm font-bold">Nhập lại mật khẩu mới
              <span className="relative mt-2 block">
                <input type={showConfirmation ? 'text' : 'password'} autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} minLength={8} maxLength={72} required className="h-12 w-full rounded-xl border border-slate-300 px-4 pr-12 text-sm outline-none focus:border-[#8594ea]" />
                <button type="button" aria-label={showConfirmation ? 'Ẩn mật khẩu nhập lại' : 'Hiện mật khẩu nhập lại'} aria-pressed={showConfirmation} onClick={() => setShowConfirmation((value) => !value)} className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-slate-500 hover:text-[#5369b8]">
                  {showConfirmation ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
                </button>
              </span>
            </label>
            <p className="text-xs leading-5 text-slate-500">Mật khẩu cần ít nhất 8 ký tự và tối đa 72 byte UTF-8.</p>
            {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
            <button type="submit" disabled={pending || !token} className="h-12 w-full rounded-full bg-[#6672d8] text-sm font-bold text-white hover:bg-[#505db8] disabled:opacity-60">{pending ? 'Đang cập nhật...' : 'Cập nhật mật khẩu'}</button>
          </form>
        )}
        <Link to="/app" className="mt-6 inline-flex min-h-10 items-center gap-2 text-sm font-bold text-[#6673b9] hover:text-[#475475]"><ArrowLeft size={16} />Quay lại đăng nhập</Link>
      </section>
    </main>
  )
}
