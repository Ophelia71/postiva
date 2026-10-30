import type { AxiosAdapter, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import type { AppChannel, AppPost } from './appDataApi'
import { handleDailyMock, isDailyMockPath, MockError } from './dailyMock'
import { isDemoActive, requestDemoSignup } from './demo'
import { getAuthSession } from './auth'

/**
 * Serves every API call of the demo from sample data, so a visitor without an account can walk through the app.
 * Reads and harmless steps (AI drafts from canned text, weekly plans) work; anything that would publish, connect
 * an account, upload or pay asks the visitor to sign up instead. Nothing here reaches the backend.
 */

const CHANNELS: AppChannel[] = [
  { id: 'demo-facebook', accountId: 'demo-page', name: 'Tiệm Cà Phê Muối (mẫu)', type: 'Facebook Page', badge: 'Facebook', color: '#1877F2', active: true },
  { id: 'demo-instagram', accountId: 'demo-ig', name: '@caphemuoi.mau', type: 'Instagram Business', badge: 'Instagram', color: '#E1306C', active: true },
]

const SAMPLE_POSTS: Array<{ days: number; hour: number; platform: string; status: string; content: string }> = [
  { days: -9, hour: 20, platform: 'Facebook', status: 'Published', content: 'Ly cà phê muối đầu tiên của ngày. Lớp kem mặn béo hòa cùng cà phê phin đậm vị. Ghé tiệm hoặc nhắn tin để được giao trong 30 phút nhé!' },
  { days: -7, hour: 9, platform: 'Facebook', status: 'Published', content: 'Tiệm mở cửa từ 7h sáng. Hôm nay có thêm bánh quy bơ mới ra lò' },
  { days: -5, hour: 20, platform: 'Instagram', status: 'Published', content: 'Behind the scenes: 5 bước pha một ly cà phê muối chuẩn vị. Bạn đoán bước nào khó nhất? #caphemuoi #hauruong' },
  { days: -3, hour: 19, platform: 'Facebook', status: 'Published', content: '“Uống một lần là ghiền” — cảm ơn chị Mai đã review cho tiệm! Mua 2 ly tặng 1 bánh quy đến hết Chủ nhật!' },
  { days: -2, hour: 12, platform: 'Facebook', status: 'Failed', content: 'Combo trưa văn phòng: cà phê muối + bánh mì chỉ 49.000đ. (Bài này lỗi vì kết nối kênh đã hết hạn, bấm Thử lại sau khi kết nối lại kênh.)' },
  { days: 1, hour: 20, platform: 'Facebook', status: 'Scheduled', content: 'Tối nay tiệm mở đến 22h. Ghé chill với ly cà phê muối nhé' },
  { days: 3, hour: 19, platform: 'Instagram', status: 'Scheduled', content: 'Cuối tuần này: mua 2 tặng 1 bánh quy. Lưu bài để không quên nha! #caphemuoi' },
]

function at(days: number, hour: number) {
  const date = new Date()
  date.setDate(date.getDate() + days)
  date.setHours(hour, 0, 0, 0)
  return date.toISOString()
}

function posts(): AppPost[] {
  return SAMPLE_POSTS.map((sample, index) => {
    const time = at(sample.days, sample.hour)
    return {
      id: `demo-post-${index}`,
      scheduleId: `demo-schedule-${index}`,
      platform: sample.platform,
      content: sample.content,
      status: sample.status,
      time,
      scheduledTime: sample.status === 'Scheduled' ? time : null,
      attemptCount: sample.status === 'Failed' ? 3 : sample.status === 'Published' ? 1 : 0,
      nextAttemptAt: null,
      lastError: sample.status === 'Failed' ? 'Kết nối Facebook đã hết hạn. Vào Kết nối kênh để kết nối lại.' : '',
      permalinkUrl: '',
      platformPostId: '',
      mediaType: '',
      mediaUrl: '',
      mediaThumbnailUrl: '',
      mediaTitle: '',
      mediaUrls: [],
      mediaIds: [],
      metrics: sample.status === 'Published'
        ? { likes: 18 + index * 9, comments: 2 + index, shares: index % 3, fetchedAt: new Date().toISOString() }
        : {},
    }
  })
}

function overview() {
  const all = posts()
  return {
    totalPosts: all.length,
    scheduledPosts: all.filter((post) => post.status === 'Scheduled').length,
    publishedPosts: all.filter((post) => post.status === 'Published').length,
    failedPosts: all.filter((post) => post.status === 'Failed').length,
    channels: CHANNELS,
    recentPosts: all,
  }
}

let shopProfile: Record<string, unknown> = {
  businessName: 'Tiệm Cà Phê Muối (mẫu)',
  industryCode: 'cafe',
  mainProducts: 'Cà phê muối pha phin, bánh quy bơ làm tại tiệm',
  targetCustomer: 'Dân văn phòng 22–35 tuổi',
  defaultTone: 'Thân thiện, gần gũi',
  phone: '0900 000 000',
  address: 'Quận 3, TP.HCM',
  signature: 'Địa chỉ: Quận 3, TP.HCM | Hotline: 0900 000 000',
  brandHashtags: '#CaPheMuoiMau',
  complete: true,
}

const INDUSTRIES = [
  { industryCode: 'cafe', industryName: 'Café / Đồ uống' },
  { industryCode: 'fashion', industryName: 'Thời trang' },
  { industryCode: 'beauty', industryName: 'Mỹ phẩm / Làm đẹp' },
  { industryCode: 'education', industryName: 'Khóa học / Giáo dục' },
]

function usage() {
  return { plan: 'FREE', limit: 30, used: 4, reserved: 0, remaining: 26, endsAt: at(26, 0) }
}

/** A canned draft per requested platform, shaped like POST /posts/generate. */
function generatedDraft(body: { productName?: string; highlights?: string; platforms?: string[] }) {
  const product = body.productName?.trim() || 'sản phẩm của bạn'
  const highlights = body.highlights?.trim() || 'chất lượng và giao nhanh'
  const contents: Record<string, Record<string, string>> = {}
  for (const platform of body.platforms ?? ['FACEBOOK']) {
    const text = platform === 'THREADS'
      ? `Ai đã thử ${product} chưa? ${highlights}. Kể shop nghe cảm nhận của bạn nhé`
      : platform === 'INSTAGRAM'
        ? `${product}: ${highlights}. Lưu bài này để lần sau ghé shop nha!`
        : `Giới thiệu ${product}: ${highlights}. Nhắn tin cho shop để được tư vấn và đặt hàng ngay hôm nay!`
    contents[platform] = { title: product, [platform === 'INSTAGRAM' ? 'caption' : 'content']: text }
  }
  return {
    postId: `demo-draft-${crypto.randomUUID()}`,
    briefId: 'demo-brief',
    title: product,
    platformContents: contents,
    hashtags: ['#postiva', '#shopnho'],
    cta: 'Nhắn tin cho shop ngay',
    imageSuggestion: `Ảnh ${product} chụp gần, ánh sáng tự nhiên`,
    imageUrl: null,
    status: 'DRAFT',
  }
}

/** Steps that need a real account, and how the sign-up prompt describes them. */
const SIGNUP_REQUIRED: Array<{ method: string; pattern: RegExp; action: string }> = [
  { method: 'post', pattern: /^\/(social-posts|facebook-business\/posts)$/, action: 'đăng hoặc lên lịch bài thật' },
  { method: 'post', pattern: /^\/weekly-plans\/[^/]+\/approve$/, action: 'duyệt và lên lịch cả tuần' },
  { method: 'post', pattern: /^\/publishing\//, action: 'đăng lại bài' },
  { method: 'any', pattern: /^\/social\//, action: 'kết nối Facebook, Instagram hoặc Threads' },
  { method: 'post', pattern: /^\/files\/upload$/, action: 'tải ảnh và video lên' },
  { method: 'post', pattern: /^\/billing\//, action: 'mua gói Pro' },
  { method: 'any', pattern: /^\/app-data\/posts\//, action: 'sửa hoặc xóa bài đã đăng' },
  { method: 'post', pattern: /^\/auth\/(password\/change|sessions\/revoke)$/, action: 'quản lý bảo mật tài khoản' },
]

async function handle(api: AxiosInstance, method: string, url: string, body: unknown): Promise<unknown> {
  const [path] = url.split('?')
  const gate = SIGNUP_REQUIRED.find((rule) => (rule.method === 'any' || rule.method === method) && rule.pattern.test(path))
  if (gate) {
    requestDemoSignup(gate.action)
    throw new MockError(403, `Đây là bản dùng thử. Tạo tài khoản miễn phí để ${gate.action}.`)
  }
  if (isDailyMockPath(path)) return handleDailyMock(api, method, url, body)

  if (method === 'get') {
    if (path === '/app-data/overview') return overview()
    if (path === '/app-data/channels') return CHANNELS
    if (path === '/app-data/posts') return posts()
    if (path === '/usage') return usage()
    if (path === '/usage/requests') return { items: [], page: 0, size: 10, total: 0 }
    if (path === '/billing/offer') return { available: false, amountVnd: 0, aiLimit: 50, periodDays: 30, refundPolicyUrl: null }
    if (path === '/billing/orders') return []
    if (path === '/shop-profile') return shopProfile
    if (path === '/templates') return INDUSTRIES
  }
  // Editing the sample profile is harmless and shows how the form works; it only lives in this tab.
  if (method === 'put' && path === '/shop-profile') {
    shopProfile = { ...(body as Record<string, unknown>), complete: true }
    return shopProfile
  }
  if (method === 'post' && path === '/posts/generate') {
    // A short pause so the demo feels like the real AI step.
    await new Promise((resolve) => window.setTimeout(resolve, 900))
    return generatedDraft(body as Parameters<typeof generatedDraft>[0])
  }
  if (method === 'put' && path.startsWith('/posts/')) return null

  requestDemoSignup('dùng đầy đủ tính năng này')
  throw new MockError(403, 'Đây là bản dùng thử. Tạo tài khoản miễn phí để dùng đầy đủ tính năng này.')
}

const adapter: AxiosAdapter = async (config) => {
  const query = config.params ? `?${new URLSearchParams(config.params as Record<string, string>).toString()}` : ''
  const url = `${String(config.url ?? '')}${query}`
  const body = typeof config.data === 'string' && config.data ? JSON.parse(config.data) : config.data
  try {
    const data = await handle(api, (config.method ?? 'get').toLowerCase(), url, body)
    return { data: { success: true, message: null, data }, status: 200, statusText: 'OK', headers: {}, config }
  } catch (error) {
    const status = error instanceof MockError ? error.status : 500
    const message = error instanceof Error ? error.message : 'Bản dùng thử gặp lỗi'
    const response = { data: { success: false, message, data: null }, status, statusText: 'Error', headers: {}, config }
    return Promise.reject(Object.assign(new Error(message), { isAxiosError: true, response, config }))
  }
}

let api: AxiosInstance

export function installDemoApi(instance: AxiosInstance) {
  api = instance
  instance.interceptors.request.use((config: InternalAxiosRequestConfig) => {
    // A real session always wins: signing in from another tab must never be served sample data.
    if (isDemoActive() && !getAuthSession()) config.adapter = adapter
    return config
  })
}
