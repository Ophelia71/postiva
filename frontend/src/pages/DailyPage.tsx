import { CalendarClock, Link2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../components/EmptyState'
import { HelpTip } from '../components/HelpTip'
import { PageHeader, PageState } from '../components/PageUi'
import { Stepper } from '../components/Stepper'
import { api } from '../lib/api'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'
import {
  type DailyPreviewItem,
  type DailyRule,
  type DailyRuleRequest,
  type DailyRun,
  type DailyStatus,
  PAUSE_REASON_LABELS,
  RUN_ERROR_LABELS,
  WEEKDAY_LABELS,
} from '../lib/dailyApi'
import { getErrorMessage } from '../lib/errors'

const BLOCKED = 'Bước này không thể bỏ qua, hãy setup lại.'
// In the Vite dev server every step can be skipped; production builds enforce all four.
const CAN_SKIP = import.meta.env.DEV
const STEPS = [
  { label: 'Kênh' },
  { label: 'Thời gian' },
  { label: 'Chủ đề' },
  { label: 'Duyệt' },
] as const
const DAY_ORDER = [1, 2, 3, 4, 5, 6, 7]
const TIMEZONES = ['Asia/Ho_Chi_Minh', 'Asia/Bangkok', 'Asia/Singapore']

type Form = {
  name: string
  socialAccountId: string
  timeOfDay: string
  days: number[]
  timezone: string
  productName: string
  productDescription: string
  targetAudience: string
  highlights: string
  mode: 'REVIEW' | 'AUTO'
  acknowledged: boolean
}

const blankForm = (channelId: string): Form => ({
  name: 'Bài mỗi ngày',
  socialAccountId: channelId,
  timeOfDay: '19:00',
  days: [...DAY_ORDER],
  timezone: TIMEZONES.includes(browserZone()) ? browserZone() : TIMEZONES[0],
  productName: '',
  productDescription: '',
  targetAudience: '',
  highlights: '',
  mode: 'REVIEW',
  acknowledged: false,
})

function browserZone() {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return TIMEZONES[0]
  }
}

export function DailyPage() {
  const status = useAppDataResource<DailyStatus>('/automation/status')
  if (status.loading) return <PageState title="Đang tải Daily..." />
  if (status.error || !status.data) {
    return <PageState title="Không tải được Daily" description={status.error ?? undefined} onRetry={status.reload} />
  }
  if (!status.data.enabled) {
    return (
      <section>
        <PageHeader title="Daily" subtitle="Tự soạn và đăng một bài mỗi ngày." />
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
          <EmptyState
            icon={<CalendarClock size={20} />}
            title="Daily chưa được bật"
            description="Tính năng này đang được thử nghiệm và chưa mở cho tài khoản của bạn. Khi được bật, bạn sẽ đặt giờ một lần và duyệt bản nháp mỗi ngày."
          />
        </div>
      </section>
    )
  }
  return <DailyHome status={status.data} />
}

function DailyHome({ status }: { status: DailyStatus }) {
  const channelsState = useAppDataResource<AppChannel[]>('/app-data/channels')
  const rulesState = useAppDataResource<DailyRule[]>('/automation/rules')
  const runsState = useAppDataResource<DailyRun[]>('/automation/runs')
  const [creating, setCreating] = useState(false)
  const [notice, setNotice] = useState<{ tone: 'ok' | 'error'; text: string } | null>(null)

  if (channelsState.loading || rulesState.loading || runsState.loading) {
    return <PageState title="Đang tải Daily..." />
  }
  if (channelsState.error || rulesState.error || runsState.error || !channelsState.data || !rulesState.data || !runsState.data) {
    return (
      <PageState
        title="Không tải được Daily"
        description={channelsState.error ?? rulesState.error ?? runsState.error ?? undefined}
        onRetry={() => { channelsState.reload(); rulesState.reload(); runsState.reload() }}
      />
    )
  }

  const facebookChannels = channelsState.data.filter((channel) => channel.active && channel.badge === 'Facebook')
  const rules = rulesState.data
  const runs = runsState.data
  const pending = runs.filter((run) => run.status === 'PENDING_REVIEW')
  const history = runs.filter((run) => run.status !== 'PENDING_REVIEW').slice(0, 5)

  function refresh() {
    rulesState.reload()
    runsState.reload()
  }

  if (facebookChannels.length === 0 && rules.length === 0) {
    return (
      <section>
        <PageHeader title="Daily" subtitle="Tự soạn và đăng một bài mỗi ngày." />
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
          <EmptyState
            icon={<Link2 size={20} />}
            title="Cần kết nối Facebook Page trước"
            description="Daily hiện đăng lên một Facebook Page. Kết nối Page rồi quay lại để tạo rule đầu tiên."
            actionLabel="Kết nối kênh"
            actionTo="/app/connections"
          />
        </div>
      </section>
    )
  }

  if (creating) {
    return (
      <RuleWizard
        status={status}
        channels={facebookChannels}
        onCancel={() => setCreating(false)}
        onCreated={() => {
          setCreating(false)
          setNotice({ tone: 'ok', text: 'Đã lưu rule. Daily sẽ soạn bản nháp trước giờ đăng để bạn duyệt.' })
          refresh()
        }}
      />
    )
  }

  const canAdd = rules.length < status.maxRulesPerUser

  return (
    <section className="space-y-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <PageHeader
          title="Daily"
          subtitle="Đặt giờ một lần, Daily soạn bản nháp mỗi ngày và bạn duyệt trước khi đăng."
        />
        {rules.length > 0 && (
          <button
            type="button"
            disabled={!canAdd}
            onClick={() => setCreating(true)}
            className="min-h-11 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0] disabled:bg-slate-400"
          >
            Tạo rule
          </button>
        )}
      </div>

      {notice && (
        <p role="status" className={`rounded-lg border px-4 py-3 text-sm font-semibold ${notice.tone === 'ok' ? 'border-emerald-200 bg-emerald-50 text-emerald-800' : 'border-rose-200 bg-rose-50 text-rose-800'}`}>
          {notice.text}
        </p>
      )}

      <section aria-labelledby="rules-heading" className="space-y-3">
        <h2 id="rules-heading" className="text-base font-bold">Rule</h2>
        {rules.length === 0 ? (
          <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
            <EmptyState
              icon={<CalendarClock size={20} />}
              title="Chưa có rule nào"
              description="Chọn Page, giờ đăng và chủ đề một lần. Mỗi ngày Daily soạn bài mới và đưa vào hàng chờ duyệt."
            />
            <div className="flex justify-center pb-8">
              <button type="button" onClick={() => setCreating(true)} className="min-h-11 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0]">
                Tạo rule đầu tiên
              </button>
            </div>
          </div>
        ) : (
          rules.map((rule) => <RuleCard key={rule.id} rule={rule} onChanged={refresh} onNotice={setNotice} />)
        )}
      </section>

      <section aria-labelledby="queue-heading" className="space-y-3">
        <h2 id="queue-heading" className="text-base font-bold">
          Chờ duyệt {pending.length > 0 && <span className="ml-1 rounded-full bg-amber-100 px-2 py-0.5 text-xs text-amber-900">{pending.length}</span>}
        </h2>
        {pending.length === 0 ? (
          <p className="text-sm text-[var(--muted-text)]">Chưa có bản nháp nào chờ duyệt. Bản nháp xuất hiện trước giờ đăng của rule.</p>
        ) : (
          pending.map((run) => <DraftCard key={run.id} run={run} onChanged={refresh} onNotice={setNotice} />)
        )}
      </section>

      {history.length > 0 && (
        <section aria-labelledby="history-heading" className="space-y-2">
          <h2 id="history-heading" className="text-base font-bold">Gần đây</h2>
          <ul className="space-y-2">
            {history.map((run) => (
              <li key={run.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 py-3 text-sm">
                <span>{run.ruleName} · {formatDay(run.runDate)}</span>
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${run.status === 'SCHEDULED' ? 'bg-emerald-50 text-emerald-800' : run.status === 'FAILED' ? 'bg-rose-50 text-rose-800' : 'bg-slate-100 text-slate-700'}`}>
                  {run.status === 'SCHEDULED' ? 'Đã lên lịch' : run.error ? (RUN_ERROR_LABELS[run.error] ?? run.error) : 'Đã bỏ qua'}
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}
    </section>
  )
}

type NoticeSetter = (notice: { tone: 'ok' | 'error'; text: string } | null) => void

function RuleCard({ rule, onChanged, onNotice }: { rule: DailyRule; onChanged: () => void; onNotice: NoticeSetter }) {
  const [busy, setBusy] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [preview, setPreview] = useState<DailyPreviewItem[] | null>(null)
  const paused = rule.status === 'PAUSED'

  async function act(action: () => Promise<unknown>, success: string) {
    setBusy(true)
    onNotice(null)
    try {
      await action()
      onNotice({ tone: 'ok', text: success })
      onChanged()
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không thực hiện được. Vui lòng thử lại.') })
    } finally {
      setBusy(false)
      setConfirmDelete(false)
    }
  }

  async function togglePreview() {
    if (preview) {
      setPreview(null)
      return
    }
    try {
      const response = await api.get<DailyPreviewItem[]>(`/automation/rules/${rule.id}/preview`)
      setPreview(response.data)
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không xem được lịch 7 ngày tới.') })
    }
  }

  return (
    <article className="space-y-3 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm">
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="font-bold">{rule.name}</h3>
          <p className="text-sm text-[var(--muted-text)]">
            Mỗi ngày {rule.timeOfDay} · {describeDays(rule.daysOfWeek)} · {rule.timezone} · {rule.accountName ?? 'Kênh đã ngắt'}
          </p>
        </div>
        <div className="flex gap-2">
          <span className="rounded-full bg-[var(--soft-bg)] px-3 py-1 text-xs font-semibold">{rule.approvalMode === 'AUTO' ? 'Tự động đăng' : 'Duyệt trước'}</span>
          <span className={`rounded-full px-3 py-1 text-xs font-semibold ${paused ? 'bg-rose-50 text-rose-800' : 'bg-emerald-50 text-emerald-800'}`}>{paused ? 'Tạm dừng' : 'Đang chạy'}</span>
        </div>
      </div>
      {paused && rule.pausedReason && (
        <p role="status" className="rounded-lg bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">
          {PAUSE_REASON_LABELS[rule.pausedReason] ?? 'Rule đang tạm dừng.'}
        </p>
      )}
      <div className="flex flex-wrap items-center gap-2">
        <button
          type="button"
          disabled={busy}
          onClick={() => void act(() => api.post(`/automation/rules/${rule.id}/${paused ? 'resume' : 'pause'}`), paused ? 'Đã bật lại rule.' : 'Đã tạm dừng rule.')}
          className="min-h-11 rounded-full border border-[var(--app-border)] px-4 text-sm font-bold hover:bg-[var(--soft-bg)] disabled:opacity-50"
        >
          {paused ? 'Bật lại' : 'Tạm dừng'}
        </button>
        <button type="button" onClick={() => void togglePreview()} aria-expanded={preview !== null} className="min-h-11 rounded-full border border-[var(--app-border)] px-4 text-sm font-bold hover:bg-[var(--soft-bg)]">
          Xem 7 ngày tới
        </button>
        {!confirmDelete && (
          <button type="button" onClick={() => setConfirmDelete(true)} className="min-h-11 px-3 text-sm font-semibold text-slate-700 underline underline-offset-4 hover:text-rose-700">
            Xóa rule
          </button>
        )}
      </div>
      {confirmDelete && (
        <div role="alertdialog" aria-label="Xác nhận xóa rule" className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 sm:flex-row sm:items-center sm:justify-between">
          <p>Xóa rule "{rule.name}"? Bản nháp chờ duyệt của rule cũng bị xóa. Bài đã lên lịch vẫn giữ nguyên ở Lịch đăng.</p>
          <div className="flex gap-2">
            <button type="button" onClick={() => setConfirmDelete(false)} className="min-h-11 rounded-lg border border-amber-300 px-4 font-semibold hover:bg-amber-100">Hủy</button>
            <button type="button" disabled={busy} onClick={() => void act(() => api.delete(`/automation/rules/${rule.id}`), 'Đã xóa rule.')} className="min-h-11 rounded-lg bg-rose-600 px-4 font-bold text-white hover:bg-rose-700">Xóa rule</button>
          </div>
        </div>
      )}
      {preview && (
        <ul className="space-y-1 text-sm">
          {preview.map((item) => (
            <li key={item.at} className="flex justify-between rounded-lg border border-[var(--app-border)] px-3 py-2">
              <span>{formatDay(item.runDate)} · {rule.timeOfDay}</span>
              <span className="text-[var(--muted-text)]">Soạn trước khoảng {Math.round(rule.leadMinutes / 60)} giờ</span>
            </li>
          ))}
        </ul>
      )}
    </article>
  )
}

function DraftCard({ run, onChanged, onNotice }: { run: DailyRun; onChanged: () => void; onNotice: NoticeSetter }) {
  const original = run.draft?.content ?? ''
  const [content, setContent] = useState(original)
  const [busy, setBusy] = useState(false)

  async function approve() {
    setBusy(true)
    onNotice(null)
    try {
      if (run.postId && run.draft && content.trim() !== original.trim()) {
        // Editing goes through the existing post endpoint; hashtags and CTA are sent back unchanged.
        await api.put(`/posts/${run.postId}`, {
          title: run.draft.title ?? undefined,
          platformContents: { FACEBOOK: { content: content.trim() } },
          hashtags: run.draft.hashtags,
          cta: run.draft.cta ?? undefined,
        })
      }
      await api.post(`/automation/runs/${run.id}/approve`)
      onNotice({ tone: 'ok', text: 'Đã duyệt. Bài xuất hiện ở Lịch đăng.' })
      onChanged()
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không duyệt được bản nháp.') })
    } finally {
      setBusy(false)
    }
  }

  async function skip() {
    setBusy(true)
    onNotice(null)
    try {
      await api.post(`/automation/runs/${run.id}/skip`)
      onChanged()
    } catch (error) {
      onNotice({ tone: 'error', text: getErrorMessage(error, 'Không bỏ qua được bản nháp.') })
    } finally {
      setBusy(false)
    }
  }

  return (
    <article className="space-y-3 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <h3 className="font-bold">{run.ruleName} · {formatDay(run.runDate)}</h3>
        <span className="rounded-full bg-amber-50 px-3 py-1 text-xs font-semibold text-amber-900">Đăng lúc {formatTime(run.plannedFor)}</span>
      </div>
      <label className="block text-sm font-bold">
        Bản nháp (sửa được)
        <textarea
          value={content}
          onChange={(event) => setContent(event.target.value)}
          rows={4}
          className="mt-2 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 py-2 text-sm font-normal leading-6"
        />
      </label>
      <div className="flex flex-wrap gap-2">
        <button type="button" disabled={busy || !content.trim()} onClick={() => void approve()} className="min-h-11 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0] disabled:bg-slate-400">
          Duyệt và lên lịch
        </button>
        <button type="button" disabled={busy} onClick={() => void skip()} className="min-h-11 rounded-full border border-[var(--app-border)] px-5 text-sm font-bold hover:bg-[var(--soft-bg)] disabled:opacity-50">
          Bỏ qua ngày này
        </button>
      </div>
    </article>
  )
}

function RuleWizard({
  status,
  channels,
  onCancel,
  onCreated,
}: {
  status: DailyStatus
  channels: AppChannel[]
  onCancel: () => void
  onCreated: () => void
}) {
  const [step, setStep] = useState(0)
  const [form, setForm] = useState<Form>(() => blankForm(channels.length === 1 ? channels[0].id : ''))
  const [notice, setNotice] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)

  function issue(index: number): string | null {
    if (index === 0 && !form.socialAccountId) return 'Chọn Page để đăng.'
    if (index === 1 && (!form.timeOfDay || form.days.length === 0)) return 'Chọn giờ đăng và ít nhất một ngày trong tuần.'
    if (index === 2 && (!form.productName.trim() || !form.productDescription.trim() || !form.targetAudience.trim() || !form.highlights.trim())) {
      return 'Điền đủ tên sản phẩm, mô tả, khách hàng mục tiêu và điểm nổi bật.'
    }
    if (index === 3 && form.mode === 'AUTO' && !form.acknowledged) return 'Xác nhận bạn hiểu bài sẽ đăng công khai không qua bước duyệt.'
    return null
  }

  function go(target: number) {
    setError('')
    if (target <= step || CAN_SKIP) {
      setNotice('')
      setStep(target)
      return
    }
    for (let index = step; index < target; index += 1) {
      const problem = issue(index)
      if (problem) {
        setStep(index)
        setNotice(`${BLOCKED} ${problem}`)
        return
      }
    }
    setNotice('')
    setStep(target)
  }

  async function save() {
    const blocked = CAN_SKIP ? undefined : [0, 1, 2, 3].find((index) => issue(index))
    if (blocked !== undefined) {
      setStep(blocked)
      setNotice(`${BLOCKED} ${issue(blocked)}`)
      return
    }
    const request: DailyRuleRequest = {
      name: form.name.trim() || 'Bài mỗi ngày',
      socialAccountId: form.socialAccountId,
      timezone: form.timezone,
      timeOfDay: form.timeOfDay,
      daysOfWeek: form.days,
      approvalMode: form.mode,
      brief: {
        productName: form.productName.trim(),
        productDescription: form.productDescription.trim(),
        targetAudience: form.targetAudience.trim(),
        highlights: form.highlights.trim(),
      },
    }
    setSaving(true)
    setError('')
    try {
      await api.post('/automation/rules', request)
      onCreated()
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Không lưu được rule. Vui lòng thử lại.'))
    } finally {
      setSaving(false)
    }
  }

  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((current) => ({ ...current, [key]: value }))
  const showNotice = notice && issue(step)
  const inputClass = 'mt-1 h-11 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm font-normal'
  const last = step === STEPS.length - 1

  return (
    <section className="mx-auto max-w-3xl space-y-4">
      <PageHeader title="Tạo rule Daily" subtitle="Bốn bước, mỗi bước một quyết định." />
      <Stepper label="Các bước tạo rule" steps={STEPS} current={step} isDone={(index) => index < step && !issue(index)} onGo={go} />
      {showNotice && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">{notice}</p>}
      {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">{error}</p>}

      <div className="space-y-4 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm">
        {step === 0 && (
          <>
            <h2 className="text-base font-bold">Đăng lên Page nào?</h2>
            <div className="grid gap-2 sm:grid-cols-2">
              {channels.map((channel) => (
                <button
                  key={channel.id}
                  type="button"
                  aria-pressed={form.socialAccountId === channel.id}
                  onClick={() => set('socialAccountId', channel.id)}
                  className={`flex min-h-14 flex-col justify-center rounded-xl border px-4 py-2 text-left ${form.socialAccountId === channel.id ? 'border-[#8594ea] bg-[var(--soft-bg)]' : 'border-[var(--app-border)] hover:bg-[var(--soft-bg)]'}`}
                >
                  <b className="text-sm">{channel.name}</b>
                  <span className="text-xs text-[var(--muted-text)]">Facebook Page{form.socialAccountId === channel.id ? ' · Đã chọn' : ''}</span>
                </button>
              ))}
            </div>
            <p className="text-sm text-[var(--muted-text)]">Mỗi rule đăng lên một Page. Muốn đăng nhiều Page, tạo nhiều rule.</p>
          </>
        )}

        {step === 1 && (
          <>
            <h2 className="text-base font-bold">Giờ và ngày đăng</h2>
            <label className="block text-sm font-bold">
              Giờ đăng
              <input type="time" value={form.timeOfDay} onChange={(event) => set('timeOfDay', event.target.value)} className={inputClass} />
            </label>
            <div role="group" aria-label="Ngày trong tuần" className="space-y-1 text-sm font-bold">
              Ngày trong tuần
              <div className="flex flex-wrap gap-2 pt-1">
                {DAY_ORDER.map((day) => (
                  <button
                    key={day}
                    type="button"
                    aria-pressed={form.days.includes(day)}
                    onClick={() => set('days', form.days.includes(day) ? form.days.filter((value) => value !== day) : [...form.days, day].sort())}
                    className={`min-h-11 min-w-11 rounded-lg border text-sm font-bold ${form.days.includes(day) ? 'border-[#505db8] bg-[#505db8] text-white' : 'border-[var(--app-border)] hover:bg-[var(--soft-bg)]'}`}
                  >
                    {WEEKDAY_LABELS[day]}
                  </button>
                ))}
              </div>
            </div>
            <label className="block text-sm font-bold">
              Múi giờ
              <select value={form.timezone} onChange={(event) => set('timezone', event.target.value)} className={inputClass}>
                {[...new Set([form.timezone, ...TIMEZONES])].map((zone) => <option key={zone}>{zone}</option>)}
              </select>
            </label>
          </>
        )}

        {step === 2 && (
          <>
            <h2 className="text-base font-bold">Chủ đề của bài</h2>
            <p className="text-sm text-[var(--muted-text)]">Mỗi ngày AI soạn một bài mới từ thông tin này và tránh lặp lại các tiêu đề gần đây. Mỗi bài dùng một lượt AI.</p>
            <label className="block text-sm font-bold">Tên rule<input value={form.name} onChange={(event) => set('name', event.target.value)} className={inputClass} /></label>
            <label className="block text-sm font-bold">Tên sản phẩm *<input value={form.productName} onChange={(event) => set('productName', event.target.value)} className={inputClass} placeholder="Ví dụ: Bó hoa hướng dương" /></label>
            <label className="block text-sm font-bold">Mô tả *<textarea value={form.productDescription} onChange={(event) => set('productDescription', event.target.value)} rows={3} className="mt-1 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 py-2 text-sm font-normal" /></label>
            <label className="block text-sm font-bold">Khách hàng mục tiêu *<input value={form.targetAudience} onChange={(event) => set('targetAudience', event.target.value)} className={inputClass} /></label>
            <label className="block text-sm font-bold">Điểm nổi bật *<input value={form.highlights} onChange={(event) => set('highlights', event.target.value)} className={inputClass} /></label>
          </>
        )}

        {step === 3 && (
          <>
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold">Duyệt hay tự động?</h2>
              <HelpTip label="Giải thích chế độ duyệt">
                <p><b>Duyệt trước:</b> bạn xem và duyệt từng bài trước giờ đăng. Quá giờ mà chưa duyệt thì ngày đó bị bỏ qua.</p>
                <p className="mt-2"><b>Tự động đăng:</b> bài lên lịch ngay khi soạn xong, không qua duyệt.</p>
              </HelpTip>
            </div>
            <div className="grid gap-2 sm:grid-cols-2">
              <button type="button" aria-pressed={form.mode === 'REVIEW'} onClick={() => setForm((current) => ({ ...current, mode: 'REVIEW', acknowledged: false }))} className={`flex min-h-14 flex-col justify-center rounded-xl border px-4 py-2 text-left ${form.mode === 'REVIEW' ? 'border-[#8594ea] bg-[var(--soft-bg)]' : 'border-[var(--app-border)]'}`}>
                <b className="text-sm">Duyệt trước (khuyến nghị)</b>
                <span className="text-xs text-[var(--muted-text)]">Bạn duyệt từng bài trước giờ đăng</span>
              </button>
              <button type="button" disabled={!status.autoEnabled} aria-pressed={form.mode === 'AUTO'} onClick={() => setForm((current) => ({ ...current, mode: 'AUTO', acknowledged: false }))} className={`flex min-h-14 flex-col justify-center rounded-xl border px-4 py-2 text-left disabled:cursor-not-allowed disabled:bg-[var(--soft-bg)] ${form.mode === 'AUTO' ? 'border-[#8594ea] bg-[var(--soft-bg)]' : 'border-[var(--app-border)]'}`}>
                <b className="text-sm">Tự động đăng</b>
                <span className="text-xs text-[var(--muted-text)]">{status.autoEnabled ? 'Bài lên lịch ngay, không qua duyệt' : 'Chưa mở cho tài khoản của bạn'}</span>
              </button>
            </div>
            {form.mode === 'AUTO' && (
              <div role="alertdialog" aria-label="Xác nhận chế độ tự động" className="rounded-lg border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900">
                <p>Bài sẽ tự đăng công khai mà không qua bước duyệt. Bạn vẫn sửa hoặc hủy được trong Lịch đăng trước giờ đăng.</p>
                <label className="mt-2 flex min-h-11 cursor-pointer items-center gap-2 font-semibold">
                  <input type="checkbox" checked={form.acknowledged} onChange={(event) => set('acknowledged', event.target.checked)} /> Tôi hiểu
                </label>
              </div>
            )}
            <div className="text-sm">
              <b>Tóm tắt</b>
              <p className="text-[var(--muted-text)]">
                Mỗi ngày {form.timeOfDay} ({describeDays(form.days)}), đăng lên {channels.find((channel) => channel.id === form.socialAccountId)?.name ?? '—'}, chủ đề "{form.productName || '—'}".
                Một rule dùng khoảng một lượt AI mỗi ngày; Daily dừng lại khi chỉ còn {status.manualQuotaReserve} lượt để bạn dùng tay.
              </p>
            </div>
          </>
        )}
      </div>

      <div className="flex items-center justify-end gap-2 border-t border-[var(--app-border)] pt-3">
        {step === 0
          ? <button type="button" onClick={onCancel} className="mr-auto min-h-11 rounded-lg border border-[var(--app-border)] px-4 text-sm font-semibold hover:bg-[var(--soft-bg)]">Hủy</button>
          : <button type="button" onClick={() => go(step - 1)} className="mr-auto min-h-11 rounded-lg border border-[var(--app-border)] px-4 text-sm font-semibold hover:bg-[var(--soft-bg)]">Quay lại</button>}
        {last
          ? <button type="button" disabled={saving} onClick={() => void save()} className="min-h-11 rounded-lg bg-blue-600 px-5 text-sm font-bold text-white hover:bg-blue-700 disabled:bg-blue-300">{saving ? 'Đang lưu...' : 'Lưu rule'}</button>
          : (
            <>
              {CAN_SKIP && <button type="button" onClick={() => go(step + 1)} className="min-h-11 px-4 text-sm font-semibold underline underline-offset-4">Bỏ qua bước này</button>}
              <button type="button" onClick={() => go(step + 1)} className="min-h-11 rounded-lg bg-blue-600 px-5 text-sm font-bold text-white hover:bg-blue-700">Tiếp tục</button>
            </>
          )}
      </div>
      <p className="text-xs text-[var(--muted-text)]">
        Tạo xong, xem bản nháp đầu tiên ở mục Chờ duyệt. Bài đã duyệt hiện ở <Link to="/app/schedule" className="underline">Lịch đăng</Link>.
      </p>
    </section>
  )
}

function describeDays(days: number[]) {
  return days.length === 7 ? 'T2–CN' : [...days].sort((a, b) => a - b).map((day) => WEEKDAY_LABELS[day]).join(', ')
}

function formatDay(isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1, day)
  return new Intl.DateTimeFormat('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' }).format(date)
}

function formatTime(instant: string) {
  return new Intl.DateTimeFormat('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit' }).format(new Date(instant))
}
