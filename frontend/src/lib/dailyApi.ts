/** Set VITE_DAILY_API=true once the /weekly-plans backend is deployed. */
export const DAILY_API_READY = import.meta.env.VITE_DAILY_API === 'true'
/** Without the backend, the Vite dev server serves Daily from an in-memory mock; production builds show it as off. */
export const DAILY_USE_MOCK = import.meta.env.DEV && !DAILY_API_READY

export type PlanStatus = 'GENERATING' | 'READY' | 'APPROVED' | 'FAILED'
export type PlanItemStatus = 'PLANNED' | 'GENERATING' | 'DRAFT' | 'SKIPPED' | 'SCHEDULED' | 'PUBLISHED' | 'FAILED'

export type PostHighlight = {
  excerpt: string
  publishedAt: string | null
  mediaType: string
  engagement: number
  likes: number | null
  comments: number | null
  shares: number | null
  views: number | null
}

/** What the shop's past posts say; mirrors the backend PerformanceSummary. */
export type PerformanceSummary = {
  postsAnalyzed: number
  postsWithMetrics: number
  warning: string | null
  averageEngagement: number
  topPosts: PostHighlight[]
  weakPosts: PostHighlight[]
  bestWeekdays: number[]
  bestHour: number | null
  engagementByMediaType: Record<string, number>
  topPostAverageLength: number | null
  otherPostAverageLength: number | null
  topHashtags: string[]
  postsPerWeek: number
  insights: string[]
}

export type PlanPost = {
  postId: string
  title: string | null
  platformContents: Record<string, { title?: string; content?: string; caption?: string }>
  hashtags: string[]
  cta: string | null
  imageSuggestion: string | null
  imageUrl: string | null
}

export type PlanMedia = { id: string; url: string; mimeType: string | null; fileName: string | null }

export type WeeklyPlanItem = {
  id: string
  plannedDate: string
  scheduledTime: string | null
  postType: string | null
  topic: string | null
  reason: string | null
  status: PlanItemStatus
  error: string | null
  scheduledPostId: string | null
  post: PlanPost | null
  /** Every image or video attached to the draft. */
  media: PlanMedia[]
}

export type WeeklyPlan = {
  id: string
  status: PlanStatus
  socialAccountId: string
  platform: string | null
  accountName: string | null
  startDate: string
  endDate: string
  goal: string
  timezone: string
  strategy: string | null
  error: string | null
  performance: Partial<PerformanceSummary> | null
  items: WeeklyPlanItem[]
  createdAt: string
  updatedAt: string
}

export type CreateWeeklyPlanRequest = {
  socialAccountId: string
  startDate: string
  postsPerWeek: number
  daysOfWeek?: number[]
  postTime?: string
  timezone: string
  productName: string
  productDescription: string
  price?: string
  targetAudience: string
  highlights: string
  goal: string
  tone?: string
  imageIds: string[]
  /** EACH gives each post one image in turn; ALL (the default) puts every image on every post. */
  imageMode?: 'ALL' | 'EACH'
}

/** One AI unit for the week's ideas plus one per draft; keep in sync with WeeklyPlanService.aiUnitsFor. */
export const aiUnitsFor = (postsPerWeek: number) => postsPerWeek + 1

export const WEEKDAY_LABELS: Record<number, string> = { 1: 'T2', 2: 'T3', 3: 'T4', 4: 'T5', 5: 'T6', 6: 'T7', 7: 'CN' }
export const WEEKDAY_NAMES: Record<number, string> = {
  1: 'Thứ Hai', 2: 'Thứ Ba', 3: 'Thứ Tư', 4: 'Thứ Năm', 5: 'Thứ Sáu', 6: 'Thứ Bảy', 7: 'Chủ nhật',
}

export const ITEM_STATUS_LABELS: Record<PlanItemStatus, string> = {
  PLANNED: 'Chờ soạn',
  GENERATING: 'Đang soạn',
  DRAFT: 'Bản nháp',
  SKIPPED: 'Bỏ qua',
  SCHEDULED: 'Đã lên lịch',
  PUBLISHED: 'Đã đăng',
  FAILED: 'Lỗi',
}

/** The draft text for the plan's platform; Instagram stores it as caption. */
export function draftText(post: PlanPost | null, platform: string | null) {
  if (!post || !platform) return ''
  const content = post.platformContents[platform]
  return (platform === 'INSTAGRAM' ? content?.caption : content?.content) ?? ''
}
