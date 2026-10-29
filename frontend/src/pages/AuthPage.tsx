import { ArrowLeft, Eye, EyeOff, LockKeyhole, LogIn, PlusCircle, UserPlus } from 'lucide-react'
import axios from 'axios'
import { useEffect, useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import type { AuthSession } from '../lib/auth'
import { getErrorMessage } from '../lib/errors'
import { usePreferences } from '../lib/preferences'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'

type AuthMode = 'LOGIN' | 'REGISTER'

type AuthRequest = {
  email: string
  password: string
  fullName?: string
  mfaCode?: string
}

type RegisterPendingResponse = {
  email: string
  expiresInSeconds: number
}

export function AuthPage({ onAuthenticated, notice }: { onAuthenticated: (session: AuthSession) => void; notice?: string }) {
  const { t } = usePreferences()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [mode, setMode] = useState<AuthMode>(() => (searchParams.get('mode') === 'register' ? 'REGISTER' : 'LOGIN'))
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)
  const [mfaCode, setMfaCode] = useState('')
  const [needsMfa, setNeedsMfa] = useState(false)
  const [fullName, setFullName] = useState('')
  const [verificationEmail, setVerificationEmail] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [status, setStatus] = useState('')
  const [statusTone, setStatusTone] = useState<'info' | 'error'>('info')
  const [submitting, setSubmitting] = useState(false)
  const [retryAt, setRetryAt] = useState(0)
  const [resendAt, setResendAt] = useState(0)
  const [now, setNow] = useState(Date.now)
  const waitSeconds = Math.max(0, Math.ceil((retryAt - now) / 1000))
  const resendSeconds = Math.max(0, Math.ceil((Math.max(resendAt, retryAt) - now) / 1000))

  useEffect(() => {
    if (Math.max(retryAt, resendAt) <= Date.now()) return
    const timer = window.setInterval(() => {
      setNow(Date.now())
      if (Math.max(retryAt, resendAt) <= Date.now()) window.clearInterval(timer)
    }, 1000)
    return () => window.clearInterval(timer)
  }, [retryAt, resendAt])

  function readCooldown(error: unknown) {
    if (axios.isAxiosError(error) && error.response?.status === 429) {
      const seconds = Number(error.response.headers['retry-after'])
      setNow(Date.now())
      setRetryAt(Date.now() + (Number.isFinite(seconds) && seconds > 0 ? seconds : 60) * 1000)
    }
  }

  async function resendCode() {
    if (submitting || resendSeconds > 0) return
    setSubmitting(true)
    try {
      await api.post<RegisterPendingResponse>('/auth/register', { email: verificationEmail, fullName, password })
      setNow(Date.now())
      setResendAt(Date.now() + 60000)
      setVerificationCode('')
      setStatusTone('info')
      setStatus('Nếu email đủ điều kiện đăng ký, mã mới sẽ được gửi. Vui lòng kiểm tra hộp thư và thư rác.')
    } catch (error) {
      readCooldown(error)
      setStatusTone('error')
      setStatus(getErrorMessage(error, 'Chưa thể gửi lại mã.'))
    } finally { setSubmitting(false) }
  }

  const isRegister = mode === 'REGISTER'
  const isVerifyingRegistration = isRegister && Boolean(verificationEmail)

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (submitting || waitSeconds > 0) return
    setSubmitting(true)
    setStatusTone('info')
    setStatus(isVerifyingRegistration
      ? t('Đang xác thực email...')
      : isRegister
        ? t('Đang gửi mã xác thực...')
        : t('Đang đăng nhập...'))

    try {
      if (isVerifyingRegistration) {
        const response = await api.post<AuthSession>('/auth/register/verify', {
          email: verificationEmail,
          code: verificationCode,
        })
        // A brand-new account always starts with onboarding, wherever the sign-up form was opened from.
        navigate('/app/start', { replace: true })
        onAuthenticated(response.data)
        return
      }

      const request: AuthRequest = {
        email,
        password,
        fullName: isRegister ? fullName : undefined,
        mfaCode: isRegister ? undefined : mfaCode.trim() || undefined,
      }

      if (isRegister) {
        const response = await api.post<RegisterPendingResponse>('/auth/register', request)
        setVerificationEmail(response.data.email)
        setVerificationCode('')
        setNow(Date.now())
        setResendAt(Date.now() + 60000)
        setStatusTone('info')
        setStatus(t('Nếu email đủ điều kiện đăng ký, mã xác thực sẽ được gửi. Nếu đã có tài khoản, hãy đăng nhập.'))
        return
      }

      const response = await api.post<AuthSession>('/auth/login', request)
      onAuthenticated(response.data)
    } catch (error) {
      readCooldown(error)
      if (!isRegister && axios.isAxiosError(error) && error.response?.status === 428) {
        setNeedsMfa(true)
        setStatusTone('info')
      } else setStatusTone('error')
      setStatus(getErrorMessage(error, isRegister ? 'Không thể hoàn tất đăng ký.' : 'Không thể đăng nhập.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="rescale-glow flex min-h-svh items-center justify-center bg-[#f2f4fb] px-4 py-10 text-[#344260]">
      <div className="grid w-full max-w-5xl overflow-hidden rounded-[32px] border border-black/8 bg-white shadow-[0_30px_100px_rgba(29,34,23,.14)] lg:grid-cols-[1fr_420px]">
        <section className="hidden bg-[#5369b8] p-10 text-white lg:block">
          <div className="flex h-full flex-col justify-between">
            <div>
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-[#84d2f4] text-[#475475]">
                <LockKeyhole size={20} />
              </div>
              <h1 className="mt-8 text-5xl font-semibold leading-[1.02] tracking-[-.055em]">
                Không gian riêng cho mọi hoạt động nội dung.
              </h1>
              <p className="mt-5 max-w-md text-sm leading-7 text-white/85">
                Đăng nhập để quản lý Trang Facebook, bài đăng, báo cáo và lịch đăng trong không gian riêng.
              </p>
            </div>
          </div>
        </section>

        <section className="p-6 sm:p-9">
          <div className="mb-6 flex items-center justify-between">
            <Link to="/" className="flex items-center gap-2 text-base font-bold tracking-tight">
              <span className="flex h-8 w-8 items-center justify-center rounded-full bg-[#6672d8] text-white">
                <PlusCircle size={16} aria-hidden="true" />
              </span>
              Postiva
            </Link>
            <Link to="/" className="inline-flex min-h-11 items-center gap-1 text-sm font-semibold text-[#505db8] hover:text-[#344260]">
              <ArrowLeft size={14} aria-hidden="true" />
              Trang chủ
            </Link>
          </div>
          <div className="mb-8">
            <h2 className="text-3xl font-semibold tracking-[-.03em]">{isVerifyingRegistration ? 'Xác thực email' : isRegister ? 'Tạo tài khoản' : 'Đăng nhập vào Postiva'}</h2>
            <p className="mt-2 text-sm text-slate-600">
              {isVerifyingRegistration
                ? `Nếu email đủ điều kiện đăng ký, mã 6 số sẽ được gửi tới ${verificationEmail}.`
                : isRegister
                  ? 'Chỉ cần tên, email và mật khẩu. Sau khi xác thực email, bạn sẽ được dẫn qua 3 bước để đăng bài đầu tiên.'
                  : 'Tiếp tục quản lý bài đăng, lịch đăng và báo cáo của bạn.'}
            </p>
          </div>

          {notice && <p role="status" className="mb-4 rounded-xl border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-900">{notice}</p>}
          <form onSubmit={(event) => void submitAuth(event)} className="space-y-4">
            {isVerifyingRegistration ? (
              <label className="block text-sm font-bold">
                Mã xác thực
                <input
                  value={verificationCode}
                  onChange={(event) => setVerificationCode(event.target.value.replace(/\D/g, '').slice(0, 6))}
                  className="mt-2 h-12 w-full rounded-xl border border-slate-300 px-4 text-sm tracking-[.4em] outline-none focus:border-[#8594ea]"
                  placeholder="000000"
                  inputMode="numeric"
                  autoComplete="one-time-code"
                  minLength={6}
                  maxLength={6}
                  required
                />
              </label>
            ) : isRegister && (
              <label className="block text-sm font-bold">
                Tên người dùng
                <input
                  value={fullName}
                  onChange={(event) => setFullName(event.target.value)}
                  className="mt-2 h-12 w-full rounded-xl border border-slate-300 px-4 text-sm outline-none focus:border-[#8594ea]"
                  placeholder="Tên của bạn"
                  maxLength={150}
                  required
                />
              </label>
            )}

            {!isVerifyingRegistration && !needsMfa && (
              <>
                <label className="block text-sm font-bold">
                  Email
                  <input
                    type="email"
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    className="mt-2 h-12 w-full rounded-xl border border-slate-300 px-4 text-sm outline-none focus:border-[#8594ea]"
                    placeholder="you@example.com"
                    maxLength={255}
                    required
                  />
                </label>

                <label className="block text-sm font-bold">
                  Mật khẩu
                  <span className="relative mt-2 block">
                    <input
                      type={showPassword ? 'text' : 'password'}
                      value={password}
                      onChange={(event) => setPassword(event.target.value)}
                      className="h-12 w-full rounded-xl border border-slate-300 px-4 pr-12 text-sm outline-none focus:border-[#8594ea]"
                      placeholder="Tối thiểu 8 ký tự"
                      autoComplete={isRegister ? 'new-password' : 'current-password'}
                      minLength={8}
                      aria-describedby={isRegister ? 'password-hint' : undefined}
                      required
                    />
                    <button type="button" aria-label={showPassword ? 'Ẩn mật khẩu' : 'Hiện mật khẩu'} aria-pressed={showPassword} onClick={() => setShowPassword((value) => !value)} className="absolute inset-y-0 right-0 flex w-12 items-center justify-center text-slate-500 hover:text-[#5369b8]">
                      {showPassword ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
                    </button>
                  </span>
                </label>
                {isRegister && <p id="password-hint" className="-mt-2 text-xs text-slate-600">Dùng ít nhất 8 ký tự; nên kết hợp chữ, số và ký hiệu.</p>}
                {!isRegister && <Link className="block text-right text-sm font-semibold text-[#6673b9] hover:text-[#475475]" to="/forgot-password">Quên mật khẩu?</Link>}
              </>
            )}

            {!isRegister && needsMfa && <>
              <p className="break-words text-sm text-slate-600">Xác thực hai bước cho {email}</p>
              <label className="block text-sm font-bold">Mã xác thực
                <input type="text" value={mfaCode} onChange={(event) => setMfaCode(event.target.value)} autoComplete="one-time-code" maxLength={64} required autoFocus className="mt-2 h-12 w-full rounded-xl border border-slate-300 px-4 text-base outline-none focus:border-[#8594ea]" />
                <span className="mt-1 block text-xs font-normal text-slate-500">Nhập mã 6 số từ ứng dụng hoặc một mã khôi phục chưa dùng.</span>
              </label>
              <button type="button" disabled={submitting} className="min-h-11 text-sm font-semibold text-[#6673b9]" onClick={() => { setNeedsMfa(false); setMfaCode(''); setPassword(''); setStatus('') }}>Đổi tài khoản</button>
            </>}
            {status && <p role="status" className={`rounded-[6px] border px-3 py-2 text-sm ${statusTone === 'error' ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>{status}</p>}

            <button
              disabled={submitting || waitSeconds > 0}
              className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-[#6672d8] text-sm font-bold text-white hover:-translate-y-0.5 hover:bg-[#505db8] disabled:bg-slate-300"
            >
              {isRegister ? <UserPlus size={16} /> : <LogIn size={16} />}
              {submitting ? 'Đang xử lý...' : waitSeconds > 0 ? `Thử lại sau ${waitSeconds}s` : isVerifyingRegistration ? 'Xác thực email' : isRegister ? 'Gửi mã xác thực' : needsMfa ? 'Xác thực và đăng nhập' : 'Đăng nhập'}
            </button>
            {isVerifyingRegistration && <button type="button" disabled={submitting || resendSeconds > 0} onClick={() => void resendCode()} className="min-h-11 w-full text-sm font-semibold text-[#475475] underline disabled:opacity-50">
              {resendSeconds > 0 ? `Gửi mã mới sau ${resendSeconds}s` : 'Gửi lại mã xác thực'}
            </button>}
          </form>

          <button
            type="button"
            disabled={submitting}
            onClick={() => {
              if (isVerifyingRegistration) {
                setVerificationEmail('')
                setVerificationCode('')
                setStatusTone('info')
                setStatus('')
                return
              }
              setMode(isRegister ? 'LOGIN' : 'REGISTER')
              setNeedsMfa(false)
              setMfaCode('')
              setPassword('')
              setVerificationEmail('')
              setVerificationCode('')
              setStatusTone('info')
              setStatus('')
            }}
            className="mt-5 text-sm font-bold text-[#6673b9] hover:text-[#475475]"
          >
            {isVerifyingRegistration ? 'Đổi email đăng ký' : isRegister ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Tạo tài khoản'}
          </button>
        </section>
      </div>
    </main>
  )
}
