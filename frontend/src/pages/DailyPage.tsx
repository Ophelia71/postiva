import { CalendarClock, Lightbulb, Link2, Sparkles } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../components/EmptyState'
import { HelpTip } from '../components/HelpTip'
import { PageHeader, PageState } from '../components/PageUi'
import { Stepper } from '../components/Stepper'
import { api } from '../lib/api'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'
import {
  aiUnitsFor,
  type CreateWeeklyPlanRequest,
  DAILY_API_READY,
  DAILY_USE_MOCK,
  draftText,
  ITEM_STATUS_LABELS,
  type PerformanceSummary,
  type PlanItemStatus,
  type WeeklyPlan,
  type WeeklyPlanItem,
  WEEKDAY_LABELS,
  WEEKDAY_NAMES,
} from '../lib/dailyApi'
import { installDailyMock } from '../lib/dailyMock'
import type { ShopProfile } from '../lib/shopProfile'
import { isDemoActive } from '../lib/demo'
import { getErrorMessage } from '../lib/errors'

if (DAILY_USE_MOCK) installDailyMock(api)

const BLOCKED = 'Bước này không thể bỏ qua, hãy setup lại.'
// In the Vite dev server every step can be skipped; production builds enforce the required ones.
const CAN_SKIP = import.meta.env.DEV
const STEPS = [{ label: 'Kênh & lịch' }, { label: 'Sản phẩm' }, { label: 'Xác nhận' }] as const
const DAY_ORDER = [1, 2, 3, 4, 5, 6, 7]
const SUPPORTED_BADGES = ['Facebook', 'Instagram', 'Threads']
const PLATFORM_NAMES: Record<string, string> = { FACEBOOK: 'Facebook', INSTAGRAM: 'Instagram', THREADS: 'Threads' }
const TONES = ['Thân thiện, gần gũi', 'Chuyên nghiệp, tin cậy', 'Trẻ trung, hài hước', 'Sang trọng, tinh tế']
const POLL_MS = 2500
const INPUT_CLASS = 'mt-1 h-11 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm font-normal'
const TEXTAREA_CLASS = 'mt-1 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 py-2 text-sm font-normal'
const PRIMARY_BUTTON = 'min-h-11 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0] disabled:bg-slate-400'
const SECONDARY_BUTTON = 'min-h-11 rounded-full border border-[var(--app-border)] px-4 text-sm font-bold hover:bg-[var(--soft-bg)] disabled:opacity-60'

function browserZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Ho_Chi_Minh'
  } catch {
    return 'Asia/Ho_Chi_Minh'
  }
}

function isoDay(date: Date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function nextMonday() {
  const today = new Date()
  const offset = ((8 - (today.getDay() || 7)) % 7) || 7
  return isoDay(new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset))
}

function formatDay(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day).toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' })
}

function formatDateTime(value: string | null) {
  if (!value) return 'Chưa có giờ'
  return new Date(value).toLocaleString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

/** datetime-local works in the browser's zone; plans default to the browser's zone too. */
function toLocalInput(value: string | null) {
  if (!value) return ''
  const date = new Date(value)
  return `${isoDay(date)}T${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

export function DailyPage() {
  // Until the backend ships, a production build must not call /weekly-plans at all.
  return DAILY_API_READY || DAILY_USE_MOCK || isDemoActive() ? <DailyHome /> : <DailyDisabled />
}

function DailyDisabled() {
  return (
    <section>
      <PageHeader title="Daily" subtitle="Lên kế hoạch và soạn sẵn bài cho cả tuần." />
      <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
        <EmptyState
          icon={<CalendarClock size={20} />}
          title="Daily chưa được bật"
          description="Tính năng này đang được thử nghiệm và chưa mở cho tài khoản của bạn. Khi được bật, Postiva sẽ phân tích bài cũ, lên ý tưởng và soạn sẵn bài cho cả tuần để bạn duyệt một lần."
        />
      </div>
    </section>
  )
}

type View = { kind: 'home' } | { kind: 'create' } | { kind: 'plan'; planId: string }

function DailyHome() {
  const channelsState = useAppDataResource<AppChannel[]>('/app-data/channels')
  const plansState = useAppDataResource<WeeklyPlan[]>('/weekly-plans')
  const [view, setView] = useState<View>({ kind: 'home' })

  if (channelsState.loading || plansState.loading) return <PageState title="Đang tải Daily..." />
  if (channelsState.error || plansState.error || !channelsState.data || !plansState.data) {
    return (
      <PageState
        title="Không tải được Daily"
        description={channelsState.error ?? plansState.error ?? undefined}
        onRetry={() => { channelsState.reload(); plansState.reload() }}
      />
    )
  }

  const channels = channelsState.data.filter((channel) => channel.active && SUPPORTED_BADGES.includes(channel.badge))
  const plans = plansState.data
  const backHome = () => { plansState.reload(); setView({ kind: 'home' }) }

  if (view.kind === 'create') {
    return <PlanWizard channels={channels} onCancel={() => setView({ kind: 'home' })} onCreated={(plan) => { plansState.reload(); setView({ kind: 'plan', planId: plan.id }) }} />
  }
  if (view.kind === 'plan') {
    return <PlanView planId={view.planId} onBack={backHome} onCreateNew={() => setView({ kind: 'create' })} />
  }

  if (channels.length === 0 && plans.length === 0) {
    return (
      <section>
        <PageHeader title="Daily" subtitle="Lên kế hoạch và soạn sẵn bài cho cả tuần." />
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
          <EmptyState
            icon={<Link2 size={20} />}
            title="Cần kết nối một kênh trước"
            description="Daily đọc bài cũ và đăng lên một Page hoặc tài khoản đã kết nối. Facebook Page ổn định nhất; Instagram và Threads đang thử nghiệm."
            actionLabel="Kết nối kênh"
            actionTo="/app/connections"
          />
        </div>
      </section>
    )
  }

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader title="Daily" subtitle="Postiva phân tích bài cũ, lên ý tưởng và soạn sẵn bài cho cả tuần. Bạn duyệt một lần là xong." />
        {plans.length > 0 && channels.length > 0 && (
          <button type="button" onClick={() => setView({ kind: 'create' })} className={PRIMARY_BUTTON}>Lên kế hoạch tuần</button>
        )}
      </div>
      <MockBanner />

      {plans.length === 0 ? (
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
          <EmptyState
            icon={<Sparkles size={20} />}
            title="Chưa có kế hoạch tuần nào"
            description="Chọn kênh và nhập sản phẩm một lần. Postiva xem bài nào của shop thu hút nhất, đề xuất 1–7 bài cho tuần tới kèm lý do, rồi soạn sẵn nội dung để bạn duyệt."
          />
          <div className="flex justify-center pb-8">
            <button type="button" onClick={() => setView({ kind: 'create' })} className={PRIMARY_BUTTON}>Lên kế hoạch tuần đầu tiên</button>
          </div>
        </div>
      ) : (
        <ul className="space-y-3">
          {plans.map((plan) => (
            <li key={plan.id}>
              <button
                type="button"
                onClick={() => setView({ kind: 'plan', planId: plan.id })}
                className="flex w-full flex-wrap items-center justify-between gap-3 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 text-left shadow-sm hover:bg-[var(--soft-bg)]"
              >
                <span className="min-w-0">
                  <span className="block font-bold">Tuần {formatDay(plan.startDate)} – {formatDay(plan.endDate)}</span>
                  <span className="block text-sm text-[var(--muted-text)]">
                    {PLATFORM_NAMES[plan.platform ?? ''] ?? 'Kênh'} · {plan.accountName ?? 'Kênh đã chọn'} · {plan.items.length} bài · {plan.goal}
                  </span>
                </span>
                <PlanStatusBadge status={plan.status} />
              </button>
            </li>
          ))}
        </ul>
      )}
    </section>
  )
}

function MockBanner() {
  // The demo shell has its own banner.
  if (!DAILY_USE_MOCK || isDemoActive()) return null
  return (
    <p className="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900">
      Dữ liệu mẫu (chỉ ở môi trường dev): kế hoạch và bản nháp được lưu tạm trong trình duyệt, tải lại trang là mất. Chưa có gì được đăng.
    </p>
  )
}

function PlanStatusBadge({ status }: { status: WeeklyPlan['status'] }) {
  const styles: Record<WeeklyPlan['status'], [string, string]> = {
    GENERATING: ['Đang soạn', 'bg-blue-50 text-blue-700'],
    READY: ['Chờ duyệt', 'bg-amber-50 text-amber-800'],
    APPROVED: ['Đã duyệt', 'bg-emerald-50 text-emerald-700'],
    FAILED: ['Lỗi', 'bg-rose-50 text-rose-700'],
  }
  const [label, className] = styles[status]
  return <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${className}`}>{label}</span>
}

function ItemStatusBadge({ status }: { status: PlanItemStatus }) {
  const className = status === 'PUBLISHED' || status === 'SCHEDULED'
    ? 'bg-emerald-50 text-emerald-700'
    : status === 'FAILED' ? 'bg-rose-50 text-rose-700'
      : status === 'SKIPPED' ? 'bg-slate-100 text-slate-500'
        : status === 'DRAFT' ? 'bg-amber-50 text-amber-800' : 'bg-blue-50 text-blue-700'
  return <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${className}`}>{ITEM_STATUS_LABELS[status]}</span>
}

/** Findings from past posts, shown before planning and next to every plan. */
function InsightsPanel({ summary, title = 'Postiva rút ra từ bài cũ' }: { summary: Partial<PerformanceSummary> | null; title?: string }) {
  if (!summary) return null
  const insights = summary.insights ?? []
  const topPosts = (summary.topPosts ?? []).filter((post) => post.excerpt)
  return (
    <div className="space-y-2 rounded-lg border border-[var(--app-border)] bg-[var(--soft-bg)] p-4 text-sm">
      <p className="flex items-center gap-2 font-bold"><Lightbulb size={16} aria-hidden="true" />{title}</p>
      {summary.warning && <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-amber-900">{summary.warning}</p>}
      {insights.length > 0 && <ul className="list-disc space-y-1 pl-5">{insights.map((line) => <li key={line}>{line}</li>)}</ul>}
      {topPosts.length > 0 && (
        <div>
          <p className="mt-2 font-semibold">Bài thu hút nhất</p>
          <ul className="mt-1 space-y-1 text-[var(--muted-text)]">
            {topPosts.map((post) => (
              <li key={post.excerpt}>“{post.excerpt}” · {Math.round(post.engagement)} điểm tương tác</li>
            ))}
          </ul>
        </div>
      )}
      {summary.postsAnalyzed !== undefined && (
        <p className="text-xs text-[var(--muted-text)]">
          Đã xem {summary.postsAnalyzed} bài gần nhất, {summary.postsWithMetrics ?? 0} bài có số liệu tương tác.
        </p>
      )}
    </div>
  )
}

type Form = {
  socialAccountId: string
  startDate: string
  postsPerWeek: number
  days: number[]
  useBestTime: boolean
  postTime: string
  productName: string
  productDescription: string
  price: string
  targetAudience: string
  highlights: string
  goal: string
  tone: string
  mediaIds: string[]
  mediaNames: Record<string, string>
}

function PlanWizard({ channels, onCancel, onCreated }: { channels: AppChannel[]; onCancel: () => void; onCreated: (plan: WeeklyPlan) => void }) {
  const usageState = useAppDataResource<{ remaining: number }>('/usage')
  const shopProfile = useAppDataResource<ShopProfile | null>('/shop-profile')
  const [form, setForm] = useState<Form>({
    socialAccountId: channels[0]?.id ?? '',
    startDate: nextMonday(),
    postsPerWeek: 5,
    days: [],
    useBestTime: true,
    postTime: '19:00',
    productName: '',
    productDescription: '',
    price: '',
    targetAudience: '',
    highlights: '',
    goal: '',
    tone: TONES[0],
    mediaIds: [],
    mediaNames: {},
  })
  const [step, setStep] = useState(0)
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [insights, setInsights] = useState<{ accountId: string; summary: PerformanceSummary | null; error: string } | null>(null)
  const [didPrefill, setDidPrefill] = useState(false)

  // The shop profile fills empty product fields once; the week can still be about something else.
  useEffect(() => {
    const profile = shopProfile.data
    if (!profile || didPrefill) return
    setDidPrefill(true)
    setForm((current) => ({
      ...current,
      productDescription: current.productDescription || profile.mainProducts || '',
      targetAudience: current.targetAudience || profile.targetCustomer || '',
      highlights: current.highlights || profile.highlights || '',
      price: current.price || profile.priceRange || '',
      tone: profile.defaultTone && TONES.includes(profile.defaultTone) ? profile.defaultTone : current.tone,
    }))
  }, [shopProfile.data, didPrefill])

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((current) => ({ ...current, [key]: value }))
  const selectedBadge = channels.find((channel) => channel.id === form.socialAccountId)?.badge
  const needsMedia = selectedBadge === 'Instagram'
  const needed = aiUnitsFor(form.postsPerWeek)
  const remaining = usageState.data?.remaining

  useEffect(() => {
    if (!form.socialAccountId) return
    let ignore = false
    setInsights(null)
    api.get<PerformanceSummary>('/weekly-plans/insights', { params: { socialAccountId: form.socialAccountId, timezone: browserZone() } })
      .then((response) => { if (!ignore) setInsights({ accountId: form.socialAccountId, summary: response.data, error: '' }) })
      .catch((loadError) => {
        if (!ignore) setInsights({ accountId: form.socialAccountId, summary: null, error: getErrorMessage(loadError, 'Không đọc được bài cũ của kênh này.') })
      })
    return () => { ignore = true }
  }, [form.socialAccountId])

  function issue(index: number): string | null {
    if (index === 0) {
      if (!form.socialAccountId) return 'Chọn kênh để đăng.'
      if (!form.startDate) return 'Chọn ngày bắt đầu tuần.'
      if (!form.useBestTime && !form.postTime) return 'Chọn giờ đăng.'
    }
    if (index === 1) {
      if (!form.productName.trim() || !form.productDescription.trim() || !form.targetAudience.trim() || !form.highlights.trim()) {
        return 'Nhập đủ các ô có dấu *.'
      }
      if (!form.goal.trim()) return 'Nhập mục tiêu của tuần.'
      if (needsMedia && form.mediaIds.length === 0) return 'Instagram cần ít nhất một ảnh hoặc video.'
    }
    return null
  }

  function go(target: number) {
    if (target <= step || CAN_SKIP) {
      setNotice('')
      setStep(target)
      return
    }
    for (let index = step; index < target; index += 1) {
      const problem = issue(index)
      if (problem) {
        setNotice(`${BLOCKED} ${problem}`)
        setStep(index)
        return
      }
    }
    setNotice('')
    setStep(target)
  }

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) return
    setUploading(true)
    setError('')
    try {
      const added = await Promise.all(Array.from(files).map(async (file) => {
        const data = new FormData()
        data.append('file', file)
        const response = await api.post<{ id: string; fileName: string }>('/files/upload', data, { headers: { 'Content-Type': 'multipart/form-data' } })
        return response.data
      }))
      setForm((current) => ({
        ...current,
        mediaIds: [...current.mediaIds, ...added.map((item) => item.id)].slice(0, 10),
        mediaNames: { ...current.mediaNames, ...Object.fromEntries(added.map((item) => [item.id, item.fileName])) },
      }))
    } catch (uploadError) {
      setError(getErrorMessage(uploadError, 'Không tải được ảnh hoặc video.'))
    } finally {
      setUploading(false)
    }
  }

  async function submit() {
    const blocked = [0, 1].find((index) => issue(index))
    if (blocked !== undefined) {
      setNotice(`${BLOCKED} ${issue(blocked)}`)
      setStep(blocked)
      return
    }
    setSaving(true)
    setError('')
    const request: CreateWeeklyPlanRequest = {
      socialAccountId: form.socialAccountId,
      startDate: form.startDate,
      postsPerWeek: form.postsPerWeek,
      daysOfWeek: form.days.length > 0 ? form.days : undefined,
      postTime: form.useBestTime ? undefined : form.postTime,
      timezone: browserZone(),
      productName: form.productName.trim(),
      productDescription: form.productDescription.trim(),
      price: form.price.trim() || undefined,
      targetAudience: form.targetAudience.trim(),
      highlights: form.highlights.trim(),
      goal: form.goal.trim(),
      tone: form.tone,
      imageIds: form.mediaIds,
    }
    try {
      const response = await api.post<WeeklyPlan>('/weekly-plans', request)
      onCreated(response.data)
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Không bắt đầu được kế hoạch. Vui lòng thử lại.'))
    } finally {
      setSaving(false)
    }
  }

  const last = step === STEPS.length - 1
  const showNotice = notice && issue(step)
  const currentInsights = insights?.accountId === form.socialAccountId ? insights : null

  return (
    <section className="mx-auto max-w-3xl space-y-4">
      <PageHeader title="Lên kế hoạch tuần" subtitle="Ba bước. Postiva soạn sẵn cả tuần, bạn duyệt trước khi đăng." />
      <Stepper label="Các bước lên kế hoạch" steps={STEPS} current={step} isDone={(index) => index < step && !issue(index)} onGo={go} />
      {showNotice && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">{notice}</p>}
      {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">{error}</p>}

      <div className="space-y-4 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm">
        {step === 0 && (
          <>
            <h2 className="text-base font-bold">Đăng lên kênh nào?</h2>
            <div className="grid gap-2 sm:grid-cols-2">
              {channels.map((channel) => (
                <button
                  key={channel.id}
                  type="button"
                  aria-pressed={form.socialAccountId === channel.id}
                  onClick={() => set('socialAccountId', channel.id)}
                  className={`flex min-h-14 flex-col justify-center rounded-xl border px-4 py-2 text-left ${form.socialAccountId === channel.id ? 'border-[#8594ea] bg-[var(--soft-bg)]' : 'border-[var(--app-border)] hover:bg-[var(--soft-bg)]'}`}
                >
                  <span className="font-bold">{channel.name}</span>
                  <span className="text-xs text-[var(--muted-text)]">{channel.badge === 'Facebook' ? 'Facebook Page' : channel.badge}{form.socialAccountId === channel.id ? ' · Đã chọn' : ''}</span>
                </button>
              ))}
            </div>

            {!currentInsights && form.socialAccountId && <p className="text-sm text-[var(--muted-text)]">Đang đọc bài cũ của kênh...</p>}
            {currentInsights?.error && <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">{currentInsights.error} Kế hoạch vẫn tạo được, chỉ là dựa vào thông tin sản phẩm.</p>}
            {currentInsights?.summary && <InsightsPanel summary={currentInsights.summary} />}

            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm font-bold">Tuần bắt đầu từ
                <input type="date" value={form.startDate} min={isoDay(new Date())} onChange={(event) => set('startDate', event.target.value)} className={INPUT_CLASS} />
              </label>
              <label className="block text-sm font-bold">Số bài trong tuần
                <select value={form.postsPerWeek} onChange={(event) => set('postsPerWeek', Number(event.target.value))} className={INPUT_CLASS}>
                  {DAY_ORDER.map((count) => <option key={count} value={count}>{count} bài</option>)}
                </select>
              </label>
            </div>

            <div role="group" aria-label="Ngày muốn đăng" className="space-y-1 text-sm font-bold">
              <span className="flex items-center gap-1">
                Ngày muốn đăng (tùy chọn)
                <HelpTip label="Giải thích chọn ngày">Để trống thì Postiva chọn ngày theo số liệu bài cũ (ngày nào tương tác tốt hơn được ưu tiên), rồi rải đều phần còn lại trong tuần.</HelpTip>
              </span>
              <div className="flex flex-wrap gap-2">
                {DAY_ORDER.map((day) => {
                  const active = form.days.includes(day)
                  return (
                    <button
                      key={day}
                      type="button"
                      aria-pressed={active}
                      aria-label={WEEKDAY_NAMES[day]}
                      onClick={() => set('days', active ? form.days.filter((value) => value !== day) : [...form.days, day].sort())}
                      className={`min-h-11 min-w-11 rounded-full border px-3 font-bold ${active ? 'border-[#8594ea] bg-[var(--soft-bg)]' : 'border-[var(--app-border)]'}`}
                    >
                      {WEEKDAY_LABELS[day]}
                    </button>
                  )
                })}
              </div>
            </div>

            <fieldset className="space-y-2 text-sm">
              <legend className="font-bold">Giờ đăng</legend>
              <label className="flex items-center gap-2">
                <input type="radio" checked={form.useBestTime} onChange={() => set('useBestTime', true)} />
                Theo giờ hiệu quả nhất từ bài cũ{currentInsights?.summary?.bestHour != null ? ` (${currentInsights.summary.bestHour}:00)` : ' (mặc định 19:00)'}
              </label>
              <label className="flex flex-wrap items-center gap-2">
                <input type="radio" checked={!form.useBestTime} onChange={() => set('useBestTime', false)} />
                Giờ cố định
                <input type="time" aria-label="Giờ cố định" value={form.postTime} disabled={form.useBestTime} onChange={(event) => set('postTime', event.target.value)} className="h-11 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 disabled:opacity-50" />
              </label>
            </fieldset>
          </>
        )}

        {step === 1 && (
          <>
            <h2 className="text-base font-bold">Shop bán gì tuần này?</h2>
            <label className="block text-sm font-bold">Tên sản phẩm *<input value={form.productName} onChange={(event) => set('productName', event.target.value)} className={INPUT_CLASS} placeholder="Ví dụ: Cà phê muối" /></label>
            <label className="block text-sm font-bold">Mô tả *<textarea value={form.productDescription} onChange={(event) => set('productDescription', event.target.value)} rows={3} className={TEXTAREA_CLASS} /></label>
            <div className="grid gap-4 sm:grid-cols-2">
              <label className="block text-sm font-bold">Khách hàng mục tiêu *<input value={form.targetAudience} onChange={(event) => set('targetAudience', event.target.value)} className={INPUT_CLASS} /></label>
              <label className="block text-sm font-bold">Giá (tùy chọn)<input value={form.price} onChange={(event) => set('price', event.target.value)} className={INPUT_CLASS} placeholder="Ví dụ: 35.000đ" /></label>
            </div>
            <label className="block text-sm font-bold">Điểm nổi bật *<input value={form.highlights} onChange={(event) => set('highlights', event.target.value)} className={INPUT_CLASS} /></label>
            <label className="block text-sm font-bold">Mục tiêu của tuần *<input value={form.goal} maxLength={255} onChange={(event) => set('goal', event.target.value)} className={INPUT_CLASS} placeholder="Ví dụ: Tăng đơn cuối tuần, giới thiệu món mới" /></label>
            <label className="block text-sm font-bold">Giọng văn
              <select value={form.tone} onChange={(event) => set('tone', event.target.value)} className={INPUT_CLASS}>
                {TONES.map((tone) => <option key={tone}>{tone}</option>)}
              </select>
            </label>
            <div className="space-y-2 rounded-lg border border-[var(--app-border)] p-3 text-sm">
              <p className="font-bold">Ảnh hoặc video dùng cho các bài {needsMedia ? '*' : '(tùy chọn)'}</p>
              <p className="text-[var(--muted-text)]">{needsMedia ? 'Instagram không đăng được bài chỉ có chữ, và chỉ nhận ảnh JPG.' : 'Gắn vào mọi bài của tuần. Bỏ trống để đăng bài chỉ có chữ.'}</p>
              <label className="inline-flex min-h-11 cursor-pointer items-center rounded-full border border-[var(--app-border)] px-4 font-bold hover:bg-[var(--soft-bg)]">
                {uploading ? 'Đang tải lên...' : 'Chọn tệp'}
                <input type="file" multiple accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm" disabled={uploading} className="sr-only" onChange={(event) => { void upload(event.target.files); event.target.value = '' }} />
              </label>
              {form.mediaIds.length > 0 && (
                <ul className="space-y-1">
                  {form.mediaIds.map((id) => (
                    <li key={id} className="flex items-center justify-between gap-2">
                      <span className="truncate">{form.mediaNames[id] ?? 'Tệp đã chọn'}</span>
                      <button type="button" onClick={() => set('mediaIds', form.mediaIds.filter((value) => value !== id))} aria-label={`Bỏ ${form.mediaNames[id] ?? 'tệp này'}`} className="min-h-11 px-3 font-semibold underline">Bỏ</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </>
        )}

        {step === 2 && (
          <>
            <h2 className="text-base font-bold">Kiểm tra lại</h2>
            <dl className="grid gap-x-4 gap-y-2 text-sm sm:grid-cols-[10rem_1fr]">
              <dt className="font-semibold text-[var(--muted-text)]">Kênh</dt>
              <dd>{channels.find((channel) => channel.id === form.socialAccountId)?.name ?? '—'}</dd>
              <dt className="font-semibold text-[var(--muted-text)]">Tuần</dt>
              <dd>Từ {form.startDate ? formatDay(form.startDate) : '—'}, {form.postsPerWeek} bài</dd>
              <dt className="font-semibold text-[var(--muted-text)]">Ngày</dt>
              <dd>{form.days.length > 0 ? form.days.map((day) => WEEKDAY_NAMES[day]).join(', ') : 'Postiva chọn theo dữ liệu'}</dd>
              <dt className="font-semibold text-[var(--muted-text)]">Giờ</dt>
              <dd>{form.useBestTime ? 'Giờ hiệu quả nhất từ bài cũ' : form.postTime}</dd>
              <dt className="font-semibold text-[var(--muted-text)]">Sản phẩm</dt>
              <dd>{form.productName || '—'}</dd>
              <dt className="font-semibold text-[var(--muted-text)]">Mục tiêu</dt>
              <dd>{form.goal || '—'}</dd>
            </dl>
            <p className="rounded-lg border border-[var(--app-border)] bg-[var(--soft-bg)] px-3 py-2 text-sm">
              Kế hoạch dùng <strong>{needed} lượt AI</strong> (1 lượt lên ý tưởng + {form.postsPerWeek} bản nháp)
              {remaining !== undefined && <>; bạn còn <strong>{remaining}</strong> lượt</>}. Chưa có gì được đăng cho tới khi bạn duyệt.
            </p>
            {remaining !== undefined && remaining < needed && (
              <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">Không đủ lượt AI. Hãy giảm số bài trong tuần.</p>
            )}
          </>
        )}
      </div>

      <div className="flex flex-wrap items-center justify-end gap-3">
        {step === 0
          ? <button type="button" onClick={onCancel} className="mr-auto min-h-11 rounded-lg border border-[var(--app-border)] px-4 text-sm font-semibold hover:bg-[var(--soft-bg)]">Hủy</button>
          : <button type="button" onClick={() => go(step - 1)} className="mr-auto min-h-11 rounded-lg border border-[var(--app-border)] px-4 text-sm font-semibold hover:bg-[var(--soft-bg)]">Quay lại</button>}
        {last
          ? <button type="button" disabled={saving || (remaining !== undefined && remaining < needed)} onClick={() => void submit()} className="min-h-11 rounded-lg bg-blue-600 px-5 text-sm font-bold text-white hover:bg-blue-700 disabled:bg-blue-300">{saving ? 'Đang bắt đầu...' : 'Lên kế hoạch'}</button>
          : (
            <>
              {CAN_SKIP && <button type="button" onClick={() => go(step + 1)} className="min-h-11 px-4 text-sm font-semibold underline underline-offset-4">Bỏ qua bước này</button>}
              <button type="button" onClick={() => go(step + 1)} className="min-h-11 rounded-lg bg-blue-600 px-5 text-sm font-bold text-white hover:bg-blue-700">Tiếp tục</button>
            </>
          )}
      </div>
    </section>
  )
}

function PlanView({ planId, onBack, onCreateNew }: { planId: string; onBack: () => void; onCreateNew: () => void }) {
  const planState = useAppDataResource<WeeklyPlan>(`/weekly-plans/${planId}`)
  const [notice, setNotice] = useState<{ tone: 'ok' | 'error'; text: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const { reload, updateData } = planState
  const status = planState.data?.status

  useEffect(() => {
    if (status !== 'GENERATING') return
    const timer = window.setInterval(reload, POLL_MS)
    return () => window.clearInterval(timer)
  }, [status, reload])

  if (planState.loading && !planState.data) return <PageState title="Đang tải kế hoạch..." />
  if (planState.error || !planState.data) {
    return <PageState title="Không tải được kế hoạch" description={planState.error ?? undefined} onRetry={reload} />
  }
  const plan = planState.data
  const replace = (next: WeeklyPlan) => updateData(() => next)
  const drafted = plan.items.filter((item) => item.post).length
  const toSchedule = plan.items.filter((item) => item.status === 'DRAFT').length

  async function approve() {
    setBusy(true)
    setNotice(null)
    try {
      replace((await api.post<WeeklyPlan>(`/weekly-plans/${planId}/approve`)).data)
      setNotice({ tone: 'ok', text: 'Đã duyệt tuần. Các bài đã được lên lịch và sẽ tự đăng đúng giờ.' })
    } catch (error) {
      setNotice({ tone: 'error', text: getErrorMessage(error, 'Không duyệt được kế hoạch.') })
    } finally {
      setBusy(false)
    }
  }

  async function remove() {
    setBusy(true)
    try {
      await api.delete(`/weekly-plans/${planId}`)
      onBack()
    } catch (error) {
      setNotice({ tone: 'error', text: getErrorMessage(error, 'Không xóa được kế hoạch.') })
      setBusy(false)
      setConfirmDelete(false)
    }
  }

  return (
    <section className="space-y-5 pb-24">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div>
          <button type="button" onClick={onBack} className="mb-2 min-h-11 text-sm font-semibold underline underline-offset-4">← Tất cả kế hoạch</button>
          <PageHeader
            title={`Tuần ${formatDay(plan.startDate)} – ${formatDay(plan.endDate)}`}
            subtitle={`${PLATFORM_NAMES[plan.platform ?? ''] ?? 'Kênh'} · ${plan.accountName ?? 'Kênh đã chọn'} · Mục tiêu: ${plan.goal}`}
          />
        </div>
        <PlanStatusBadge status={plan.status} />
      </div>
      <MockBanner />

      {notice && (
        <p role="status" className={`rounded-lg border px-4 py-3 text-sm font-semibold ${notice.tone === 'ok' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800'}`}>
          {notice.text}
        </p>
      )}
      {plan.error && (
        <p role="alert" className={`rounded-lg border px-4 py-3 text-sm font-semibold ${plan.status === 'FAILED' ? 'border-rose-200 bg-rose-50 text-rose-800' : 'border-amber-200 bg-amber-50 text-amber-900'}`}>
          {plan.error}
        </p>
      )}

      {plan.status === 'GENERATING' && (
        <div role="status" className="rounded-lg border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900">
          {plan.items.length === 0
            ? 'Đang đọc bài cũ và lên ý tưởng cho tuần...'
            : `Đang soạn bài: ${drafted}/${plan.items.length}. Bạn có thể rời trang, Postiva vẫn tiếp tục soạn.`}
        </div>
      )}

      {plan.strategy && (
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 text-sm shadow-sm">
          <p className="flex items-center gap-2 font-bold"><Sparkles size={16} aria-hidden="true" />Chiến lược tuần</p>
          <p className="mt-1">{plan.strategy}</p>
        </div>
      )}
      <InsightsPanel summary={plan.performance} title="Kế hoạch dựa trên bài cũ" />

      <ol className="space-y-3">
        {plan.items.map((item) => (
          <li key={item.id}>
            <PlanItemCard plan={plan} item={item} onChanged={replace} onNotice={setNotice} />
          </li>
        ))}
      </ol>

      {plan.status === 'FAILED' && (
        <button type="button" onClick={onCreateNew} className={PRIMARY_BUTTON}>Lên kế hoạch mới</button>
      )}
      {plan.status === 'APPROVED' && (
        <Link to="/app/schedule" className={`inline-flex items-center ${PRIMARY_BUTTON}`}>Xem ở Lịch đăng</Link>
      )}

      {(plan.status === 'READY' || plan.status === 'FAILED') && (
        <div className="sticky bottom-4 flex flex-wrap items-center justify-end gap-3 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-3 shadow-md">
          {confirmDelete ? (
            <div role="alertdialog" aria-label="Xác nhận xóa kế hoạch" className="mr-auto flex flex-wrap items-center gap-2 text-sm">
              <span>Xóa kế hoạch này? Các bản nháp vẫn được giữ trong bài viết của bạn.</span>
              <button type="button" onClick={() => setConfirmDelete(false)} className={SECONDARY_BUTTON}>Hủy</button>
              <button type="button" disabled={busy} onClick={() => void remove()} className="min-h-11 rounded-full bg-rose-600 px-4 text-sm font-bold text-white hover:bg-rose-700">Xóa</button>
            </div>
          ) : (
            <button type="button" onClick={() => setConfirmDelete(true)} className="mr-auto min-h-11 px-3 text-sm font-semibold underline underline-offset-4">Xóa kế hoạch</button>
          )}
          {plan.status === 'READY' && (
            <button type="button" disabled={busy || toSchedule === 0} onClick={() => void approve()} className={PRIMARY_BUTTON}>
              {busy ? 'Đang lên lịch...' : `Duyệt tuần (${toSchedule} bài)`}
            </button>
          )}
        </div>
      )}
    </section>
  )
}

type NoticeSetter = (notice: { tone: 'ok' | 'error'; text: string } | null) => void

function PlanItemCard({ plan, item, onChanged, onNotice }: { plan: WeeklyPlan; item: WeeklyPlanItem; onChanged: (plan: WeeklyPlan) => void; onNotice: NoticeSetter }) {
  const original = draftText(item.post, plan.platform)
  const [content, setContent] = useState(original)
  const [time, setTime] = useState(toLocalInput(item.scheduledTime))
  const [busy, setBusy] = useState(false)
  const editable = plan.status === 'READY' && (item.status === 'DRAFT' || item.status === 'SKIPPED')

  // Keep the editor in sync while drafts arrive during generation.
  useEffect(() => { setContent(original) }, [original])
  useEffect(() => { setTime(toLocalInput(item.scheduledTime)) }, [item.scheduledTime])

  async function patch(change: { scheduledTime?: string; skipped?: boolean }, success: string) {
    setBusy(true)
    onNotice(null)
    try {
      onChanged((await api.patch<WeeklyPlan>(`/weekly-plans/${plan.id}/items/${item.id}`, change)).data)
      onNotice({ tone: 'ok', text: success })
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không lưu được thay đổi.') })
    } finally {
      setBusy(false)
    }
  }

  async function saveContent() {
    if (!item.post || !plan.platform) return
    setBusy(true)
    onNotice(null)
    try {
      // Text edits go through the existing post endpoint; hashtags and CTA are sent back unchanged.
      await api.put(`/posts/${item.post.postId}`, {
        title: item.post.title ?? undefined,
        platformContents: {
          [plan.platform]: plan.platform === 'INSTAGRAM'
            ? { title: item.post.title ?? '', caption: content.trim() }
            : { title: item.post.title ?? '', content: content.trim() },
        },
        hashtags: item.post.hashtags,
        cta: item.post.cta ?? undefined,
      })
      onChanged({
        ...plan,
        items: plan.items.map((entry) => entry.id !== item.id || !entry.post ? entry : {
          ...entry,
          post: {
            ...entry.post,
            platformContents: {
              ...entry.post.platformContents,
              [plan.platform as string]: { ...entry.post.platformContents[plan.platform as string], [plan.platform === 'INSTAGRAM' ? 'caption' : 'content']: content.trim() },
            },
          },
        }),
      })
      onNotice({ tone: 'ok', text: 'Đã lưu nội dung bài.' })
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không lưu được nội dung.') })
    } finally {
      setBusy(false)
    }
  }

  const skipped = item.status === 'SKIPPED'
  return (
    <article className={`space-y-3 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm ${skipped ? 'opacity-70' : ''}`}>
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-bold">{formatDateTime(item.scheduledTime)} · {item.postType ?? 'Bài đăng'}</h3>
          {item.topic && <p className="text-sm">{item.topic}</p>}
        </div>
        <ItemStatusBadge status={item.status} />
      </div>

      {item.reason && (
        <p className="rounded-md bg-[var(--soft-bg)] px-3 py-2 text-sm"><span className="font-semibold">Vì sao nên đăng: </span>{item.reason}</p>
      )}
      {item.error && <p className="rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900">{item.error}</p>}
      {item.status === 'GENERATING' && <p className="text-sm text-[var(--muted-text)]">Đang soạn bản nháp...</p>}

      {item.post && (
        <label className="block text-sm font-bold">
          Bản nháp{editable ? ' (sửa được)' : ''}
          <textarea value={content} readOnly={!editable} onChange={(event) => setContent(event.target.value)} rows={5} className={`${TEXTAREA_CLASS} read-only:bg-[var(--soft-bg)]`} />
        </label>
      )}
      {item.post?.hashtags && item.post.hashtags.length > 0 && <p className="text-xs text-[var(--muted-text)]">{item.post.hashtags.join(' ')}</p>}
      {item.post?.imageSuggestion && <p className="text-xs text-[var(--muted-text)]">Gợi ý ảnh: {item.post.imageSuggestion}</p>}

      {editable && (
        <div className="flex flex-wrap items-end gap-2">
          {!skipped && content.trim() !== original.trim() && (
            <button type="button" disabled={busy || !content.trim()} onClick={() => void saveContent()} className={PRIMARY_BUTTON}>Lưu nội dung</button>
          )}
          {!skipped && (
            <label className="text-sm font-bold">
              Giờ đăng
              <span className="mt-1 flex gap-2">
                <input type="datetime-local" value={time} onChange={(event) => setTime(event.target.value)} className="h-11 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 font-normal" />
                {time && time !== toLocalInput(item.scheduledTime) && (
                  <button type="button" disabled={busy} onClick={() => void patch({ scheduledTime: new Date(time).toISOString() }, 'Đã đổi giờ đăng.')} className={SECONDARY_BUTTON}>Lưu giờ</button>
                )}
              </span>
            </label>
          )}
          <button type="button" disabled={busy} onClick={() => void patch({ skipped: !skipped }, skipped ? 'Đã đưa bài trở lại tuần.' : 'Đã bỏ qua ngày này.')} className={`ml-auto ${SECONDARY_BUTTON}`}>
            {skipped ? 'Đăng lại ngày này' : 'Bỏ qua ngày này'}
          </button>
        </div>
      )}
    </article>
  )
}
