import { ArrowLeft, KeyRound } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../lib/api'
import { getErrorMessage } from '../lib/errors'

export function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [error, setError] = useState('')
  const [pending, setPending] = useState(false)

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    setPending(true)
    setError('')
    try {
      await api.post('/auth/password/forgot', { email })
      setSent(true)
    } catch (requestError) {
      setError(getErrorMessage(requestError, 'Chưa thể gửi yêu cầu. Vui lòng thử lại sau.'))
    } finally {
      setPending(false)
    }
  }

  return (
    <main className="flex min-h-svh items-center justify-center bg-[#f2f4fb] px-4 py-10 text-[#344260]">
      <section className="w-full max-w-md rounded-[28px] border border-black/8 bg-white p-7 shadow-[0_30px_100px_rgba(29,34,23,.14)] sm:p-9">
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#e8ebff] text-[#5369b8]"><KeyRound size={21} /></div>
        <h1 className="mt-6 text-3xl font-semibold tracking-[-.04em]">Quên mật khẩu?</h1>
        <p className="mt-2 text-sm leading-6 text-slate-500">Nhập email đã đăng ký. Nếu tài khoản đang hoạt động, Postiva sẽ gửi liên kết khôi phục có hiệu lực trong 30 phút.</p>

        {sent ? (
          <div role="status" className="mt-6 rounded-xl border border-emerald-200 bg-emerald-50 p-4 text-sm leading-6 text-emerald-900">
            Nếu email thuộc tài khoản đang hoạt động, hướng dẫn khôi phục sẽ được gửi trong ít phút. Hãy kiểm tra cả thư rác.
          </div>
        ) : (
          <form onSubmit={(event) => void submit(event)} className="mt-6 space-y-4">
            <label className="block text-sm font-bold">Email
              <input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} maxLength={255} required className="mt-2 h-12 w-full rounded-xl border border-slate-300 px-4 text-sm outline-none focus:border-[#8594ea]" placeholder="you@example.com" />
            </label>
            {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-700">{error}</p>}
            <button type="submit" disabled={pending} className="h-12 w-full rounded-full bg-[#6672d8] text-sm font-bold text-white hover:bg-[#505db8] disabled:opacity-60">{pending ? 'Đang gửi...' : 'Gửi liên kết khôi phục'}</button>
          </form>
        )}

        <Link to="/app" className="mt-6 inline-flex min-h-10 items-center gap-2 text-sm font-bold text-[#6673b9] hover:text-[#475475]"><ArrowLeft size={16} />Quay lại đăng nhập</Link>
      </section>
    </main>
  )
}
