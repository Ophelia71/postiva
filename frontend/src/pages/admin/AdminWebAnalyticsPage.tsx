import { BarChart3, RefreshCw } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { AdminError, AdminLoading } from '../../components/AdminUi'
import { Button } from '../../components/UiPrimitives'
import { useAdminResource } from '../../hooks/useAdminResource'

type Dashboard = {
  from: string
  to: string
  trackingStartedAt: string | null
  sessions: {
    sessions: number
    visitors: number
    pageViews: number
    bounces: number
    newSessions: number
    returningSessions: number
    durationSeconds: number
    identifiedUsers: number
  }
  bounceRatePercent: number | null
  averageSessionDurationSeconds: number | null
  pagesPerSession: number | null
  visitorMix: { newVisitors: number; returningVisitors: number }
  sources: { source: string; sessions: number }[]
  scroll: { views: number; depth25: number; depth50: number; depth75: number; depth100: number }
  registrations: number
  attributedRegistrations: number
  signUpRatePercent: number | null
  payments: { orders: number; buyers: number; grossVnd: number }
  refundsVnd: number
  attributedBuyers: number
  purchaseRatePercent: number | null
  averageOrderValueVnd: number | null
}

const integer = (value: number) => new Intl.NumberFormat('vi-VN').format(value)
const decimal = (value: number | null | undefined, suffix = '') => value == null || !Number.isFinite(value) ? '—' : `${new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)}${suffix}`
const money = (value: number | null | undefined) => value == null || !Number.isFinite(value) ? '—' : new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND', maximumFractionDigits: 0 }).format(value)
const sourceNames: Record<string, string> = {
  DIRECT: 'Truy cập trực tiếp', ORGANIC: 'Tìm kiếm tự nhiên', SOCIAL: 'Mạng xã hội',
  REFERRAL: 'Giới thiệu', PAID: 'Quảng cáo', EMAIL: 'Email', OTHER: 'Khác',
}

export function AdminWebAnalyticsPage() {
  const [days, setDays] = useState(30)
  const report = useAdminResource<Dashboard>(`/admin/analytics?days=${days}`)
  const data = report.data
  const hasTraffic = Boolean(data?.sessions.sessions)

  return <section className="space-y-6">
    <header className="flex flex-wrap items-start justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold sm:text-3xl">Phân tích web</h1>
      </div>
      <Button type="button" variant="secondary" onClick={report.reload} disabled={report.loading}><RefreshCw size={16} aria-hidden="true" />Làm mới</Button>
    </header>

    <div className="flex flex-wrap gap-2" role="group" aria-label="Khoảng thời gian báo cáo">
      {[7, 30, 90].map((value) => <button key={value} type="button" onClick={() => setDays(value)}
        aria-pressed={days === value}
        className={`min-h-11 rounded-xl border px-4 text-sm font-semibold ${days === value ? 'border-[var(--accent)] bg-[var(--soft-bg)] text-[var(--accent-strong)]' : 'border-[var(--app-border)] bg-[var(--panel-bg)] text-[var(--muted-text)]'}`}>{value} ngày</button>)}
    </div>

    {report.loading ? <AdminLoading /> : report.error ? <AdminError message={report.error} onRetry={report.reload} /> : data && <>
      <p className="text-xs text-[var(--muted-text)]">{new Date(data.from).toLocaleDateString('vi-VN')} – {new Date(data.to).toLocaleDateString('vi-VN')}</p>
      {!hasTraffic && <div className="rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 text-sm text-[var(--muted-text)]">
        {data.trackingStartedAt ? 'Không có lượt truy cập trong kỳ này.' : 'Chưa ghi nhận lượt truy cập. Lượt của admin không được tính.'}
      </div>}

      {hasTraffic && <><Panel title="Hành vi người dùng">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Metric label="Tỷ lệ thoát" value={decimal(data.bounceRatePercent, '%')} />
          <Metric label="Thời lượng phiên" value={decimal(data.averageSessionDurationSeconds, ' giây')} />
          <Metric label="Trang / phiên" value={decimal(data.pagesPerSession)} />
          <Metric label="Lượt xem trang" value={integer(data.sessions.pageViews)} />
        </div>
        <h3 className="mt-5 text-sm font-bold">Độ sâu cuộn trang</h3>
        <div className="mt-3 grid gap-3 sm:grid-cols-4">
          {([25, 50, 75, 100] as const).map((depth) => <Metric key={depth} label={`Cuộn ≥ ${depth}%`}
            value={data.scroll.views ? decimal(data.scroll[`depth${depth}`] * 100 / data.scroll.views, '%') : '—'} />)}
        </div>
      </Panel>

      <Panel title="Lưu lượng truy cập">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Metric label="Khách truy cập" value={integer(data.sessions.visitors)} />
          <Metric label="Phiên" value={integer(data.sessions.sessions)} />
          <Metric label="Khách mới" value={integer(data.visitorMix.newVisitors)} />
          <Metric label="Khách quay lại" value={integer(data.visitorMix.returningVisitors)} />
        </div>
        {data.sources.length > 0 && <><h3 className="mt-5 text-sm font-bold">Nguồn truy cập</h3>
          <div className="mt-3 space-y-3">{data.sources.map((item) => <div key={item.source}>
              <div className="flex justify-between gap-3 text-sm"><span>{sourceNames[item.source] ?? item.source}</span><strong>{integer(item.sessions)} phiên</strong></div>
              <div className="mt-1 h-2 rounded-full bg-[var(--soft-bg)]"><div className="h-2 rounded-full bg-[var(--accent)]" style={{ width: `${Math.max(2, item.sessions * 100 / data.sessions.sessions)}%` }} /></div>
            </div>)}</div></>}
      </Panel>
      </>}

      <Panel title="Chuyển đổi">
        <div className="grid gap-3 sm:grid-cols-2 xl:grid-cols-4">
          <Metric label="Tỷ lệ đăng ký" value={decimal(data.signUpRatePercent, '%')} />
          <Metric label="Tài khoản mới" value={integer(data.registrations)} />
          <Metric label="Tỷ lệ mua Pro" value={decimal(data.purchaseRatePercent, '%')} />
          <Metric label="Giá trị đơn trung bình" value={money(data.averageOrderValueVnd)} />
          <Metric label="Người mua Pro" value={integer(data.payments.buyers)} />
          <Metric label="Giao dịch thành công" value={integer(data.payments.orders)} />
          <Metric label="Doanh thu đã thanh toán" value={money(data.payments.grossVnd)} />
          <Metric label="Hoàn tiền đã xử lý" value={money(data.refundsVnd)} />
        </div>
        {!hasTraffic && <p className="mt-3 text-xs text-[var(--muted-text)]">— = chưa có dữ liệu truy cập hoặc giao dịch để tính tỷ lệ.</p>}
      </Panel>
    </>}
  </section>
}

function Panel({ title, children }: { title: string; children: ReactNode }) {
  return <section className="rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5">
    <div className="mb-4 flex items-center gap-3"><BarChart3 className="text-[var(--accent-strong)]" size={20} aria-hidden="true" /><h2 className="font-bold">{title}</h2></div>
    {children}
  </section>
}

function Metric({ label, value }: { label: string; value: string }) {
  return <div className="rounded-xl bg-[var(--soft-bg)] p-4"><p className="text-xs text-[var(--muted-text)]">{label}</p><p className="mt-2 break-words text-lg font-bold">{value}</p></div>
}
