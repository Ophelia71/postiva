import { ArrowLeft, ArrowRight, CalendarClock, Check, Clock3, Film, Link2, Trash2, Undo2 } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../components/EmptyState'
import { PageState } from '../components/PageUi'
import { ProtectedMediaImage } from '../components/ProtectedMedia'
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
import { isDemoActive } from '../lib/demo'
import { getErrorMessage } from '../lib/errors'
import { isShopProfileComplete, type ShopProfile } from '../lib/shopProfile'

if (DAILY_USE_MOCK) installDailyMock(api)

const DAY_ORDER = [1, 2, 3, 4, 5, 6, 7]
const SUPPORTED_BADGES = ['Facebook', 'Instagram', 'Threads']
const PLATFORM_NAMES: Record<string, string> = { FACEBOOK: 'Facebook', INSTAGRAM: 'Instagram', THREADS: 'Threads' }
const PLATFORM_COLORS: Record<string, string> = { FACEBOOK: '#1877F2', INSTAGRAM: '#E1306C', THREADS: '#111827' }
const TONES = ['Thân thiện, gần gũi', 'Chuyên nghiệp, tin cậy', 'Trẻ trung, hài hước', 'Sang trọng, tinh tế']
const GOALS = ['Giới thiệu sản phẩm mới', 'Chạy khuyến mãi', 'Tăng tương tác', 'Kéo khách cũ quay lại', 'Xây dựng thương hiệu']
const POST_COUNTS = [3, 5, 7]
const MEDIA_LABELS: Record<string, string> = { VIDEO: 'Video', IMAGE: 'Bài có ảnh', CAROUSEL: 'Bài nhiều ảnh', TEXT: 'Bài chỉ có chữ', LINK: 'Bài chia sẻ link' }
const POLL_MS = 2500
const INPUT = 'mt-1 h-11 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm font-normal'
const TEXTAREA = 'mt-1 w-full rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 py-2 text-sm font-normal'
const PRIMARY = 'inline-flex min-h-11 items-center justify-center gap-2 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0] disabled:bg-slate-400'
const SECONDARY = 'inline-flex min-h-11 items-center justify-center gap-2 rounded-full border border-[var(--app-border)] px-4 text-sm font-bold hover:bg-[var(--soft-bg)] disabled:opacity-60'
const CHIP = 'min-h-11 rounded-full border px-4 text-sm font-semibold transition'
const chipClass = (active: boolean) => `${CHIP} ${active ? 'border-[#505db8] bg-[#505db8] text-white' : 'border-[var(--app-border)] bg-[var(--panel-bg)] hover:border-[#8594ea]'}`

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

function parseDay(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** Next Monday and the three after it, the weeks a plan can usually be made for. */
function upcomingWeeks() {
  const today = new Date()
  const offset = ((8 - (today.getDay() || 7)) % 7) || 7
  return [0, 1, 2, 3].map((week) => isoDay(new Date(today.getFullYear(), today.getMonth(), today.getDate() + offset + week * 7)))
}

function weekDates(start: string) {
  const first = parseDay(start)
  return DAY_ORDER.map((_, index) => isoDay(new Date(first.getFullYear(), first.getMonth(), first.getDate() + index)))
}

function shortDate(value: string) {
  const date = parseDay(value)
  return `${String(date.getDate()).padStart(2, '0')}/${String(date.getMonth() + 1).padStart(2, '0')}`
}

function weekday(value: string) {
  return parseDay(value).getDay() || 7
}

function weekRange(plan: Pick<WeeklyPlan, 'startDate' | 'endDate'>) {
  return `${shortDate(plan.startDate)} – ${shortDate(plan.endDate)}`
}

function timeOf(value: string | null) {
  return value ? new Date(value).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' }) : '--:--'
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

function Header({ title, subtitle, action }: { title: string; subtitle: string; action?: React.ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <p className="text-xs font-bold uppercase tracking-wide text-[#6672d8]">Daily</p>
        <h1 className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">{title}</h1>
        <p className="mt-1 max-w-2xl text-sm text-[var(--muted-text)]">{subtitle}</p>
      </div>
      {action}
    </div>
  )
}

function DailyDisabled() {
  return (
    <section>
      <Header title="Kế hoạch tuần" subtitle="Lên kế hoạch và soạn sẵn bài cho cả tuần." />
      <div className="rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
        <EmptyState
          icon={<CalendarClock size={20} />}
          title="Daily chưa được bật"
          description="Tính năng này đang được thử nghiệm và chưa mở cho tài khoản của bạn. Khi được bật, Postiva sẽ phân tích bài cũ, lên ý tưởng và soạn sẵn bài cho cả tuần để bạn duyệt một lần."
        />
      </div>
    </section>
  )
}


type View = { kind: 'home' } | { kind: 'create' } | { kind: 'plan'; planId: string; itemId?: string }

function DailyHome() {
  const channelsState = useAppDataResource<AppChannel[]>('/app-data/channels')
  const plansState = useAppDataResource<WeeklyPlan[]>('/weekly-plans')
  const [view, setView] = useState<View>({ kind: 'home' })
  const [showPast, setShowPast] = useState(false)

  if (channelsState.loading || plansState.loading) return <PageState title="Đang tải kế hoạch tuần..." />
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
    return <PlanCreator channels={channels} onCancel={() => setView({ kind: 'home' })} onCreated={(plan) => { plansState.reload(); setView({ kind: 'plan', planId: plan.id }) }} />
  }
  if (view.kind === 'plan') {
    return <PlanView planId={view.planId} initialItemId={view.itemId} onBack={backHome} onCreateNew={() => setView({ kind: 'create' })} />
  }

  if (channels.length === 0 && plans.length === 0) {
    return (
      <section>
        <Header title="Kế hoạch tuần" subtitle="Postiva phân tích bài cũ, lên ý tưởng và soạn sẵn bài cho cả tuần. Bạn duyệt một lần là xong." />
        <div className="rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
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

  const [current, ...past] = plans
  const createButton = channels.length > 0 && (
    <button type="button" onClick={() => setView({ kind: 'create' })} className={PRIMARY}>
      {current ? 'Lên kế hoạch tuần tới' : 'Lên kế hoạch tuần đầu tiên'}
    </button>
  )

  return (
    <section>
      {/* With no plan yet the empty state carries the button; one call to action is enough. */}
      <Header title="Kế hoạch tuần" subtitle="Postiva phân tích bài cũ, lên ý tưởng và soạn sẵn bài cho cả tuần. Bạn duyệt một lần là xong." action={current ? createButton : undefined} />

      {!current ? (
        <div className="rounded-3xl border border-dashed border-[#b9bfee] bg-[var(--panel-bg)] p-8 text-center sm:p-12">
          <span className="mx-auto flex h-14 w-14 items-center justify-center rounded-2xl bg-[#eef0fd] text-[#505db8]"><CalendarClock size={26} aria-hidden="true" /></span>
          <h2 className="mt-4 text-xl font-bold">Tuần tới đăng gì? Để Postiva lo.</h2>
          <p className="mx-auto mt-2 max-w-lg text-sm text-[var(--muted-text)]">
            Chọn chủ đề cho tuần, Postiva xem bài nào của shop thu hút nhất rồi soạn sẵn 3–7 bài vào đúng ngày, đúng giờ. Bạn chỉ cần xem lại và bấm duyệt.
          </p>
          <div className="mt-6 flex justify-center">{createButton}</div>
        </div>
      ) : (
        <div className="space-y-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h2 className="text-base font-bold">Tuần {weekRange(current)} · {PLATFORM_NAMES[current.platform ?? ''] ?? 'Kênh'} · {current.accountName ?? 'Kênh đã chọn'}</h2>
            <PlanStatusBadge status={current.status} />
          </div>
          <WeekBoard plan={current} onSelect={(itemId) => setView({ kind: 'plan', planId: current.id, itemId })} />
          <button type="button" onClick={() => setView({ kind: 'plan', planId: current.id })} className="min-h-11 text-sm font-semibold text-[#505db8] underline underline-offset-4">
            {current.status === 'READY' ? 'Xem và duyệt tuần này →' : 'Xem chi tiết tuần này →'}
          </button>
        </div>
      )}

      {past.length > 0 && (
        <div className="mt-8">
          <button type="button" aria-expanded={showPast} onClick={() => setShowPast((value) => !value)} className="min-h-11 text-sm font-bold">
            {showPast ? '▾' : '▸'} Các tuần trước ({past.length})
          </button>
          {showPast && (
            <ul className="mt-2 space-y-2">
              {past.map((plan) => (
                <li key={plan.id}>
                  <button type="button" onClick={() => setView({ kind: 'plan', planId: plan.id })} className="flex w-full flex-wrap items-center justify-between gap-3 rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 py-3 text-left hover:bg-[var(--soft-bg)]">
                    <span className="text-sm"><span className="font-bold">Tuần {weekRange(plan)}</span> · {plan.items.length} bài · {plan.goal}</span>
                    <PlanStatusBadge status={plan.status} />
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  )
}

function PlanStatusBadge({ status }: { status: WeeklyPlan['status'] }) {
  const styles: Record<WeeklyPlan['status'], [string, string]> = {
    GENERATING: ['Đang soạn', 'bg-blue-50 text-blue-700'],
    READY: ['Chờ duyệt', 'bg-[#eef0fd] text-[#344260]'],
    APPROVED: ['Đã duyệt', 'bg-emerald-50 text-emerald-700'],
    FAILED: ['Lỗi', 'bg-rose-50 text-rose-700'],
  }
  const [label, className] = styles[status]
  return <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${className}`}>{label}</span>
}

function itemTone(status: PlanItemStatus) {
  if (status === 'PUBLISHED' || status === 'SCHEDULED') return 'bg-emerald-50 text-emerald-700'
  if (status === 'FAILED') return 'bg-rose-50 text-rose-700'
  if (status === 'SKIPPED') return 'bg-slate-100 text-slate-500'
  if (status === 'DRAFT') return 'bg-slate-100 text-slate-700'
  return 'bg-blue-50 text-blue-700'
}

function ItemStatusBadge({ status }: { status: PlanItemStatus }) {
  return <span className={`w-fit rounded-full px-2.5 py-0.5 text-xs font-semibold ${itemTone(status)}`}>{ITEM_STATUS_LABELS[status]}</span>
}

/** Seven day columns; days without a post stay visible so the rhythm of the week is clear. */
function WeekBoard({ plan, selectedId, onSelect }: { plan: WeeklyPlan; selectedId?: string; onSelect: (itemId: string) => void }) {
  const byDay = new Map(plan.items.map((item) => [item.plannedDate, item]))
  const generating = plan.status === 'GENERATING'
  return (
    <ol className="grid grid-cols-1 gap-2 sm:grid-cols-7">
      {weekDates(plan.startDate).map((day) => {
        const item = byDay.get(day)
        const text = item ? draftText(item.post, plan.platform) : ''
        const pending = item && !item.post && (generating || item.status === 'GENERATING' || item.status === 'PLANNED')
        return (
          <li key={day}>
            {item ? (
              <button
                type="button"
                onClick={() => onSelect(item.id)}
                aria-current={selectedId === item.id ? 'true' : undefined}
                className={`flex h-full min-h-[8.5rem] w-full flex-col gap-2 rounded-2xl border p-3 text-left transition sm:min-h-[11rem] ${selectedId === item.id ? 'border-[#505db8] bg-[#eef0fd] shadow-md' : 'border-[var(--app-border)] bg-[var(--panel-bg)] hover:border-[#8594ea] hover:shadow-sm'} ${item.status === 'SKIPPED' ? 'opacity-60' : ''}`}
              >
                <span className="flex items-center justify-between gap-2 text-xs font-bold">
                  <span>{WEEKDAY_LABELS[weekday(day)]} · {shortDate(day)}</span>
                  <span className="flex items-center gap-1 text-[var(--muted-text)]"><Clock3 size={12} aria-hidden="true" />{timeOf(item.scheduledTime)}</span>
                </span>
                <span className="line-clamp-1 text-xs font-semibold text-[#505db8]">{item.postType ?? 'Bài đăng'}</span>
                {pending ? (
                  <span className="flex flex-1 flex-col gap-1.5" aria-label="Đang soạn">
                    <span className="h-2 w-full animate-pulse rounded bg-[#e4e7fb]" />
                    <span className="h-2 w-4/5 animate-pulse rounded bg-[#e4e7fb]" />
                    <span className="h-2 w-3/5 animate-pulse rounded bg-[#e4e7fb]" />
                  </span>
                ) : (
                  <span className="flex-1"><span className="line-clamp-3 text-xs text-[var(--muted-text)]">{text || item.topic}</span></span>
                )}
                <ItemStatusBadge status={item.status} />
              </button>
            ) : (
              <div className="flex h-full min-h-[3rem] flex-col justify-between rounded-2xl border border-dashed border-[var(--app-border)] p-3 text-xs text-[var(--muted-text)] sm:min-h-[11rem]">
                <span className="font-bold">{WEEKDAY_LABELS[weekday(day)]} · {shortDate(day)}</span>
                <span>Nghỉ</span>
              </div>
            )}
          </li>
        )
      })}
    </ol>
  )
}

/** The three findings that matter most for planning, as tiles instead of a paragraph. */
function InsightTiles({ summary, stacked = false }: { summary: Partial<PerformanceSummary> | null | undefined; stacked?: boolean }) {
  const [open, setOpen] = useState(false)
  if (!summary) return null
  const media = Object.entries(summary.engagementByMediaType ?? {}).sort((a, b) => b[1] - a[1])[0]?.[0]
  const tiles = [
    { icon: Film, label: 'Hiệu quả nhất', value: media ? MEDIA_LABELS[media] ?? media : null },
    { icon: CalendarClock, label: 'Ngày tốt nhất', value: summary.bestWeekdays?.length ? summary.bestWeekdays.slice(0, 2).map((day) => WEEKDAY_NAMES[day]).join(', ') : null },
    { icon: Clock3, label: 'Giờ tốt nhất', value: summary.bestHour != null ? `${summary.bestHour}h – ${summary.bestHour + 1}h` : null },
  ]
  const hasData = tiles.some((tile) => tile.value)
  return (
    <div className="rounded-2xl border border-[var(--app-border)] bg-[var(--soft-bg)] p-4">
      <p className="flex items-center gap-2 text-sm font-bold">Postiva thấy gì từ bài cũ</p>
      {summary.warning && <p className="mt-2 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 py-2 text-xs text-[var(--muted-text)]">{summary.warning}</p>}
      {hasData ? (
        <div className={`mt-3 grid grid-cols-1 gap-2 ${stacked ? '' : 'sm:grid-cols-3'}`}>
          {tiles.map((tile) => (
            <div key={tile.label} className="flex items-center gap-3 rounded-xl bg-[var(--panel-bg)] px-3 py-2">
              <tile.icon size={18} aria-hidden="true" className="shrink-0 text-[#505db8]" />
              <span className="min-w-0">
                <span className="block text-xs text-[var(--muted-text)]">{tile.label}</span>
                <span className="block truncate text-sm font-bold">{tile.value ?? 'Chưa đủ dữ liệu'}</span>
              </span>
            </div>
          ))}
        </div>
      ) : (
        <p className="mt-2 text-sm text-[var(--muted-text)]">{summary.insights?.[0] ?? 'Chưa có đủ bài cũ để phân tích; kế hoạch dựa trên hồ sơ shop.'}</p>
      )}
      {(summary.insights?.length ?? 0) > 0 && hasData && (
        <>
          <button type="button" aria-expanded={open} onClick={() => setOpen((value) => !value)} className="mt-2 min-h-11 text-xs font-semibold underline underline-offset-4">
            {open ? 'Ẩn chi tiết' : 'Xem chi tiết'}
          </button>
          {open && <ul className="list-disc space-y-1 pl-5 text-xs text-[var(--muted-text)]">{summary.insights?.map((line) => <li key={line}>{line}</li>)}</ul>}
        </>
      )}
    </div>
  )
}

type Brief = {
  productName: string
  productDescription: string
  price: string
  targetAudience: string
  highlights: string
  tone: string
  mediaIds: string[]
  mediaNames: Record<string, string>
}

function briefFromProfile(profile: ShopProfile | null | undefined): Brief {
  const products = profile?.mainProducts ?? ''
  return {
    productName: products.slice(0, 200),
    productDescription: profile?.description || products,
    price: profile?.priceRange ?? '',
    targetAudience: profile?.targetCustomer || (products ? 'Khách hàng của shop' : ''),
    highlights: profile?.highlights || products,
    tone: profile?.defaultTone && TONES.includes(profile.defaultTone) ? profile.defaultTone : TONES[0],
    mediaIds: [],
    mediaNames: {},
  }
}

/** One screen: what the week is about, where and how often. The shop profile supplies the product details. */
function PlanCreator({ channels, onCancel, onCreated }: { channels: AppChannel[]; onCancel: () => void; onCreated: (plan: WeeklyPlan) => void }) {
  const usageState = useAppDataResource<{ remaining: number }>('/usage')
  const profileState = useAppDataResource<ShopProfile | null>('/shop-profile')
  const weeks = useMemo(upcomingWeeks, [])
  const [channelId, setChannelId] = useState(channels[0]?.id ?? '')
  const [goal, setGoal] = useState(GOALS[0])
  const [customGoal, setCustomGoal] = useState('')
  const [startDate, setStartDate] = useState(weeks[0])
  const [posts, setPosts] = useState(5)
  const [days, setDays] = useState<number[]>([])
  const [fixedTime, setFixedTime] = useState('')
  const [brief, setBrief] = useState<Brief | null>(null)
  const [showMore, setShowMore] = useState(false)
  const [insights, setInsights] = useState<{ accountId: string; summary: PerformanceSummary | null; error: string } | null>(null)
  const [saving, setSaving] = useState(false)
  const [uploading, setUploading] = useState(false)
  const [error, setError] = useState('')

  const profileComplete = isShopProfileComplete(profileState.data)
  const currentBrief = brief ?? briefFromProfile(profileState.data)
  const setBriefField = <K extends keyof Brief>(key: K, value: Brief[K]) => setBrief({ ...currentBrief, [key]: value })
  const needsMedia = channels.find((channel) => channel.id === channelId)?.badge === 'Instagram'
  const needed = aiUnitsFor(posts)
  const remaining = usageState.data?.remaining
  const weekGoal = goal === 'other' ? customGoal.trim() : goal
  // Without a profile the product fields are the only source, so they are shown and required.
  const productOpen = showMore || !profileComplete

  useEffect(() => {
    if (!channelId) return
    let ignore = false
    api.get<PerformanceSummary>('/weekly-plans/insights', { params: { socialAccountId: channelId, timezone: browserZone() } })
      .then((response) => { if (!ignore) setInsights({ accountId: channelId, summary: response.data, error: '' }) })
      .catch((loadError) => {
        if (!ignore) setInsights({ accountId: channelId, summary: null, error: getErrorMessage(loadError, 'Không đọc được bài cũ của kênh này.') })
      })
    return () => { ignore = true }
  }, [channelId])

  async function upload(files: FileList | null) {
    if (!files || files.length === 0) return
    setUploading(true)
    setError('')
    try {
      const added = await Promise.all(Array.from(files).map(async (file) => {
        const data = new FormData()
        data.append('file', file)
        return (await api.post<{ id: string; fileName: string }>('/files/upload', data, { headers: { 'Content-Type': 'multipart/form-data' } })).data
      }))
      setBrief({
        ...currentBrief,
        mediaIds: [...currentBrief.mediaIds, ...added.map((item) => item.id)].slice(0, 10),
        mediaNames: { ...currentBrief.mediaNames, ...Object.fromEntries(added.map((item) => [item.id, item.fileName])) },
      })
    } catch (uploadError) {
      setError(getErrorMessage(uploadError, 'Không tải được ảnh hoặc video.'))
    } finally {
      setUploading(false)
    }
  }

  function problem() {
    if (!channelId) return 'Chọn kênh để đăng.'
    if (!weekGoal) return 'Chọn hoặc nhập chủ đề cho tuần.'
    if (!currentBrief.productName.trim() || !currentBrief.productDescription.trim() || !currentBrief.targetAudience.trim() || !currentBrief.highlights.trim()) {
      return profileComplete ? 'Điền đủ thông tin sản phẩm trong phần Tuỳ chỉnh thêm.' : 'Điền thông tin sản phẩm, hoặc hoàn thiện Hồ sơ shop để Postiva tự điền.'
    }
    if (needsMedia && currentBrief.mediaIds.length === 0) return 'Instagram cần ít nhất một ảnh hoặc video.'
    return null
  }

  async function submit() {
    const blocked = problem()
    if (blocked) {
      setError(blocked)
      if (blocked.includes('sản phẩm') || blocked.includes('Instagram')) setShowMore(true)
      return
    }
    setSaving(true)
    setError('')
    const request: CreateWeeklyPlanRequest = {
      socialAccountId: channelId,
      startDate,
      postsPerWeek: posts,
      daysOfWeek: days.length > 0 ? days : undefined,
      postTime: fixedTime || undefined,
      timezone: browserZone(),
      productName: currentBrief.productName.trim(),
      productDescription: currentBrief.productDescription.trim(),
      price: currentBrief.price.trim() || undefined,
      targetAudience: currentBrief.targetAudience.trim(),
      highlights: currentBrief.highlights.trim(),
      goal: weekGoal.slice(0, 255),
      tone: currentBrief.tone,
      imageIds: currentBrief.mediaIds,
    }
    try {
      onCreated((await api.post<WeeklyPlan>('/weekly-plans', request)).data)
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Không bắt đầu được kế hoạch. Vui lòng thử lại.'))
    } finally {
      setSaving(false)
    }
  }

  const currentInsights = insights?.accountId === channelId ? insights : null

  return (
    <section className="mx-auto max-w-3xl">
      <button type="button" onClick={onCancel} className="mb-3 inline-flex min-h-11 items-center gap-2 text-sm font-semibold"><ArrowLeft size={16} aria-hidden="true" />Kế hoạch tuần</button>
      <Header title="Tuần tới bạn muốn tập trung vào gì?" subtitle="Một màn hình là xong. Postiva lấy sản phẩm và giọng văn từ hồ sơ shop của bạn." />

      <div className="space-y-6 rounded-3xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm sm:p-8">
        <fieldset>
          <legend className="text-sm font-bold">Chủ đề của tuần</legend>
          <div className="mt-2 flex flex-wrap gap-2">
            {GOALS.map((item) => (
              <button key={item} type="button" aria-pressed={goal === item} onClick={() => setGoal(item)} className={chipClass(goal === item)}>{item}</button>
            ))}
            <button type="button" aria-pressed={goal === 'other'} onClick={() => setGoal('other')} className={chipClass(goal === 'other')}>Khác…</button>
          </div>
          {goal === 'other' && (
            <input aria-label="Chủ đề khác" value={customGoal} maxLength={255} onChange={(event) => setCustomGoal(event.target.value)} className={INPUT} placeholder="Ví dụ: Giới thiệu menu mùa đông" autoFocus />
          )}
        </fieldset>

        <div className="grid gap-4 sm:grid-cols-3">
          <label className="block text-sm font-bold">Kênh
            <select value={channelId} onChange={(event) => setChannelId(event.target.value)} className={INPUT}>
              {channels.map((channel) => <option key={channel.id} value={channel.id}>{channel.name} ({channel.badge})</option>)}
            </select>
          </label>
          <label className="block text-sm font-bold">Tuần
            <select value={startDate} onChange={(event) => setStartDate(event.target.value)} className={INPUT}>
              {weeks.map((week, index) => (
                <option key={week} value={week}>{index === 0 ? 'Tuần tới' : `Sau ${index + 1} tuần`} · {shortDate(week)} – {shortDate(weekDates(week)[6])}</option>
              ))}
            </select>
          </label>
          <div className="text-sm font-bold">
            <span id="post-count-label">Số bài</span>
            <div role="group" aria-labelledby="post-count-label" className="mt-1 flex gap-2">
              {POST_COUNTS.map((count) => (
                <button key={count} type="button" aria-pressed={posts === count} onClick={() => setPosts(count)} className={`${chipClass(posts === count)} flex-1 px-0`}>{count}</button>
              ))}
            </div>
          </div>
        </div>

        {!currentInsights && channelId && <p className="text-sm text-[var(--muted-text)]">Đang đọc bài cũ của kênh...</p>}
        {currentInsights?.error && <p className="rounded-xl border border-[var(--app-border)] bg-[var(--soft-bg)] px-3 py-2 text-sm text-[var(--app-text)]">{currentInsights.error} Kế hoạch vẫn tạo được, dựa trên hồ sơ shop.</p>}
        {currentInsights?.summary && <InsightTiles summary={currentInsights.summary} />}

        {!profileComplete && (
          <p className="rounded-xl border border-[var(--app-border)] bg-[var(--soft-bg)] px-3 py-2 text-sm text-[var(--app-text)]">
            Chưa có hồ sơ shop nên cần nhập sản phẩm bên dưới. <Link to="/app/shop-profile" className="font-bold underline">Hoàn thiện hồ sơ shop</Link> để lần sau Postiva tự điền.
          </p>
        )}

        {profileComplete && (
          <button type="button" aria-expanded={showMore} onClick={() => setShowMore((value) => !value)} className="min-h-11 text-sm font-semibold underline underline-offset-4">
            {showMore ? 'Ẩn tuỳ chỉnh' : 'Tuỳ chỉnh thêm (ngày, giờ, sản phẩm riêng cho tuần này, ảnh)'}
          </button>
        )}

        {(productOpen || needsMedia) && (
          <div className="space-y-5 border-t border-[var(--app-border)] pt-5">
            {productOpen && (
              <>
                <div role="group" aria-label="Ngày muốn đăng" className="text-sm font-bold">
                  Ngày muốn đăng <span className="font-normal text-[var(--muted-text)]">(để trống: Postiva chọn theo bài cũ)</span>
                  <div className="mt-2 flex flex-wrap gap-2">
                    {DAY_ORDER.map((day) => {
                      const on = days.includes(day)
                      return <button key={day} type="button" aria-pressed={on} aria-label={WEEKDAY_NAMES[day]} onClick={() => setDays(on ? days.filter((value) => value !== day) : [...days, day].sort())} className={`${chipClass(on)} min-w-11 px-3`}>{WEEKDAY_LABELS[day]}</button>
                    })}
                  </div>
                </div>
                <label className="block max-w-xs text-sm font-bold">Giờ đăng cố định <span className="font-normal text-[var(--muted-text)]">(để trống: giờ tốt nhất)</span>
                  <input type="time" value={fixedTime} onChange={(event) => setFixedTime(event.target.value)} className={INPUT} />
                </label>
                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="block text-sm font-bold">Sản phẩm của tuần *<input value={currentBrief.productName} maxLength={200} onChange={(event) => setBriefField('productName', event.target.value)} className={INPUT} /></label>
                  <label className="block text-sm font-bold">Khách hàng mục tiêu *<input value={currentBrief.targetAudience} maxLength={1000} onChange={(event) => setBriefField('targetAudience', event.target.value)} className={INPUT} /></label>
                </div>
                <label className="block text-sm font-bold">Mô tả *<textarea value={currentBrief.productDescription} maxLength={4000} rows={2} onChange={(event) => setBriefField('productDescription', event.target.value)} className={TEXTAREA} /></label>
                <div className="grid gap-4 sm:grid-cols-3">
                  <label className="block text-sm font-bold sm:col-span-2">Điểm nổi bật *<input value={currentBrief.highlights} maxLength={2000} onChange={(event) => setBriefField('highlights', event.target.value)} className={INPUT} /></label>
                  <label className="block text-sm font-bold">Giá<input value={currentBrief.price} maxLength={100} onChange={(event) => setBriefField('price', event.target.value)} className={INPUT} /></label>
                </div>
                <label className="block max-w-xs text-sm font-bold">Giọng văn
                  <select value={currentBrief.tone} onChange={(event) => setBriefField('tone', event.target.value)} className={INPUT}>
                    {TONES.map((tone) => <option key={tone}>{tone}</option>)}
                  </select>
                </label>
              </>
            )}
            <div className="space-y-2 rounded-xl border border-[var(--app-border)] p-3 text-sm">
              <p className="font-bold">Ảnh hoặc video cho các bài {needsMedia ? '*' : '(tùy chọn)'}</p>
              <p className="text-[var(--muted-text)]">{needsMedia ? 'Instagram không đăng được bài chỉ có chữ, và chỉ nhận ảnh JPG.' : 'Gắn vào mọi bài của tuần. Bỏ trống để đăng bài chỉ có chữ.'}</p>
              <label className="inline-flex min-h-11 cursor-pointer items-center rounded-full border border-[var(--app-border)] px-4 font-bold hover:bg-[var(--soft-bg)]">
                {uploading ? 'Đang tải lên...' : 'Chọn tệp'}
                <input type="file" multiple accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm" disabled={uploading} className="sr-only" onChange={(event) => { void upload(event.target.files); event.target.value = '' }} />
              </label>
              {currentBrief.mediaIds.length > 0 && (
                <ul className="space-y-1">
                  {currentBrief.mediaIds.map((id) => (
                    <li key={id} className="flex items-center justify-between gap-2">
                      <span className="truncate">{currentBrief.mediaNames[id] ?? 'Tệp đã chọn'}</span>
                      <button type="button" onClick={() => setBriefField('mediaIds', currentBrief.mediaIds.filter((value) => value !== id))} aria-label={`Bỏ ${currentBrief.mediaNames[id] ?? 'tệp này'}`} className="min-h-11 px-3 font-semibold underline">Bỏ</button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          </div>
        )}

        {error && <p role="alert" className="rounded-xl border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">{error}</p>}

        <div className="flex flex-col-reverse items-stretch gap-3 border-t border-[var(--app-border)] pt-5 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-sm text-[var(--muted-text)]">
            Dùng <strong className="text-[var(--app-text)]">{needed} lượt AI</strong>{remaining !== undefined && <> · còn {remaining}</>}. Chưa đăng gì cho tới khi bạn duyệt.
          </p>
          <button type="button" disabled={saving || (remaining !== undefined && remaining < needed)} onClick={() => void submit()} className={PRIMARY}>
            {saving ? 'Đang bắt đầu...' : 'Lên kế hoạch'}
          </button>
        </div>
        {remaining !== undefined && remaining < needed && (
          <p role="alert" className="text-sm font-semibold text-rose-700">Không đủ lượt AI cho {posts} bài. Hãy chọn ít bài hơn.</p>
        )}
      </div>
    </section>
  )
}

type Notice = { tone: 'ok' | 'error'; text: string } | null

function PlanView({ planId, initialItemId, onBack, onCreateNew }: { planId: string; initialItemId?: string; onBack: () => void; onCreateNew: () => void }) {
  const planState = useAppDataResource<WeeklyPlan>(`/weekly-plans/${planId}`)
  const [selectedId, setSelectedId] = useState<string | undefined>(initialItemId)
  const [notice, setNotice] = useState<Notice>(null)
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
  if (planState.error || !planState.data) return <PageState title="Không tải được kế hoạch" description={planState.error ?? undefined} onRetry={reload} />

  const plan = planState.data
  const replace = (next: WeeklyPlan) => updateData(() => next)
  const items = plan.items
  const selected = items.find((item) => item.id === selectedId) ?? items.find((item) => item.status === 'DRAFT') ?? items[0]
  const selectedIndex = selected ? items.indexOf(selected) : -1
  const drafted = items.filter((item) => item.post).length
  const toSchedule = items.filter((item) => item.status === 'DRAFT').length
  const firstTime = items.filter((item) => item.status === 'DRAFT' && item.scheduledTime).map((item) => item.scheduledTime as string).sort()[0]

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
    // Room for the fixed approval bar, which sits above the phone's bottom navigation.
    <section className="pb-52 lg:pb-28">
      <button type="button" onClick={onBack} className="mb-3 inline-flex min-h-11 items-center gap-2 text-sm font-semibold"><ArrowLeft size={16} aria-hidden="true" />Kế hoạch tuần</button>
      <Header
        title={`Tuần ${weekRange(plan)}`}
        subtitle={`${PLATFORM_NAMES[plan.platform ?? ''] ?? 'Kênh'} · ${plan.accountName ?? 'Kênh đã chọn'} · Chủ đề: ${plan.goal}`}
        action={<PlanStatusBadge status={plan.status} />}
      />

      {notice && (
        <p role="status" className={`mb-4 rounded-xl border px-4 py-3 text-sm font-semibold ${notice.tone === 'ok' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800'}`}>{notice.text}</p>
      )}
      {plan.error && (
        <p role="alert" className={`mb-4 rounded-xl border px-4 py-3 text-sm font-semibold ${plan.status === 'FAILED' ? 'border-rose-200 bg-rose-50 text-rose-800' : 'border-[var(--app-border)] bg-[var(--soft-bg)] text-[var(--app-text)]'}`}>{plan.error}</p>
      )}

      {plan.status === 'GENERATING' && (
        <div role="status" className="mb-4 flex items-center gap-3 rounded-xl border border-blue-200 bg-blue-50 px-4 py-3 text-sm text-blue-900">
          <span className="h-2.5 w-2.5 animate-ping rounded-full bg-blue-500" aria-hidden="true" />
          {items.length === 0 ? 'Đang đọc bài cũ và lên ý tưởng cho tuần...' : `Đang soạn bài ${Math.min(drafted + 1, items.length)}/${items.length}. Bạn có thể rời trang, Postiva vẫn tiếp tục.`}
        </div>
      )}

      {plan.strategy && (
        <div className="mb-4 flex items-start gap-3 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 text-sm">
          <p><span className="font-bold">Chiến lược tuần: </span>{plan.strategy}</p>
        </div>
      )}

      {plan.status === 'GENERATING' || items.length === 0 ? (
        <>
          {items.length === 0 ? <SkeletonWeek start={plan.startDate} /> : <WeekBoard plan={plan} onSelect={setSelectedId} />}
          <div className="mt-4"><InsightTiles summary={plan.performance} /></div>
        </>
      ) : (
        <div className="grid gap-4 lg:grid-cols-[minmax(0,20rem)_minmax(0,1fr)]">
          {/* min-w-0 keeps the horizontally scrolling day list on phones from widening the whole page. */}
          <aside className="min-w-0 space-y-3">
            <ol aria-label="Các ngày trong tuần" className="flex gap-2 overflow-x-auto pb-1 lg:flex-col lg:overflow-visible">
              {items.map((item) => (
                <li key={item.id} className="shrink-0 lg:shrink">
                  <button
                    type="button"
                    onClick={() => setSelectedId(item.id)}
                    aria-current={selected?.id === item.id ? 'true' : undefined}
                    className={`flex w-48 items-center gap-3 rounded-xl border px-3 py-2 text-left lg:w-full ${selected?.id === item.id ? 'border-[#505db8] bg-[#eef0fd]' : 'border-[var(--app-border)] bg-[var(--panel-bg)] hover:bg-[var(--soft-bg)]'} ${item.status === 'SKIPPED' ? 'opacity-60' : ''}`}
                  >
                    <span className="flex w-10 shrink-0 flex-col items-center rounded-lg bg-[var(--soft-bg)] py-1 text-xs font-bold">
                      {WEEKDAY_LABELS[weekday(item.plannedDate)]}
                      <span className="text-[10px] font-semibold text-[var(--muted-text)]">{shortDate(item.plannedDate)}</span>
                    </span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-semibold">{item.postType ?? 'Bài đăng'}</span>
                      <span className="block text-xs text-[var(--muted-text)]">{timeOf(item.scheduledTime)}</span>
                    </span>
                    {item.status === 'SKIPPED' ? <span className="text-xs text-[var(--muted-text)]">Bỏ</span> : item.status !== 'DRAFT' && <ItemStatusBadge status={item.status} />}
                  </button>
                </li>
              ))}
            </ol>
            <div className="hidden lg:block"><InsightTiles summary={plan.performance} stacked /></div>
          </aside>

          {selected && (
            <DayDetail
              key={selected.id}
              plan={plan}
              item={selected}
              onChanged={replace}
              onNotice={setNotice}
              onPrev={selectedIndex > 0 ? () => setSelectedId(items[selectedIndex - 1].id) : undefined}
              onNext={selectedIndex < items.length - 1 ? () => setSelectedId(items[selectedIndex + 1].id) : undefined}
            />
          )}
        </div>
      )}

      {plan.status === 'FAILED' && <button type="button" onClick={onCreateNew} className={`${PRIMARY} mt-6`}>Lên kế hoạch mới</button>}
      {plan.status === 'APPROVED' && (
        <div className="mt-6 flex flex-wrap items-center gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <Check size={18} aria-hidden="true" />
          <span className="flex-1">Tuần này đã được duyệt. Postiva sẽ tự đăng đúng giờ.</span>
          <Link to="/app/schedule" className={PRIMARY}>Xem ở Lịch đăng</Link>
        </div>
      )}

      {(plan.status === 'READY' || plan.status === 'FAILED') && (
        <div className="fixed inset-x-0 bottom-[72px] z-10 border-t border-[var(--app-border)] bg-[var(--panel-bg)]/95 px-4 py-3 shadow-[0_-8px_24px_rgba(15,23,42,0.06)] backdrop-blur lg:bottom-0 lg:left-[272px]">
          <div className="mx-auto flex max-w-[1240px] flex-wrap items-center justify-end gap-3">
            {confirmDelete ? (
              <div role="alertdialog" aria-label="Xác nhận xóa kế hoạch" className="mr-auto flex flex-wrap items-center gap-2 text-sm">
                <span>Xóa kế hoạch này? Bản nháp vẫn được giữ trong bài viết của bạn.</span>
                <button type="button" onClick={() => setConfirmDelete(false)} className={SECONDARY}>Hủy</button>
                <button type="button" disabled={busy} onClick={() => void remove()} className="min-h-11 rounded-full bg-rose-600 px-4 text-sm font-bold text-white hover:bg-rose-700">Xóa</button>
              </div>
            ) : (
              <button type="button" onClick={() => setConfirmDelete(true)} className="mr-auto inline-flex min-h-11 items-center gap-2 px-2 text-sm font-semibold text-[var(--muted-text)] hover:text-rose-700"><Trash2 size={15} aria-hidden="true" />Xóa kế hoạch</button>
            )}
            {plan.status === 'READY' && (
              <button type="button" disabled={busy || toSchedule === 0} onClick={() => void approve()} className={PRIMARY}>
                <Check size={16} aria-hidden="true" />
                {busy ? 'Đang lên lịch...' : `Duyệt ${toSchedule} bài${firstTime ? ` · tự đăng từ ${WEEKDAY_LABELS[weekday(isoDay(new Date(firstTime)))]} ${timeOf(firstTime)}` : ''}`}
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  )
}

/** Grey week shown while the AI is still choosing the days. */
function SkeletonWeek({ start }: { start: string }) {
  return (
    <ol className="grid grid-cols-1 gap-2 sm:grid-cols-7" aria-label="Đang lên ý tưởng">
      {weekDates(start).map((day) => (
        <li key={day} className="flex min-h-[3rem] flex-col gap-2 rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-3 sm:min-h-[11rem]">
          <span className="text-xs font-bold text-[var(--muted-text)]">{WEEKDAY_LABELS[weekday(day)]} · {shortDate(day)}</span>
          <span className="h-2 w-3/4 animate-pulse rounded bg-[#e4e7fb]" />
          <span className="h-2 w-1/2 animate-pulse rounded bg-[#e4e7fb]" />
        </li>
      ))}
    </ol>
  )
}

/** One day, previewed the way it will look on the platform, with the edits that make sense before approval. */
function DayDetail({ plan, item, onChanged, onNotice, onPrev, onNext }: {
  plan: WeeklyPlan
  item: WeeklyPlanItem
  onChanged: (plan: WeeklyPlan) => void
  onNotice: (notice: Notice) => void
  onPrev?: () => void
  onNext?: () => void
}) {
  const original = draftText(item.post, plan.platform)
  const [content, setContent] = useState(original)
  const [time, setTime] = useState(toLocalInput(item.scheduledTime))
  const [editingTime, setEditingTime] = useState(false)
  const [busy, setBusy] = useState(false)
  const editable = plan.status === 'READY' && (item.status === 'DRAFT' || item.status === 'SKIPPED')
  const skipped = item.status === 'SKIPPED'
  const platform = plan.platform ?? 'FACEBOOK'
  const accountName = plan.accountName ?? PLATFORM_NAMES[platform] ?? 'Shop'

  useEffect(() => { setContent(original) }, [original])

  async function patch(change: { scheduledTime?: string; skipped?: boolean }, success: string) {
    setBusy(true)
    onNotice(null)
    try {
      onChanged((await api.patch<WeeklyPlan>(`/weekly-plans/${plan.id}/items/${item.id}`, change)).data)
      onNotice({ tone: 'ok', text: success })
      setEditingTime(false)
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không lưu được thay đổi.') })
    } finally {
      setBusy(false)
    }
  }

  async function saveContent() {
    if (!item.post) return
    setBusy(true)
    onNotice(null)
    try {
      // Text edits go through the existing post endpoint; hashtags and CTA are sent back unchanged.
      const field = platform === 'INSTAGRAM' ? 'caption' : 'content'
      await api.put(`/posts/${item.post.postId}`, {
        title: item.post.title ?? undefined,
        platformContents: { [platform]: { title: item.post.title ?? '', [field]: content.trim() } },
        hashtags: item.post.hashtags,
        cta: item.post.cta ?? undefined,
      })
      onChanged({
        ...plan,
        items: plan.items.map((entry) => entry.id !== item.id || !entry.post ? entry : {
          ...entry,
          post: { ...entry.post, platformContents: { ...entry.post.platformContents, [platform]: { ...entry.post.platformContents[platform], [field]: content.trim() } } },
        }),
      })
      onNotice({ tone: 'ok', text: 'Đã lưu nội dung bài.' })
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không lưu được nội dung.') })
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="min-w-0 space-y-4 rounded-3xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm sm:p-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div>
          <p className="text-xs font-bold uppercase tracking-wide text-[#6672d8]">{WEEKDAY_NAMES[weekday(item.plannedDate)]}, {shortDate(item.plannedDate)} · {timeOf(item.scheduledTime)}</p>
          <h2 className="mt-1 text-lg font-bold">{item.topic ?? item.postType ?? 'Bài đăng'}</h2>
        </div>
        <div className="flex items-center gap-1">
          <button type="button" disabled={!onPrev} onClick={onPrev} aria-label="Ngày trước" className="flex h-11 w-11 items-center justify-center rounded-full border border-[var(--app-border)] disabled:opacity-40"><ArrowLeft size={16} aria-hidden="true" /></button>
          <button type="button" disabled={!onNext} onClick={onNext} aria-label="Ngày sau" className="flex h-11 w-11 items-center justify-center rounded-full border border-[var(--app-border)] disabled:opacity-40"><ArrowRight size={16} aria-hidden="true" /></button>
        </div>
      </div>

      {item.reason && (
        <p className="text-sm text-[var(--muted-text)]">
          <span><span className="font-semibold text-[var(--app-text)]">Lý do: </span>{item.reason}</span>
        </p>
      )}
      {item.error && <p className="rounded-xl border border-[var(--app-border)] bg-[var(--soft-bg)] px-3 py-2 text-sm text-[var(--app-text)]">{item.error}</p>}

      {item.post ? (
        <div className={`overflow-hidden rounded-2xl border border-[var(--app-border)] bg-white text-slate-900 ${skipped ? 'opacity-60' : ''}`} style={{ borderTop: `4px solid ${PLATFORM_COLORS[platform] ?? '#505db8'}` }}>
          <div className="flex items-center gap-3 px-4 pt-4">
            <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#eef0fd] text-sm font-bold text-[#505db8]">{accountName.charAt(0).toUpperCase()}</span>
            <span>
              <span className="block text-sm font-bold">{accountName}</span>
              <span className="block text-xs text-slate-500">{PLATFORM_NAMES[platform]} · {timeOf(item.scheduledTime)}</span>
            </span>
          </div>
          {editable && !skipped ? (
            <textarea
              aria-label="Nội dung bài (sửa trực tiếp)"
              value={content}
              onChange={(event) => setContent(event.target.value)}
              rows={Math.min(14, Math.max(5, content.split('\n').length + 2))}
              className="mt-3 block w-full resize-y border-0 bg-transparent px-4 text-sm leading-relaxed text-slate-900 outline-none focus:bg-[#f8f9ff]"
            />
          ) : (
            <p className="mt-3 whitespace-pre-wrap px-4 text-sm leading-relaxed">{original}</p>
          )}
          {item.post.hashtags.length > 0 && <p className="px-4 pt-2 text-sm text-[#1877F2]">{item.post.hashtags.join(' ')}</p>}
          {item.post.imageUrl ? (
            <ProtectedMediaImage src={item.post.imageUrl} alt="" className="mt-3 max-h-80 w-full object-cover" />
          ) : (
            item.post.imageSuggestion && <p className="mx-4 mt-3 rounded-lg bg-slate-50 px-3 py-2 text-xs text-slate-500">Gợi ý ảnh: {item.post.imageSuggestion}</p>
          )}
        </div>
      ) : (
        <div className="space-y-2 rounded-2xl border border-[var(--app-border)] p-4" aria-label="Đang soạn">
          <span className="block h-3 w-4/5 animate-pulse rounded bg-[#e4e7fb]" />
          <span className="block h-3 w-3/5 animate-pulse rounded bg-[#e4e7fb]" />
          <span className="block h-3 w-2/3 animate-pulse rounded bg-[#e4e7fb]" />
        </div>
      )}

      {editable && (
        <div className="flex flex-wrap items-center gap-2">
          {!skipped && content.trim() !== original.trim() && (
            <button type="button" disabled={busy || !content.trim()} onClick={() => void saveContent()} className={PRIMARY}>Lưu nội dung</button>
          )}
          {!skipped && (editingTime ? (
            <span className="flex flex-wrap items-center gap-2">
              <input type="datetime-local" aria-label="Giờ đăng" value={time} onChange={(event) => setTime(event.target.value)} className="h-11 rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm" />
              <button type="button" disabled={busy || !time} onClick={() => void patch({ scheduledTime: new Date(time).toISOString() }, 'Đã đổi giờ đăng.')} className={SECONDARY}>Lưu giờ</button>
              <button type="button" onClick={() => { setEditingTime(false); setTime(toLocalInput(item.scheduledTime)) }} className="min-h-11 px-2 text-sm font-semibold underline">Hủy</button>
            </span>
          ) : (
            <button type="button" onClick={() => setEditingTime(true)} className={SECONDARY}><Clock3 size={15} aria-hidden="true" />Đổi giờ</button>
          ))}
          <button type="button" disabled={busy} onClick={() => void patch({ skipped: !skipped }, skipped ? 'Đã đưa bài trở lại tuần.' : 'Đã bỏ ngày này khỏi tuần.')} className={`${SECONDARY} ml-auto`}>
            {skipped ? <><Undo2 size={15} aria-hidden="true" />Đăng lại ngày này</> : <><Trash2 size={15} aria-hidden="true" />Bỏ ngày này</>}
          </button>
        </div>
      )}
      {!editable && item.status !== 'DRAFT' && <ItemStatusBadge status={item.status} />}
    </article>
  )
}
