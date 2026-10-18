import { CircleDollarSign, Power, RefreshCw, ShieldAlert } from 'lucide-react'
import { useState } from 'react'
import { AdminError, AdminLoading } from '../../components/AdminUi'
import { Button, Field, StatusMessage } from '../../components/UiPrimitives'
import { useAdminResource } from '../../hooks/useAdminResource'
import { api } from '../../lib/api'
import { type AiBudgetStatus, type AiBudgetWindow } from '../../lib/adminApi'
import { getErrorMessage } from '../../lib/errors'

export function AdminAiBudgetPage() {
  const budget = useAdminResource<AiBudgetStatus>('/admin/ai-budget')
  const [reason, setReason] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  async function toggle() {
    if (!budget.data || !reason.trim()) {
      setError('Nhập lý do trước khi bật hoặc tắt dịch vụ AI.')
      return
    }
    setSaving(true)
    setError('')
    try {
      await api.patch<AiBudgetStatus>('/admin/ai-budget/switch', {
        enabled: !budget.data.databaseEnabled,
        reason: reason.trim(),
      })
      setReason('')
      budget.reload()
    } catch (caught) {
      setError(getErrorMessage(caught, 'Không thể cập nhật công tắc AI.'))
    } finally {
      setSaving(false)
    }
  }

  return <section className="space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <p className="mb-2 text-xs font-semibold uppercase tracking-widest text-[var(--accent-strong)]">Vận hành nền tảng</p>
        <h1 className="text-2xl font-bold sm:text-3xl">AI và ngân sách</h1>
        <p className="mt-2 max-w-2xl text-sm leading-6 text-[var(--muted-text)]">Theo dõi chi phí theo token và project OpenAI, xem khoản đang giữ chỗ và dừng yêu cầu mới khi cần.</p>
      </div>
      <Button type="button" variant="secondary" onClick={budget.reload} disabled={budget.loading}>
        <RefreshCw size={16} aria-hidden="true" />Làm mới
      </Button>
    </header>

    {budget.loading ? <AdminLoading /> : budget.error ? <AdminError message={budget.error} onRetry={budget.reload} /> : budget.data && <>
      <div className="grid gap-4 lg:grid-cols-2">
        <BudgetCard data={budget.data.daily} />
        <BudgetCard data={budget.data.monthly} />
      </div>

      <section className="grid gap-5 xl:grid-cols-[minmax(0,1fr)_minmax(320px,0.72fr)]">
        <div className="space-y-4 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5">
          <div className="flex items-center gap-3">
            <span className={`flex h-11 w-11 items-center justify-center rounded-xl ${budget.data.newRequestsAllowed ? 'bg-emerald-50 text-emerald-700' : 'bg-amber-50 text-amber-800'}`}>
              {budget.data.newRequestsAllowed ? <CircleDollarSign size={21} aria-hidden="true" /> : <ShieldAlert size={21} aria-hidden="true" />}
            </span>
            <div>
              <h2 className="font-bold">{budget.data.newRequestsAllowed ? 'AI đang nhận yêu cầu' : 'AI đang dừng yêu cầu mới'}</h2>
              <p className="text-xs text-[var(--muted-text)]">Công tắc runtime {budget.data.databaseEnabled ? 'đang bật' : 'đang tắt'} · biến môi trường {budget.data.environmentEnabled ? 'đang bật' : 'đang tắt'}</p>
            </div>
          </div>
          {!budget.data.environmentEnabled && <StatusMessage tone="warning">POSTIVA_AI_ENABLED đang tắt ở cấu hình backend. Bật công tắc tại đây sẽ không vượt qua lớp bảo vệ này.</StatusMessage>}
          {!budget.data.newRequestsAllowed && budget.data.enabled && <StatusMessage tone="warning">Ngân sách không còn đủ để giữ trước một yêu cầu tối đa; backend sẽ trả lỗi tạm dừng cho yêu cầu mới.</StatusMessage>}
          <dl className="grid grid-cols-2 gap-3 text-sm">
            <Metric label="Yêu cầu đã chốt theo token" value={budget.data.settledRequests.toLocaleString('vi-VN')} />
            <Metric label="Khoản chưa xác định" value={budget.data.unknownCharges.toLocaleString('vi-VN')} />
            <Metric label="Cơ sở theo dõi" value={budget.data.providerReconciliationEnabled ? 'Đối soát project OpenAI' : 'Ước lượng token'} />
            <Metric label="Đồng bộ project" value={budget.data.providerReconciliationEnabled ? 'Đang cấu hình' : 'Chưa cấu hình Admin API key/project'} />
          </dl>
          <p className="rounded-xl bg-[var(--soft-bg)] px-3 py-2.5 text-xs leading-5">
            <strong>Model {budget.data.configuredModel}</strong><br />
            Giá ước lượng mỗi triệu token: input ${budget.data.inputUsdPerMillionTokens} · đọc cache ${budget.data.cachedInputUsdPerMillionTokens} · ghi cache ${budget.data.cacheWriteInputUsdPerMillionTokens} · output ${budget.data.outputUsdPerMillionTokens}.
          </p>
          <p className="text-xs leading-5 text-[var(--muted-text)]">{budget.data.costBasis === 'ESTIMATED_FROM_PROVIDER_TOKEN_USAGE'
            ? 'Chi phí là ước lượng theo token và đơn giá cấu hình. Thêm OpenAI Organization Admin API key cùng project riêng để đối soát số tiền từ nhà cung cấp.'
            : 'Chi phí project được đồng bộ định kỳ từ Organization Costs API; chi phí từng yêu cầu vẫn được ước lượng theo token để chặn vượt ngân sách theo thời gian thực.'}</p>
        </div>

        <div className="space-y-4 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5">
          <div>
            <h2 className="font-bold">Công tắc dừng khẩn cấp</h2>
            <p className="mt-1 text-sm leading-5 text-[var(--muted-text)]">Yêu cầu đang chạy được phép hoàn tất. Thay đổi được ghi audit cùng lý do.</p>
          </div>
          <Field label="Lý do thao tác" value={reason} onChange={(event) => setReason(event.target.value)} maxLength={1000} placeholder="Ví dụ: tạm dừng để kiểm tra chi phí bất thường" />
          {error && <StatusMessage tone="error">{error}</StatusMessage>}
          <Button type="button" variant={budget.data.databaseEnabled ? 'danger' : 'primary'} onClick={toggle} disabled={saving || (!budget.data.databaseEnabled && !budget.data.environmentEnabled)} className="w-full">
            <Power size={16} aria-hidden="true" />{saving ? 'Đang cập nhật…' : budget.data.databaseEnabled ? 'Tắt tạo nội dung AI' : 'Bật lại tạo nội dung AI'}
          </Button>
        </div>
      </section>
    </>}
  </section>
}

function BudgetCard({ data }: { data: AiBudgetWindow }) {
  const used = Math.min(100, Math.max(0, data.percentUsed))
  const money = (value: number) => `$${value.toFixed(4)} USD`
  const lastSync = data.actualReconciledAt
    ? new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(data.actualReconciledAt))
    : 'Chưa có dữ liệu đối soát'
  return <article className="space-y-4 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5">
    <div className="flex items-start justify-between gap-3">
      <div><p className="text-xs font-semibold uppercase tracking-widest text-[var(--muted-text)]">Ngân sách {data.window === 'DAILY' ? 'hôm nay' : 'tháng này'}</p><p className="mt-1 text-2xl font-bold tabular-nums">{money(data.totalUsd)} <span className="text-sm font-medium text-[var(--muted-text)]">/ {money(data.budgetUsd)}</span></p></div>
      {data.warningSent && <span className="rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-900">Đã cảnh báo</span>}
    </div>
    <div aria-label={`${used.toFixed(0)}% ngân sách`} className="h-2 overflow-hidden rounded-full bg-[var(--soft-bg)]"><div className={`h-full rounded-full ${used >= 100 ? 'bg-rose-600' : used >= 80 ? 'bg-amber-500' : 'bg-emerald-600'}`} style={{ width: `${used}%` }} /></div>
    <dl className="grid grid-cols-2 gap-3 text-sm">
      <Metric label="Theo token" value={money(data.spentUsd)} />
      <Metric label="OpenAI project" value={money(data.actualProviderUsd)} />
      <Metric label="Đang giữ chỗ" value={money(data.reservedUsd)} />
      <Metric label="Đối soát gần nhất" value={lastSync} />
    </dl>
  </article>
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="min-w-0 rounded-xl bg-[var(--soft-bg)] px-3 py-2.5">
    <dt className="text-xs text-[var(--muted-text)]">{label}</dt>
    <dd className="mt-1 break-words text-sm font-semibold tabular-nums">{value}</dd>
  </div>
}
