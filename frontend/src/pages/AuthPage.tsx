import { LockKeyhole, LogIn, UserPlus } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { api } from '../lib/api'
import type { AuthSession } from '../lib/auth'
import { getErrorMessage } from '../lib/errors'
import { usePreferences } from '../lib/preferences'

type AuthMode = 'LOGIN' | 'REGISTER'

type AuthRequest = {
  email: string
  password: string
  fullName?: string
}

type RegisterPendingResponse = {
  email: string
  expiresInSeconds: number
}

export function AuthPage({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const { t } = usePreferences()
  const [mode, setMode] = useState<AuthMode>('LOGIN')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [verificationEmail, setVerificationEmail] = useState('')
  const [verificationCode, setVerificationCode] = useState('')
  const [status, setStatus] = useState('')
  const [statusTone, setStatusTone] = useState<'info' | 'error'>('info')
  const [submitting, setSubmitting] = useState(false)

  const isRegister = mode === 'REGISTER'
  const isVerifyingRegistration = isRegister && Boolean(verificationEmail)

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
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
        onAuthenticated(response.data)
        return
      }

      const request: AuthRequest = {
        email,
        password,
        fullName: isRegister ? fullName : undefined,
      }

      if (isRegister) {
        const response = await api.post<RegisterPendingResponse>('/auth/register', request)
        setVerificationEmail(response.data.email)
        setVerificationCode('')
        setStatusTone('info')
        setStatus(t(`Mã xác thực đã được gửi tới ${response.data.email}. Vui lòng kiểm tra email.`))
        return
      }

      const response = await api.post<AuthSession>('/auth/login', request)
      onAuthenticated(response.data)
    } catch (error) {
      setStatusTone('error')
      setStatus(getErrorMessage(error, isRegister ? 'Không thể hoàn tất đăng ký.' : 'Không thể đăng nhập.'))
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="rescale-grid rescale-glow flex min-h-svh items-center justify-center bg-[#f2f4fb] px-4 py-10 text-[#344260]">
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
              <p className="mt-5 max-w-md text-sm leading-7 text-white/55">
                Đăng nhập để quản lý Trang Facebook, bài đăng, báo cáo và lịch đăng trong không gian riêng.
              </p>
            </div>
          </div>
        </section>

        <section className="p-6 sm:p-9">
          <div className="mb-8">
            <p className="text-xs font-bold uppercase tracking-[.2em] text-[#6673b9]">Không gian Postiva</p>
            <h2 className="mt-3 text-3xl font-semibold tracking-[-.04em]">{isVerifyingRegistration ? 'Xác thực email' : isRegister ? 'Tạo tài khoản' : 'Chào mừng trở lại'}</h2>
            <p className="mt-2 text-sm text-slate-500">
              {isVerifyingRegistration
                ? `Nhập mã 6 số đã gửi tới ${verificationEmail}.`
                : isRegister
                  ? 'Tạo tài khoản bằng tên người dùng, email và mật khẩu.'
                  : ''}
            </p>
          </div>

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
                  maxLength={255}
                  required
                />
              </label>
            )}

            {!isVerifyingRegistration && (
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
                  <input
                    type="password"
                    value={password}
                    onChange={(event) => setPassword(event.target.value)}
                    className="mt-2 h-12 w-full rounded-xl border border-slate-300 px-4 text-sm outline-none focus:border-[#8594ea]"
                    placeholder="Tối thiểu 8 ký tự"
                    minLength={8}
                    required
                  />
                </label>
              </>
            )}

            {status && <p className={`rounded-[6px] border px-3 py-2 text-sm ${statusTone === 'error' ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>{status}</p>}

            <button
              disabled={submitting}
              className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-[#6672d8] text-sm font-bold text-white hover:-translate-y-0.5 hover:bg-[#505db8] disabled:bg-slate-300"
            >
              {isRegister ? <UserPlus size={16} /> : <LogIn size={16} />}
              {submitting ? 'Đang xử lý...' : isVerifyingRegistration ? 'Xác thực email' : isRegister ? 'Gửi mã xác thực' : 'Đăng nhập'}
            </button>
          </form>

          <button
            type="button"
            onClick={() => {
              if (isVerifyingRegistration) {
                setVerificationEmail('')
                setVerificationCode('')
                setStatusTone('info')
                setStatus('')
                return
              }
              setMode(isRegister ? 'LOGIN' : 'REGISTER')
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
