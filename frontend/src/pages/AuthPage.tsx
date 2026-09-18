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

export function AuthPage({ onAuthenticated }: { onAuthenticated: (session: AuthSession) => void }) {
  const { t } = usePreferences()
  const [mode, setMode] = useState<AuthMode>('LOGIN')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fullName, setFullName] = useState('')
  const [status, setStatus] = useState('')
  const [statusTone, setStatusTone] = useState<'info' | 'error'>('info')
  const [submitting, setSubmitting] = useState(false)

  const isRegister = mode === 'REGISTER'

  async function submitAuth(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setSubmitting(true)
    setStatusTone('info')
    setStatus(isRegister
      ? t('Đang tạo tài khoản...')
      : t('Đang đăng nhập...'))

    try {
      const request: AuthRequest = {
        email,
        password,
        fullName: isRegister ? fullName : undefined,
      }
      const response = await api.post<AuthSession>(isRegister ? '/auth/register' : '/auth/login', request)
      onAuthenticated(response.data)
    } catch (error) {
      setStatusTone('error')
      setStatus(getErrorMessage(error, isRegister ? 'Không thể tạo tài khoản.' : 'Không thể đăng nhập.'))
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
            <h2 className="mt-3 text-3xl font-semibold tracking-[-.04em]">{isRegister ? 'Tạo tài khoản' : 'Chào mừng trở lại'}</h2>
            <p className="mt-2 text-sm text-slate-500">
              {isRegister ? 'Tạo tài khoản bằng tên người dùng, email và mật khẩu.' : ''}
            </p>
          </div>

          <form onSubmit={(event) => void submitAuth(event)} className="space-y-4">
            {isRegister && (
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

            {status && <p className={`rounded-[6px] border px-3 py-2 text-sm ${statusTone === 'error' ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>{status}</p>}

            <button
              disabled={submitting}
              className="inline-flex h-12 w-full items-center justify-center gap-2 rounded-full bg-[#6672d8] text-sm font-bold text-white hover:-translate-y-0.5 hover:bg-[#505db8] disabled:bg-slate-300"
            >
              {isRegister ? <UserPlus size={16} /> : <LogIn size={16} />}
              {submitting ? 'Đang xử lý...' : isRegister ? 'Tạo tài khoản' : 'Đăng nhập'}
            </button>
          </form>

          <button
            type="button"
            onClick={() => {
              setMode(isRegister ? 'LOGIN' : 'REGISTER')
              setStatusTone('info')
              setStatus('')
            }}
            className="mt-5 text-sm font-bold text-[#6673b9] hover:text-[#475475]"
          >
            {isRegister ? 'Đã có tài khoản? Đăng nhập' : 'Chưa có tài khoản? Tạo tài khoản'}
          </button>
        </section>
      </div>
    </main>
  )
}
