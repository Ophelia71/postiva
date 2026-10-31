import type { AxiosAdapter, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import type { AppChannel } from './appDataApi'
import { isDemoActive } from './demo'
import type { CreateWeeklyPlanRequest, PerformanceSummary, WeeklyPlan, WeeklyPlanItem } from './dailyApi'

/**
 * In-memory stand-in for the /weekly-plans API so Daily can be used in the Vite dev server before the backend runs.
 * Only /weekly-plans/* and edits of mock drafts are intercepted; channels and uploads still hit the real backend.
 * State lives in this module and resets on page reload.
 */

const PLATFORM_BY_BADGE: Record<string, string> = { Facebook: 'FACEBOOK', Instagram: 'INSTAGRAM', Threads: 'THREADS' }
// What each kind of post says, in a shop owner's own words; {p} is the product, {h} what makes it good.
const DRAFTS: Record<string, string> = {
  'Giới thiệu sản phẩm': '{P} ở shop có gì khác? {H}. Ghé thử một lần, hoặc nhắn tin để được giao tận nơi nhé!',
  'Hướng dẫn': 'Mẹo nhỏ để thưởng thức {p} trọn vị nhất: dùng ngay khi vừa nhận và đừng ngại hỏi shop cách bảo quản. Lưu bài này lại để lần sau khỏi quên nhé!',
  Feedback: '"Thử một lần là ưng ngay" — cảm ơn bạn khách đã gửi lời khen cho {p}. Điều khách thích nhất: {h}.',
  'Hậu trường': 'Một ngày ở shop bắt đầu từ sáng sớm: chuẩn bị {p} thật kỹ và kiểm tra từng đơn trước khi giao. Bạn muốn xem khâu nào kỹ hơn? Bình luận cho shop biết nhé!',
  'Ưu đãi': 'Cuối tuần này: mua 2 phần {p} tặng 1 phần quà nhỏ. Số lượng có hạn, nhắn shop ngay để giữ phần nhé!',
  'Hỏi đáp': 'Hỏi nhanh – đáp gọn về {p}. Giao bao lâu? Trong ngày. Có gì đặc biệt? {H}. Còn thắc mắc, bình luận để shop trả lời nha!',
  'Kêu gọi mua': '{P} tuần này sắp hết. Ai còn đang phân vân thì nhắn shop ngay hôm nay để giữ hàng nhé!',
}

function draftFor(postType: string | null | undefined, productName: string, highlights: string) {
  // A shop's product list ("Cà phê muối, bạc xỉu, …") reads best as its first item.
  const product = (productName.split(/[,;\n]/)[0] || 'sản phẩm').trim()
  const good = (highlights.split(/[\n]/)[0] || 'chất lượng ổn định, giao nhanh').trim().replace(/[.!]+$/, '')
  const lowerFirst = (text: string) => text.charAt(0).toLowerCase() + text.slice(1)
  const upperFirst = (text: string) => text.charAt(0).toUpperCase() + text.slice(1)
  return (DRAFTS[postType ?? ''] ?? DRAFTS['Giới thiệu sản phẩm'])
    .replace('{P}', upperFirst(product)).replace('{p}', lowerFirst(product))
    .replace('{H}', upperFirst(good)).replace('{h}', lowerFirst(good))
}

function hashtagFor(productName: string) {
  const first = (productName.split(/[,;\n]/)[0] || '').trim()
  const tag = first.split(/\s+/).filter(Boolean).map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join('')
  return tag ? [`#${tag}`] : []
}

const IDEAS = [
  { postType: 'Giới thiệu sản phẩm', topic: 'Câu chuyện đằng sau sản phẩm', reason: 'Mở đầu tuần bằng câu chuyện giúp khách nhớ đến shop.' },
  { postType: 'Hướng dẫn', topic: 'Mẹo dùng sản phẩm hiệu quả', reason: 'Bài hữu ích hay được lưu và chia sẻ, tăng độ phủ tự nhiên.' },
  { postType: 'Feedback', topic: 'Khách hàng nói gì về shop', reason: 'Bằng chứng xã hội giúp khách mới yên tâm đặt hàng.' },
  { postType: 'Hậu trường', topic: 'Một ngày ở shop', reason: 'Nội dung chân thật tạo thiện cảm và kéo bình luận.' },
  { postType: 'Ưu đãi', topic: 'Ưu đãi cuối tuần', reason: 'Cuối tuần khách rảnh và dễ ra quyết định mua hơn.' },
  { postType: 'Hỏi đáp', topic: 'Giải đáp thắc mắc thường gặp', reason: 'Trả lời trước các câu hỏi giúp giảm tin nhắn hỏi lại.' },
  { postType: 'Kêu gọi mua', topic: 'Chốt đơn trước khi hết hàng', reason: 'Tạo cảm giác khan hiếm để kết thúc tuần bằng đơn hàng.' },
]

let plans: WeeklyPlan[] = []

function sampleInsights(): PerformanceSummary {
  return {
    postsAnalyzed: 18,
    postsWithMetrics: 18,
    warning: null,
    averageEngagement: 24.5,
    topPosts: [
      { excerpt: 'Video pha ly cà phê muối đầu tiên của ngày ở Mây #CaPheMuoi', publishedAt: null, mediaType: 'VIDEO', engagement: 86, likes: 52, comments: 9, shares: 5, views: null },
      { excerpt: 'Chị Mai review: "uống một lần là ghiền" #MayCoffee', publishedAt: null, mediaType: 'IMAGE', engagement: 61, likes: 40, comments: 6, shares: 3, views: null },
    ],
    weakPosts: [],
    bestWeekdays: [5, 6, 3],
    bestHour: 20,
    engagementByMediaType: { VIDEO: 52.3, IMAGE: 28.1, TEXT: 9.4 },
    topPostAverageLength: 320,
    otherPostAverageLength: 140,
    topHashtags: ['#CaPheMuoi', '#MayCoffee'],
    postsPerWeek: 2.5,
    insights: [
      'Bài video đạt tương tác gấp 5.6 lần bài chỉ có chữ.',
      'Ngày có tương tác cao nhất: Thứ Sáu, Thứ Bảy, Thứ Tư.',
      'Khung giờ hiệu quả nhất: 20h–21h.',
      'Bài hiệu quả thường dài hơn, khoảng 320 ký tự.',
    ],
  }
}

async function findChannel(api: AxiosInstance, id: string) {
  try {
    return (await api.get<AppChannel[]>('/app-data/channels')).data.find((channel) => channel.id === id)
  } catch {
    return undefined
  }
}

function pickDays(request: CreateWeeklyPlanRequest): string[] {
  const start = new Date(`${request.startDate}T00:00:00`)
  const week = Array.from({ length: 7 }, (_, offset) => new Date(start.getFullYear(), start.getMonth(), start.getDate() + offset))
  const preferred = request.daysOfWeek?.length ? request.daysOfWeek : [5, 6, 3]
  const chosen = new Set<number>()
  for (const weekday of preferred) {
    const index = week.findIndex((day) => (day.getDay() || 7) === weekday)
    if (index >= 0 && chosen.size < request.postsPerWeek) chosen.add(index)
  }
  for (let i = 0; chosen.size < request.postsPerWeek && i < request.postsPerWeek; i += 1) {
    chosen.add(Math.min(6, Math.round((i * 7) / request.postsPerWeek)))
  }
  for (let i = 0; chosen.size < request.postsPerWeek && i < 7; i += 1) chosen.add(i)
  const time = request.postTime ?? '20:00'
  return [...chosen].sort((a, b) => a - b).map((index) => {
    const day = week[index]
    const [hours, minutes] = time.split(':').map(Number)
    return new Date(day.getFullYear(), day.getMonth(), day.getDate(), hours, minutes).toISOString()
  })
}

function localDate(iso: string) {
  const date = new Date(iso)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function updatePlan(id: string, change: (plan: WeeklyPlan) => WeeklyPlan) {
  plans = plans.map((plan) => (plan.id === id ? { ...change(plan), updatedAt: new Date().toISOString() } : plan))
}

/** Drafts appear one by one, like the real background worker. */
function simulateDrafting(planId: string, request: CreateWeeklyPlanRequest) {
  const plan = plans.find((item) => item.id === planId)
  if (!plan) return
  plan.items.forEach((item, index) => {
    window.setTimeout(() => {
      updatePlan(planId, (current) => ({
        ...current,
        items: current.items.map((entry) => entry.id !== item.id ? entry : {
          ...entry,
          status: 'DRAFT',
          post: {
            postId: `mock-${crypto.randomUUID()}`,
            title: entry.topic,
            platformContents: {
              [current.platform ?? 'FACEBOOK']: {
                title: entry.topic ?? '',
                [current.platform === 'INSTAGRAM' ? 'caption' : 'content']:
                  draftFor(entry.postType, request.productName ?? '', request.highlights ?? ''),
              },
            },
            hashtags: hashtagFor(request.productName ?? ''),
            cta: 'Nhắn shop để đặt ngay',
            imageSuggestion: 'Ảnh sản phẩm chụp gần, ánh sáng tự nhiên',
            imageUrl: imagesFor(request, index)[0] ? `/api/files/${imagesFor(request, index)[0]}` : null,
          },
          media: imagesFor(request, index).map((id) => ({ id, url: `/api/files/${id}`, mimeType: null, fileName: null })),
        }),
      }))
      if (index === plan.items.length - 1) updatePlan(planId, (current) => ({ ...current, status: 'READY' }))
    }, 900 * (index + 2))
  })
}

/** Mirrors WeeklyPlanGenerator.imagesFor: all images, or in EACH mode one per post taking them in turn. */
function imagesFor(request: CreateWeeklyPlanRequest, position: number) {
  if (request.imageIds.length < 2 || request.imageMode !== 'EACH') return request.imageIds
  return [request.imageIds[position % request.imageIds.length]]
}

export class MockError extends Error {
  readonly status: number

  constructor(status: number, message: string) {
    super(message)
    this.status = status
  }
}

function requirePlan(id: string | undefined) {
  const plan = plans.find((item) => item.id === id)
  if (!plan) throw new MockError(404, 'Không tìm thấy kế hoạch tuần')
  return plan
}

export async function handleDailyMock(api: AxiosInstance, method: string, url: string, body: unknown): Promise<unknown> {
  const [path] = url.split('?')
  if (path.startsWith('/posts/mock-') && method === 'put') {
    const postId = path.slice('/posts/'.length)
    const update = body as { platformContents?: Record<string, Record<string, string>>; mediaIds?: string[] }
    plans = plans.map((plan) => ({
      ...plan,
      items: plan.items.map((item) => item.post?.postId !== postId ? item : {
        ...item,
        post: { ...item.post, platformContents: { ...item.post.platformContents, ...(update.platformContents ?? {}) } },
        media: update.mediaIds
          ? update.mediaIds.map((id) => item.media.find((media) => media.id === id) ?? { id, url: `/api/files/${id}`, mimeType: null, fileName: null })
          : item.media,
      }),
    }))
    return null
  }

  const [, , resource, ...rest] = path.split('/')
  if (resource === 'insights' && method === 'get') return sampleInsights()
  if (!resource) {
    if (method === 'get') return plans
    if (method === 'post') {
      const request = body as CreateWeeklyPlanRequest
      if (plans.some((plan) => plan.status === 'GENERATING')) throw new MockError(409, 'Postiva đang lên một kế hoạch khác cho bạn.')
      const channel = await findChannel(api, request.socialAccountId)
      const platform = PLATFORM_BY_BADGE[channel?.badge ?? ''] ?? 'FACEBOOK'
      if (platform === 'INSTAGRAM' && request.imageIds.length === 0) {
        throw new MockError(400, 'Instagram cần ít nhất một ảnh hoặc video cho các bài trong tuần')
      }
      const now = new Date().toISOString()
      const items: WeeklyPlanItem[] = pickDays(request).map((at, index) => ({
        id: crypto.randomUUID(),
        plannedDate: localDate(at),
        scheduledTime: at,
        postType: IDEAS[index % IDEAS.length].postType,
        topic: IDEAS[index % IDEAS.length].topic,
        reason: IDEAS[index % IDEAS.length].reason,
        status: 'GENERATING',
        error: null,
        scheduledPostId: null,
        post: null,
        media: [],
      }))
      const end = new Date(`${request.startDate}T00:00:00`)
      end.setDate(end.getDate() + 6)
      const plan: WeeklyPlan = {
        id: crypto.randomUUID(),
        status: 'GENERATING',
        socialAccountId: request.socialAccountId,
        platform,
        accountName: channel?.name ?? 'Kênh đã chọn',
        startDate: request.startDate,
        endDate: localDate(end.toISOString()),
        goal: request.goal,
        timezone: request.timezone,
        strategy: `Tuần này tập trung vào ${request.goal.toLowerCase()}: mở bằng câu chuyện, giữa tuần chia sẻ giá trị, cuối tuần chốt đơn bằng ưu đãi.`,
        error: null,
        performance: sampleInsights(),
        items,
        createdAt: now,
        updatedAt: now,
      }
      plans = [plan, ...plans]
      simulateDrafting(plan.id, request)
      return plan
    }
  }

  const plan = requirePlan(resource)
  const [sub, itemId, action] = rest
  if (method === 'get' && !sub) return plan
  if (method === 'delete' && !sub) {
    if (plan.status === 'APPROVED') throw new MockError(409, 'Kế hoạch đã duyệt. Hãy hủy từng bài ở Lịch đăng.')
    plans = plans.filter((item) => item.id !== plan.id)
    return null
  }
  if (method === 'patch' && sub === 'items' && itemId && !action) {
    if (plan.status !== 'READY') throw new MockError(409, 'Kế hoạch này không còn chỉnh sửa được')
    const change = body as { scheduledTime?: string; skipped?: boolean }
    updatePlan(plan.id, (current) => ({
      ...current,
      items: current.items.map((item) => {
        if (item.id !== itemId) return item
        const scheduledTime = change.scheduledTime ?? item.scheduledTime
        const status = change.skipped === true ? 'SKIPPED' : change.skipped === false && item.status === 'SKIPPED' ? 'DRAFT' : item.status
        return { ...item, scheduledTime, plannedDate: scheduledTime ? localDate(scheduledTime) : item.plannedDate, status }
      }),
    }))
    return requirePlan(plan.id)
  }
  if (method === 'post' && sub === 'items' && !itemId) {
    if (plan.status !== 'READY') throw new MockError(409, 'Kế hoạch này không còn chỉnh sửa được')
    const day = (body as { plannedDate: string }).plannedDate
    if (plan.items.some((item) => item.plannedDate === day)) throw new MockError(409, 'Ngày này đã có bài trong kế hoạch')
    const scheduledTime = new Date(`${day}T09:00:00`).toISOString()
    const text = draftFor('Giới thiệu sản phẩm', 'sản phẩm', '')
    updatePlan(plan.id, (current) => ({
      ...current,
      items: [...current.items, {
        id: crypto.randomUUID(),
        plannedDate: day,
        scheduledTime,
        postType: 'Giới thiệu sản phẩm',
        topic: 'Giới thiệu sản phẩm',
        reason: 'Bạn thêm ngày này vào kế hoạch.',
        status: 'DRAFT' as const,
        error: null,
        scheduledPostId: null,
        post: {
          postId: `mock-${crypto.randomUUID()}`,
          title: 'Giới thiệu sản phẩm',
          platformContents: { [current.platform ?? 'FACEBOOK']: { title: 'Giới thiệu sản phẩm', [current.platform === 'INSTAGRAM' ? 'caption' : 'content']: text } },
          hashtags: [],
          cta: null,
          imageSuggestion: null,
          imageUrl: null,
        },
        media: [],
      }].sort((a, b) => a.plannedDate.localeCompare(b.plannedDate)),
    }))
    return requirePlan(plan.id)
  }
  if (method === 'post' && sub === 'approve') {
    if (plan.status !== 'READY') throw new MockError(409, 'Kế hoạch này không còn chỉnh sửa được')
    updatePlan(plan.id, (current) => ({
      ...current,
      status: 'APPROVED',
      items: current.items.map((item) => item.status === 'DRAFT'
        ? { ...item, status: 'SCHEDULED', scheduledPostId: `mock-${crypto.randomUUID()}` }
        : item),
    }))
    return requirePlan(plan.id)
  }
  throw new MockError(404, `Mock Daily chưa hỗ trợ ${method.toUpperCase()} ${path}`)
}

export function isDailyMockPath(url: string) {
  return url.startsWith('/weekly-plans') || url.startsWith('/posts/mock-')
}

export function installDailyMock(api: AxiosInstance) {
  const adapter: AxiosAdapter = async (config) => {
    const query = config.params ? `?${new URLSearchParams(config.params as Record<string, string>).toString()}` : ''
    const url = `${String(config.url ?? '')}${query}`
    const body = typeof config.data === 'string' && config.data ? JSON.parse(config.data) : config.data
    try {
      const data = await handleDailyMock(api, (config.method ?? 'get').toLowerCase(), url, body)
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
    // The demo serves these paths itself and asks for sign-up on approval; the dev mock must not answer first.
    if (isDailyMockPath(String(config.url ?? '')) && !isDemoActive()) config.adapter = adapter
    return config
  })
}
