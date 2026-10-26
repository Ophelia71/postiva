export type DailyStatus = {
  enabled: boolean
  autoEnabled: boolean
  maxRulesPerUser: number
  manualQuotaReserve: number
}

export type DailyBrief = {
  productName: string
  productDescription: string
  price?: string | null
  targetAudience: string
  highlights: string
  goal?: string | null
  tone?: string | null
}

export type DailyRule = {
  id: string
  socialAccountId: string | null
  accountName: string | null
  platform: string
  name: string
  status: 'ACTIVE' | 'PAUSED'
  pausedReason: string | null
  timezone: string
  timeOfDay: string
  /** ISO weekdays: 1 = Monday ... 7 = Sunday. */
  daysOfWeek: number[]
  approvalMode: 'REVIEW' | 'AUTO'
  leadMinutes: number
  brief: DailyBrief
  createdAt: string
  updatedAt: string
}

export type DailyDraft = {
  title: string | null
  content: string
  hashtags: string[]
  cta: string | null
}

export type DailyRun = {
  id: string
  ruleId: string
  ruleName: string
  runDate: string
  plannedFor: string
  status: 'GENERATING' | 'PENDING_REVIEW' | 'SCHEDULED' | 'SKIPPED' | 'FAILED'
  postId: string | null
  scheduledPostId: string | null
  error: string | null
  draft: DailyDraft | null
}

export type DailyPreviewItem = { runDate: string; at: string }

export type DailyRuleRequest = {
  name: string
  socialAccountId: string
  timezone: string
  timeOfDay: string
  daysOfWeek: number[]
  approvalMode: 'REVIEW' | 'AUTO'
  brief: DailyBrief
}

export const WEEKDAY_LABELS: Record<number, string> = { 1: 'T2', 2: 'T3', 3: 'T4', 4: 'T5', 5: 'T6', 6: 'T7', 7: 'CN' }

export const PAUSE_REASON_LABELS: Record<string, string> = {
  USER: 'Bạn đã tạm dừng rule này.',
  TOKEN: 'Token của kênh hết hạn hoặc thiếu quyền. Kết nối lại kênh rồi bật lại rule.',
  CHANNEL_DISCONNECTED: 'Kênh đã bị ngắt. Kết nối lại kênh rồi tạo rule mới hoặc bật lại rule.',
  UNKNOWN_RESULT: 'Bài gần nhất có kết quả chưa xác định. Đối soát ở Lịch đăng trước khi bật lại.',
  USER_LOCKED: 'Tài khoản đang bị khóa.',
  AI_QUOTA_EXHAUSTED: 'Còn quá ít lượt AI, Daily giữ lại phần còn lại cho bạn dùng tay.',
}

export const RUN_ERROR_LABELS: Record<string, string> = {
  EXPIRED: 'Quá giờ mà chưa duyệt',
  USER_SKIPPED: 'Bạn đã bỏ qua',
  PAUSED: 'Rule đã tạm dừng',
  MISSED: 'Đã lỡ giờ đăng',
  AI_BUSY: 'AI đang bận, thử lại sau',
  AI_UNAVAILABLE: 'AI tạm thời không dùng được',
  AI_UNCERTAIN: 'Lần soạn trước chưa rõ kết quả, bỏ qua ngày này',
  GENERATION_FAILED: 'Không soạn được bài',
  GENERATION_EXHAUSTED: 'Thử soạn nhiều lần không được',
}
