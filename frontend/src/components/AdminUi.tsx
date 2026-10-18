import { ChevronLeft, ChevronRight, RefreshCw } from 'lucide-react'
import { type AccountStatus, type AdminPage } from '../lib/adminApi'
import { Button } from './UiPrimitives'

export function AccountStatusBadge({ status }: { status: AccountStatus }) {
  return (
    <span className={`inline-flex w-fit items-center gap-1.5 rounded-full px-3 py-1 text-xs font-semibold ${status === 'ACTIVE' ? 'bg-emerald-50 text-emerald-800' : 'bg-rose-50 text-rose-800'}`}>
      <span aria-hidden="true" className={`h-1.5 w-1.5 rounded-full ${status === 'ACTIVE' ? 'bg-emerald-600' : 'bg-rose-600'}`} />
      {status === 'ACTIVE' ? 'Đang hoạt động' : 'Đã khóa'}
    </span>
  )
}

export function AdminPagination({ data, onPageChange }: {
  data: Pick<AdminPage<unknown>, 'page' | 'size' | 'totalElements' | 'totalPages'>
  onPageChange: (page: number) => void
}) {
  if (!data.totalElements) return null
  const first = data.page * data.size + 1
  const last = Math.min((data.page + 1) * data.size, data.totalElements)
  return (
    <nav aria-label="Phân trang" className="flex flex-wrap items-center justify-between gap-3 border-t border-[var(--app-border)] p-4 text-sm">
      <span className="tabular-nums text-[var(--muted-text)]">{first}–{last} / {data.totalElements}</span>
      <div className="flex items-center gap-2">
        <Button variant="secondary" type="button" disabled={data.page <= 0} onClick={() => onPageChange(data.page - 1)} aria-label="Trang trước">
          <ChevronLeft size={16} aria-hidden="true" />
        </Button>
        <span className="min-w-16 text-center tabular-nums">{data.page + 1} / {Math.max(1, data.totalPages)}</span>
        <Button variant="secondary" type="button" disabled={data.page + 1 >= data.totalPages} onClick={() => onPageChange(data.page + 1)} aria-label="Trang sau">
          <ChevronRight size={16} aria-hidden="true" />
        </Button>
      </div>
    </nav>
  )
}

export function AdminLoading() {
  return <div role="status" className="flex min-h-48 items-center justify-center gap-3 p-6 text-sm text-[var(--muted-text)]">
    <RefreshCw className="motion-safe:animate-spin" size={18} aria-hidden="true" />Đang tải dữ liệu…
  </div>
}

export function AdminError({ message, onRetry }: { message: string; onRetry: () => void }) {
  return <div className="space-y-4 rounded-xl border border-rose-200 bg-rose-50 p-5 text-rose-900">
    <p role="alert" className="text-sm">{message}</p>
    <Button type="button" variant="secondary" onClick={onRetry}>Thử lại</Button>
  </div>
}
