import { useEffect, useState } from 'react'
import { useAppDataResource } from '../lib/appDataApi'
import { usePreferences } from '../lib/preferences'

type Summary = { plan: 'FREE' | 'PRO'; limit: number; used: number; reserved: number; remaining: number; endsAt: string }
type Usage = { id: string; status: 'RESERVED' | 'SUCCEEDED' | 'FAILED' | 'UNKNOWN'; createdAt: string }
type History = { items: Usage[]; total: number }

export function UsagePanel({ refreshVersion }: { refreshVersion: number }) {
  const { t } = usePreferences()
  const { data, loading, error, reload } = useAppDataResource<Summary>('/usage')
  const [showHistory, setShowHistory] = useState(false)
  useEffect(() => { if (refreshVersion > 0) reload() }, [refreshVersion, reload])

  return <aside aria-label={t('Gói và lượt AI', 'Plan and AI usage')} className="mb-5 rounded-xl border border-slate-200 bg-white p-4 text-sm text-slate-700">
    {loading ? <p role="status">{t('Đang tải lượt AI…', 'Loading AI usage…')}</p> : error ? <p role="alert">
      {t('Chưa tải được số lượt AI.', 'Could not load AI usage.')}
      <button type="button" onClick={reload} className="ml-2 min-h-11 underline focus-visible:outline-2">{t('Thử lại', 'Retry')}</button>
    </p> : data && <>
      <div className="flex flex-wrap items-center justify-between gap-x-4 gap-y-1">
        <p role="status"><strong>{data.plan === 'PRO' ? 'Pro' : 'Free'}</strong> · {t(`Còn ${data.remaining}/${data.limit} lượt AI`, `${data.remaining}/${data.limit} AI generations left`)}
          {data.reserved > 0 && <span> · {t(`${data.reserved} lượt đang xử lý`, `${data.reserved} in progress`)}</span>}
        </p>
        <button type="button" aria-expanded={showHistory} aria-controls="ai-usage-history" onClick={() => setShowHistory(!showHistory)} className="min-h-11 font-semibold underline focus-visible:outline-2">
          {showHistory ? t('Ẩn lịch sử', 'Hide history') : t('Lịch sử sử dụng', 'Usage history')}
        </button>
      </div>
      <p className="text-slate-600">{t('Kỳ hiện tại kết thúc', 'Current period ends')} {new Date(data.endsAt).toLocaleString('vi-VN')}.</p>
      <p className="mt-2 text-xs leading-5 text-slate-600">{t('Dùng thử Free: 30 lượt tạo bài AI mỗi kỳ 30 ngày. Pro có 50 lượt mỗi kỳ; chỉ bài được lưu thành công mới trừ lượt.', 'Free trial: 30 AI generations per 30-day period. Pro includes 50 per period; only saved posts use a generation.')}</p>
      {data.remaining === 0 && <p className="mt-2 font-medium">{t('Đã hết lượt AI khả dụng. Bạn vẫn có thể chỉnh sửa, đăng và lên lịch bài viết.', 'No AI generations available. You can still edit, publish and schedule posts.')}</p>}
    </>}
    {showHistory && <UsageHistory key={refreshVersion} />}
  </aside>
}

function UsageHistory() {
  const { t } = usePreferences()
  const [page, setPage] = useState(0)
  const { data, loading, error, reload } = useAppDataResource<History>(`/usage/requests?page=${page}&size=10`)
  const labels = {
    RESERVED: t('Đang xử lý', 'Processing'), SUCCEEDED: t('Đã tạo bài · 1 lượt', 'Post created · 1 generation'),
    FAILED: t('Không thành công · không trừ lượt', 'Failed · no quota charged'),
    UNKNOWN: t('Bị gián đoạn · không trừ lượt', 'Interrupted · no quota charged'),
  }
  return <div id="ai-usage-history" className="mt-3 border-t border-slate-200 pt-3" aria-busy={loading}>
    {loading ? <p role="status">{t('Đang tải lịch sử…', 'Loading history…')}</p> : error ? <p role="alert">
      {t('Chưa tải được lịch sử.', 'Could not load history.')} <button type="button" onClick={reload} className="min-h-11 underline">{t('Thử lại', 'Retry')}</button>
    </p> : <>
      {data?.items.length === 0 && <p>{t('Chưa có yêu cầu AI trong lịch sử.', 'No AI requests yet.')}</p>}
      <ul className="space-y-2">{data?.items.map((item) => <li key={item.id} className="flex flex-wrap justify-between gap-2">
        <time dateTime={item.createdAt}>{new Date(item.createdAt).toLocaleString('vi-VN')}</time><span>{labels[item.status]}</span>
      </li>)}</ul>
      {(data?.total ?? 0) > 10 && <div className="mt-2 flex items-center gap-4">
        <button type="button" disabled={page === 0} onClick={() => setPage(page - 1)} className="min-h-11 underline disabled:opacity-40">{t('Trước', 'Previous')}</button>
        <span>{t(`Trang ${page + 1}`, `Page ${page + 1}`)}</span>
        <button type="button" disabled={(page + 1) * 10 >= (data?.total ?? 0)} onClick={() => setPage(page + 1)} className="min-h-11 underline disabled:opacity-40">{t('Sau', 'Next')}</button>
      </div>}
    </>}
  </div>
}
