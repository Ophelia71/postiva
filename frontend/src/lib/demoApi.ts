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

// One believable shop runs through the whole demo: its channels, posts, profile and AI drafts.
const SHOP = 'Mây Coffee'

const CHANNELS: AppChannel[] = [
  { id: 'demo-facebook', accountId: 'demo-page', name: SHOP, type: 'Facebook Page', badge: 'Facebook', color: '#1877F2', active: true },
  { id: 'demo-instagram', accountId: 'demo-ig', name: '@maycoffee.saigon', type: 'Instagram Business', badge: 'Instagram', color: '#E1306C', active: true },
]

const SAMPLE_POSTS: Array<{ days: number; hour: number; platform: string; status: string; content: string }> = [
  { days: -9, hour: 20, platform: 'Facebook', status: 'Published', content: 'Ly cà phê muối đầu tiên của ngày ở Mây: lớp kem muối béo mặn phủ trên cà phê phin đậm vị. Ghé quán hoặc nhắn tin, Mây giao trong 30 phút nhé! #MayCoffee #CaPheMuoi' },
  { days: -7, hour: 9, platform: 'Facebook', status: 'Published', content: 'Mây mở cửa từ 7h sáng nay. Mẻ bánh quy bơ đầu tiên vừa ra lò, ăn kèm bạc xỉu là hết ý. Hẹn bạn ở góc quen nha!' },
  { days: -5, hour: 20, platform: 'Instagram', status: 'Published', content: 'Hậu trường: 5 bước để có một ly cà phê muối chuẩn vị Mây. Bạn đoán bước nào khó nhất? #MayCoffee #CaPheMuoi' },
  { days: -3, hour: 19, platform: 'Facebook', status: 'Published', content: '"Uống một lần là ghiền" — cảm ơn chị Mai đã ghé và để lại lời khen cho Mây. Từ nay đến hết Chủ nhật: mua 2 ly tặng 1 bánh quy bơ!' },
  { days: -2, hour: 12, platform: 'Facebook', status: 'Failed', content: 'Combo trưa văn phòng: cà phê muối + bánh mì chảo chỉ 49.000đ, giao tận nơi quanh Quận 3. Nhắn Mây trước 11h để kịp giờ nghỉ trưa nhé!' },
  { days: 1, hour: 20, platform: 'Facebook', status: 'Scheduled', content: 'Tối nay Mây mở đến 22h. Ghé ngồi chill với ly cà phê muối và nghe nhạc acoustic cùng quán nhé!' },
  { days: 3, hour: 19, platform: 'Instagram', status: 'Scheduled', content: 'Cuối tuần này ở Mây: mua 2 ly tặng 1 bánh quy bơ. Lưu bài lại để không quên nha! #MayCoffee #CaPheMuoi' },
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
  businessName: SHOP,
  industryCode: 'cafe',
  industryName: 'Café / Đồ uống',
  mainProducts: 'Cà phê muối, bạc xỉu, bánh quy bơ nướng tại quán',
  targetCustomer: 'Dân văn phòng và sinh viên quanh Quận 3',
  defaultTone: 'Thân thiện, gần gũi',
  address: 'Quận 3, TP.HCM',
  highlights: 'Kem muối đánh mới mỗi sáng, giao trong 30 phút',
  priceRange: '29.000đ – 49.000đ',
  openingHours: '7h – 22h hằng ngày',
  signature: 'Mây Coffee · Quận 3, TP.HCM · Mở cửa 7h – 22h',
  brandHashtags: '#MayCoffee #CaPheMuoi',
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
  const product = body.productName?.trim() || 'Cà phê muối'
  const highlights = (body.highlights?.trim() || 'kem muối béo mặn đánh mới mỗi sáng, cà phê phin đậm vị').replace(/[.!]+$/, '')
  const lower = product.charAt(0).toLowerCase() + product.slice(1)
  const contents: Record<string, Record<string, string>> = {}
  for (const platform of body.platforms ?? ['FACEBOOK']) {
    const text = platform === 'THREADS'
      ? `Có ai mê ${lower} như Mây không? Bí quyết của tụi mình: ${highlights}. Kể Mây nghe bạn thích uống kiểu nào nhé.`
      : platform === 'INSTAGRAM'
        ? `${product} ở Mây: ${highlights}. Một ly cho buổi chiều thêm dịu. Lưu bài lại để lần sau ghé quán nha!`
        : `${product} của Mây có gì đặc biệt? ${highlights.charAt(0).toUpperCase() + highlights.slice(1)}. Ghé Mây ở Quận 3 hoặc nhắn tin để Mây giao tận nơi nhé!`
    contents[platform] = { title: product, [platform === 'INSTAGRAM' ? 'caption' : 'content']: text }
  }
  return {
    postId: `demo-draft-${crypto.randomUUID()}`,
    briefId: 'demo-brief',
    title: product,
    platformContents: contents,
    hashtags: ['#MayCoffee', '#CaPheMuoi'],
    cta: 'Nhắn Mây để đặt ly đầu tiên',
    imageSuggestion: `Ảnh ${lower} chụp gần trên bàn gỗ, ánh sáng tự nhiên buổi sáng`,
    imageUrl: null,
    status: 'DRAFT',
  }
}

/** The real plans, shown but not sold: buying asks the visitor to sign up. */
function demoOffer() {
  const features = [
    { icon: 'channels', text: 'Đăng lên Facebook, Instagram, Threads' },
    { icon: 'daily', text: 'Daily: kế hoạch đăng cả tuần' },
    { icon: 'schedule', text: 'Tự đăng đúng giờ đã hẹn' },
    { icon: 'reports', text: 'Báo cáo lượt thích, bình luận, lượt xem' },
  ]
  return {
    // Shown as on sale: pressing "Nâng lên Pro" invites the visitor to create an account.
    available: true, amountVnd: 149000, periodDays: 30, refundPolicyUrl: null, method: 'BANK_TRANSFER', bank: null, autoConfirm: true,
    plans: [
      { code: 'FREE', name: 'Free', headline: 'Dùng thử miễn phí', description: 'Đủ để thử tạo và đăng bài cho shop, không cần thanh toán.', priceVnd: 0, aiLimit: 30, periodDays: 0, features, purchasable: false },
      { code: 'PRO', name: 'Pro', headline: 'Đăng bài đều mỗi ngày', description: 'Lượt AI mới mỗi tháng cho shop bán hàng thường xuyên.', priceVnd: 149000, aiLimit: 300, periodDays: 30, features, purchasable: true },
    ],
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
    if (path === '/billing/offer') return demoOffer()
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
