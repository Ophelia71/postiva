import type { AxiosAdapter, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import type { AppChannel } from './appDataApi'
import type { DailyPreviewItem, DailyRule, DailyRuleRequest, DailyRun, DailyStatus } from './dailyApi'

/**
 * In-memory stand-in for the /automation API so the Daily page can be used in the Vite dev server before the backend
 * exists. Only /automation/* requests are intercepted; everything else (channels, uploads) still hits the real backend.
 * State lives in this module and resets on page reload.
 */

const STATUS: DailyStatus = { enabled: true, autoEnabled: true, maxRulesPerUser: 3, manualQuotaReserve: 5 }
const PLATFORM_BY_BADGE: Record<string, string> = { Facebook: 'FACEBOOK', Instagram: 'INSTAGRAM', Threads: 'THREADS' }

let rules: DailyRule[] = []
let runs: DailyRun[] = []

function isoDate(date: Date) {
  return date.toISOString().slice(0, 10)
}

function isoWeekday(date: Date) {
  return date.getDay() === 0 ? 7 : date.getDay()
}

/** The next `count` days on which the rule fires, starting today. Ignores the rule's timezone; good enough for a mock. */
function upcoming(rule: DailyRule, count: number): DailyPreviewItem[] {
  const [hours, minutes] = rule.timeOfDay.split(':').map(Number)
  const items: DailyPreviewItem[] = []
  const day = new Date()
  for (let offset = 0; items.length < count && offset < 60; offset += 1) {
    const at = new Date(day.getFullYear(), day.getMonth(), day.getDate() + offset, hours || 0, minutes || 0)
    if (at > new Date() && rule.daysOfWeek.includes(isoWeekday(at))) items.push({ runDate: isoDate(at), at: at.toISOString() })
  }
  return items
}

function sampleDraft(rule: DailyRule) {
  return {
    title: rule.brief.productName,
    content: `[Bản nháp mẫu] ${rule.brief.productName}: ${rule.brief.highlights || rule.brief.productDescription}`.slice(0, 400),
    hashtags: ['#postiva', '#daily'],
    cta: rule.brief.goal ?? null,
  }
}

async function findChannel(api: AxiosInstance, socialAccountId: string) {
  try {
    const channels = (await api.get<AppChannel[]>('/app-data/channels')).data
    return channels.find((channel) => channel.id === socialAccountId)
  } catch {
    return undefined
  }
}

function toRule(request: DailyRuleRequest, channel: AppChannel | undefined, existing?: DailyRule): DailyRule {
  const now = new Date().toISOString()
  return {
    id: existing?.id ?? crypto.randomUUID(),
    socialAccountId: request.socialAccountId,
    accountName: channel?.name ?? existing?.accountName ?? 'Kênh đã chọn',
    platform: PLATFORM_BY_BADGE[channel?.badge ?? ''] ?? existing?.platform ?? 'FACEBOOK',
    name: request.name,
    status: existing?.status ?? 'ACTIVE',
    pausedReason: existing?.pausedReason ?? null,
    timezone: request.timezone,
    timeOfDay: request.timeOfDay,
    daysOfWeek: request.daysOfWeek,
    approvalMode: request.approvalMode,
    leadMinutes: 60,
    brief: request.brief,
    mediaIds: request.mediaIds,
    createdAt: existing?.createdAt ?? now,
    updatedAt: now,
  }
}

/** A REVIEW rule gets a draft waiting for approval right away, so the review flow can be tried without waiting a day. */
function queueRun(rule: DailyRule) {
  const next = upcoming(rule, 1)[0]
  if (!next) return
  runs = [{
    id: crypto.randomUUID(),
    ruleId: rule.id,
    ruleName: rule.name,
    platform: rule.platform,
    runDate: next.runDate,
    plannedFor: next.at,
    status: rule.approvalMode === 'AUTO' ? 'SCHEDULED' : 'PENDING_REVIEW',
    postId: null,
    scheduledPostId: null,
    error: null,
    draft: sampleDraft(rule),
  }, ...runs.filter((run) => run.ruleId !== rule.id || run.status !== 'PENDING_REVIEW')]
}

function setRunStatus(id: string, status: DailyRun['status'], error: string | null) {
  runs = runs.map((run) => (run.id === id ? { ...run, status, error } : run))
}

class MockError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

async function handle(api: AxiosInstance, method: string, path: string, body: unknown): Promise<unknown> {
  const segments = path.replace(/^\/automation\/?/, '').split('/').filter(Boolean)
  const [resource, id, action] = segments

  if (method === 'get' && resource === 'status') return STATUS
  if (method === 'get' && resource === 'runs') return runs

  if (resource === 'rules') {
    if (method === 'get' && !id) return rules
    if (method === 'post' && !id) {
      if (rules.length >= STATUS.maxRulesPerUser) throw new MockError(400, `Tối đa ${STATUS.maxRulesPerUser} rule.`)
      const request = body as DailyRuleRequest
      const rule = toRule(request, await findChannel(api, request.socialAccountId))
      rules = [...rules, rule]
      queueRun(rule)
      return rule
    }

    const rule = rules.find((item) => item.id === id)
    if (!rule) throw new MockError(404, 'Không tìm thấy rule.')

    if (method === 'get' && action === 'preview') return upcoming(rule, 7)
    if (method === 'put' && !action) {
      const request = body as DailyRuleRequest
      const updated = toRule(request, await findChannel(api, request.socialAccountId), rule)
      rules = rules.map((item) => (item.id === id ? updated : item))
      return updated
    }
    if (method === 'delete' && !action) {
      rules = rules.filter((item) => item.id !== id)
      runs = runs.filter((run) => run.ruleId !== id || run.status !== 'PENDING_REVIEW')
      return null
    }
    if (method === 'post' && (action === 'pause' || action === 'resume')) {
      const paused = action === 'pause'
      rules = rules.map((item) => (item.id === id
        ? { ...item, status: paused ? 'PAUSED' : 'ACTIVE', pausedReason: paused ? 'USER' : null, updatedAt: new Date().toISOString() }
        : item))
      return null
    }
  }

  if (resource === 'runs' && id && method === 'post') {
    if (!runs.some((run) => run.id === id)) throw new MockError(404, 'Không tìm thấy bản nháp.')
    if (action === 'approve' || action === 'skip') {
      if (action === 'approve') setRunStatus(id, 'SCHEDULED', null)
      else setRunStatus(id, 'SKIPPED', 'USER_SKIPPED')
      return null
    }
  }

  throw new MockError(404, `Mock Daily chưa hỗ trợ ${method.toUpperCase()} ${path}`)
}

export function installDailyMock(api: AxiosInstance) {
  const adapter: AxiosAdapter = async (config) => {
    const path = String(config.url ?? '')
    const body = typeof config.data === 'string' && config.data ? JSON.parse(config.data) : config.data
    try {
      const data = await handle(api, (config.method ?? 'get').toLowerCase(), path, body)
      return { data: { success: true, message: null, data }, status: 200, statusText: 'OK', headers: {}, config }
    } catch (error) {
      const status = error instanceof MockError ? error.status : 500
      const message = error instanceof Error ? error.message : 'Mock Daily lỗi'
      const response = { data: { success: false, message, data: null }, status, statusText: 'Error', headers: {}, config }
      // Mirror axios: non-2xx responses reject with an error carrying the response.
      return Promise.reject(Object.assign(new Error(message), { isAxiosError: true, response, config }))
    }
  }

  api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    if (String(config.url ?? '').startsWith('/automation')) config.adapter = adapter
    return config
  })
}
