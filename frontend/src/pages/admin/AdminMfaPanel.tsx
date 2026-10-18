import { useEffect, useRef, useState, type FormEvent } from 'react'
import QRCode from 'qrcode'
import { Button } from '../../components/UiPrimitives'
import { useAdminResource } from '../../hooks/useAdminResource'
import { api } from '../../lib/api'
import { getErrorMessage } from '../../lib/errors'

type MfaStatus = { enabled: boolean; configured: boolean; recoveryCodesRemaining: number }
type Setup = { secret: string; otpauthUri: string; expiresAt: string }
type Recovery = { recoveryCodes: string[] }

export function AdminMfaPanel({ onFinished }: { onFinished: () => void }) {
  const status = useAdminResource<MfaStatus>('/admin/security/mfa')
  const [setup, setSetup] = useState<Setup | null>(null)
  const [codes, setCodes] = useState<string[]>([])
  const [saved, setSaved] = useState(false)
  const [password, setPassword] = useState('')
  const [code, setCode] = useState('')
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [disableConfirmed, setDisableConfirmed] = useState(false)
  const inFlight = useRef(false)
  const recoveryDialog = useRef<HTMLDialogElement>(null)

  useEffect(() => {
    if (!codes.length) return
    const dialog = recoveryDialog.current
    dialog?.showModal()
    return () => dialog?.close()
  }, [codes])

  useEffect(() => {
    if (!codes.length || saved) return
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault() }
    window.addEventListener('beforeunload', warn)
    return () => window.removeEventListener('beforeunload', warn)
  }, [codes, saved])

  async function perform(action: 'setup' | 'confirm' | 'recovery-codes' | 'disable') {
    if (inFlight.current) return
    inFlight.current = true
    setPending(true)
    setError('')
    try {
      const payload = action === 'setup' ? { currentPassword: password }
        : action === 'confirm' ? { code: code.trim() } : { currentPassword: password, code: code.trim() }
      if (action === 'setup') {
        const response = await api.post<Setup>('/admin/security/mfa/setup', payload)
        setSetup(response.data)
        setPassword('')
      } else if (action === 'disable') {
        await api.post('/admin/security/mfa/disable', payload)
        setPassword(''); setCode('')
        onFinished()
      } else {
        const response = await api.post<Recovery>(`/admin/security/mfa/${action}`, payload)
        setCodes(response.data.recoveryCodes)
        setSaved(false)
        setSetup(null)
        setPassword(''); setCode('')
      }
    } catch (failure) {
      setError(getErrorMessage(failure, 'Không thể cập nhật MFA. Vui lòng thử lại.'))
    } finally { inFlight.current = false; setPending(false) }
  }

  function submit(event: FormEvent<HTMLFormElement>, action: 'setup' | 'confirm' | 'recovery-codes') {
    event.preventDefault()
    void perform(action)
  }

  function download() {
    const blob = new Blob(['Postiva - mã khôi phục MFA (mỗi mã chỉ dùng một lần)\n\n' + codes.join('\n')], { type: 'text/plain;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url; link.download = 'postiva-ma-khoi-phuc.txt'; link.click()
    URL.revokeObjectURL(url)
  }

  return <section className="space-y-4 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 sm:p-7" aria-busy={pending}>
    <h2 className="text-xl font-bold">Xác thực hai bước (MFA)</h2>
    {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{error}</p>}
    {codes.length > 0 ? <dialog ref={recoveryDialog} aria-labelledby="mfa-recovery-title" onCancel={(event) => event.preventDefault()} className="m-auto max-h-[90dvh] w-[calc(100%-2rem)] max-w-lg space-y-4 overflow-y-auto rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-6 text-[var(--app-text)] shadow-xl">
      <h3 id="mfa-recovery-title" className="text-xl font-bold">Lưu mã khôi phục</h3>
      <p role="status" className="text-sm leading-6">Bảo mật đã được cập nhật. Lưu các mã này ngay; chúng chỉ hiển thị một lần. Mỗi mã thay cho mã TOTP khi đăng nhập và vẫn cần mật khẩu.</p>
      <ul aria-label="Mã khôi phục" className="space-y-2 rounded-xl bg-[var(--soft-bg)] p-4 font-mono text-xs sm:text-sm">{codes.map((value) => <li key={value} className="break-all select-all">{value}</li>)}</ul>
      <Button variant="secondary" type="button" onClick={download}>Tải mã khôi phục</Button>
      <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={saved} onChange={(event) => setSaved(event.target.checked)} />Tôi đã lưu mã ở nơi an toàn.</label>
      <p className="text-xs leading-5 text-[var(--muted-text)]">Các phiên cũ đã hết hiệu lực. Lần đăng nhập tiếp theo dùng mã TOTP mới; mã vừa dùng để xác nhận không thể dùng lại.</p>
      <Button type="button" disabled={!saved} onClick={onFinished}>Đã lưu, đăng nhập lại</Button>
    </dialog> : status.loading ? <p role="status">Đang tải trạng thái MFA…</p>
      : status.error ? <><p role="alert" className="text-sm text-rose-800">{status.error}</p><Button variant="secondary" type="button" onClick={status.reload}>Thử lại</Button></>
      : status.data && <>
        <p className="text-sm leading-6">{status.data.enabled ? `MFA đang bật. Còn ${status.data.recoveryCodesRemaining} mã khôi phục.` : 'Admin cần bật MFA trước khi quản lý người dùng. Quét mã QR bằng ứng dụng xác thực, rồi nhập mã để hoàn tất.'}</p>
        {!status.data.configured && <p role="alert" className="text-sm text-rose-800">Máy chủ chưa có khóa mã hóa MFA. Liên hệ người vận hành để cấu hình.</p>}
        {setup ? <form onSubmit={(event) => submit(event, 'confirm')} className="space-y-4">
          <p className="text-sm leading-6">Trong ứng dụng Authenticator, chọn thêm tài khoản bằng cách quét mã QR. Mã này chứa khóa bí mật, không chia sẻ hoặc chụp ảnh.</p>
          <MfaQrCode uri={setup.otpauthUri} />
          <details className="text-sm"><summary className="cursor-pointer font-semibold">Không quét được? Nhập khóa thủ công</summary><p className="mt-2 text-xs text-[var(--muted-text)]">Chọn loại mã theo thời gian trong ứng dụng Authenticator.</p><p className="mt-2 break-all rounded-xl bg-[var(--soft-bg)] p-4 font-mono select-all">{setup.secret}</p></details>
          <p className="text-xs text-[var(--muted-text)]">Hết hạn lúc {new Date(setup.expiresAt).toLocaleTimeString('vi-VN')}. Bật giờ tự động trên điện thoại.</p>
          <CodeField value={code} onChange={setCode} disabled={pending} totpOnly />
          <div className="flex flex-wrap gap-3"><Button type="submit" disabled={pending}>{pending ? 'Đang xác nhận…' : 'Xác nhận và bật MFA'}</Button><Button variant="secondary" type="button" disabled={pending} onClick={() => { setSetup(null); setCode('') }}>Thiết lập lại</Button></div>
        </form> : <form onSubmit={(event) => submit(event, status.data!.enabled ? 'recovery-codes' : 'setup')} className="space-y-4">
          <label className="block text-sm font-semibold">Mật khẩu hiện tại<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required maxLength={72} disabled={pending} className={inputClass} /></label>
          {status.data.enabled && <CodeField value={code} onChange={setCode} disabled={pending} />}
          <Button type="submit" disabled={pending || !status.data.configured}>{pending ? 'Đang xử lý…' : status.data.enabled ? 'Tạo lại mã khôi phục' : 'Bắt đầu thiết lập MFA'}</Button>
          {status.data.enabled && <div className="space-y-3 border-t border-[var(--app-border)] pt-4">
            <label className="flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={disableConfirmed} disabled={pending} onChange={(event) => setDisableConfirmed(event.target.checked)} />Tôi hiểu tắt MFA sẽ thu hồi phiên và phải thiết lập lại trước khi quản trị.</label>
            <Button variant="danger" type="button" disabled={pending || !disableConfirmed || !password || !code.trim()} onClick={() => void perform('disable')}>Tắt MFA để thiết lập lại</Button>
          </div>}
        </form>}
      </>}
  </section>
}

function MfaQrCode({ uri }: { uri: string }) {
  const canvas = useRef<HTMLCanvasElement>(null)
  const [error, setError] = useState(false)

  useEffect(() => {
    const element = canvas.current
    if (!element) return
    let active = true
    setError(false)
    void QRCode.toCanvas(element, uri, { width: 224, margin: 3, errorCorrectionLevel: 'M' })
      .catch(() => { if (active) setError(true) })
    return () => {
      active = false
      element.getContext('2d')?.clearRect(0, 0, element.width, element.height)
    }
  }, [uri])

  return <div className="space-y-2">
    <p className="text-xs font-semibold text-[var(--muted-text)]">Mã QR thiết lập — chỉ quét bằng điện thoại của bạn</p>
    <div className="inline-flex rounded-xl border border-[var(--app-border)] bg-white p-2"><canvas ref={canvas} role="img" aria-label="Mã QR để thêm tài khoản Postiva vào ứng dụng xác thực" /></div>
    {error && <p role="alert" className="text-sm text-rose-800">Không tạo được mã QR. Hãy nhập khóa thủ công bên dưới.</p>}
  </div>
}

const inputClass = 'mt-2 min-h-12 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 text-base outline-none focus:border-[var(--color-primary)] focus:ring-2 focus:ring-[var(--focus-ring)]'
function CodeField({ value, onChange, disabled, totpOnly }: { value: string; onChange: (value: string) => void; disabled: boolean; totpOnly?: boolean }) {
  return <label className="block text-sm font-semibold">{totpOnly ? 'Mã 6 số từ ứng dụng' : 'Mã TOTP hoặc mã khôi phục'}<input type="text" autoComplete="one-time-code" inputMode={totpOnly ? 'numeric' : 'text'} pattern={totpOnly ? '[0-9]{6}' : undefined} value={value} onChange={(event) => onChange(event.target.value)} required maxLength={64} disabled={disabled} className={inputClass} /></label>
}
