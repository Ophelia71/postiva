import { CalendarDays, ChevronLeft, ChevronRight, Eye, Pencil, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { EditPostModal } from '../components/EditPostModal'
import { DeletePostConfirmModal } from '../components/DeletePostConfirmModal'
import { getPaginatedItems, PaginationControls } from '../components/PaginationControls'
import { PostDetailModal, PostSummary } from '../components/PostDetails'
import { api } from '../lib/api'
import { type AppPost, useAppDataResource } from '../lib/appDataApi'
import { addDays, toDateKey } from '../lib/dates'
import { getErrorMessage } from '../lib/errors'
import { sortPostsNewestFirst } from '../lib/posts'
import { usePreferences } from '../lib/preferences'
import { PageHeader, PageState, PlatformBadge, StatusBadge } from '../components/PageUi'

type CalendarDay = {
  date: Date
  inCurrentMonth: boolean
}

const schedulePageSize = 8
type DeletePostResponse = {
  deleted: boolean
  facebookDeleted: boolean
  facebookMessage: string
  message?: string
}

export function SchedulePage() {
  const { t } = usePreferences()
  const { data: posts, loading, error, reload } = useAppDataResource<AppPost[]>('/app-data/posts')
  const previousStatuses = useRef<Map<string, string> | null>(null)
  const [publishNotice, setPublishNotice] = useState<{ tone: 'success' | 'error' | 'info'; message: string } | null>(null)
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()))
  const [selectedDate, setSelectedDate] = useState(() => stripTime(new Date()))
  const [schedulePage, setSchedulePage] = useState(1)
  const [editingPost, setEditingPost] = useState<AppPost | null>(null)
  const [editingMessage, setEditingMessage] = useState('')
  const [editError, setEditError] = useState('')
  const [isSavingEdit, setIsSavingEdit] = useState(false)
  const [detailPost, setDetailPost] = useState<AppPost | null>(null)
  const [deleteCandidate, setDeleteCandidate] = useState<AppPost | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)

  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === 'visible') reload()
    }
    const timer = window.setInterval(refresh, 10000)
    document.addEventListener('visibilitychange', refresh)
    return () => {
      window.clearInterval(timer)
      document.removeEventListener('visibilitychange', refresh)
    }
  }, [reload])

  useEffect(() => {
    if (!posts) return
    const currentStatuses = new Map(posts
      .filter((post) => post.scheduleId)
      .map((post) => [post.scheduleId, post.status]))
    const previous = previousStatuses.current
    previousStatuses.current = currentStatuses
    if (!previous) return

    const terminalChanges = posts.filter((post) => {
      if (!post.scheduleId || previous.get(post.scheduleId) === post.status) return false
      return ['Published', 'Failed', 'Unknown', 'Canceled'].includes(post.status)
    })
    if (terminalChanges.length === 1) {
      const post = terminalChanges[0]
      const labels: Record<string, string> = {
        Published: 'đã đăng thành công',
        Failed: 'đăng thất bại, có thể xem lỗi và thử lại',
        Unknown: 'có kết quả chưa xác định, cần kiểm tra trên nền tảng',
        Canceled: 'đã bị hủy vì tài khoản đích ngắt kết nối',
      }
      setPublishNotice({
        tone: post.status === 'Published' ? 'success' : post.status === 'Failed' ? 'error' : 'info',
        message: `Bài “${post.content.slice(0, 70)}” ${labels[post.status] ?? 'đã cập nhật trạng thái'}.`,
      })
    } else if (terminalChanges.length > 1) {
      setPublishNotice({ tone: 'info', message: `${terminalChanges.length} bài đăng vừa cập nhật trạng thái. Mở từng bài để xem chi tiết.` })
    }
  }, [posts])

  const calendarDays = useMemo(() => buildCalendarDays(visibleMonth), [visibleMonth])

  useEffect(() => {
    setSchedulePage(1)
  }, [posts?.length])

  if (loading) {
    return <PageState title={t('Đang tải lịch đăng...')} />
  }

  if (error || !posts) {
    return <PageState title="Không thể tải lịch đăng" description={error ?? undefined} />
  }

  const scheduleRows = sortPostsNewestFirst(posts.filter(isScheduleVisible))
  const scheduledPosts = scheduleRows.filter(isEditableScheduledPost)
  const paginatedSchedule = getPaginatedItems(scheduleRows, schedulePage, schedulePageSize)
  const postsByDate = new Map<string, AppPost[]>()
  for (const post of scheduledPosts) {
    const dateKey = getDateKey(post.scheduledTime)
    if (!dateKey) {
      continue
    }
    postsByDate.set(dateKey, [...(postsByDate.get(dateKey) ?? []), post])
  }
  const selectedPosts = postsByDate.get(toDateKey(selectedDate)) ?? []

  return (
    <section className="space-y-6">
      <PageHeader title={t('Lịch đăng')} />

      {publishNotice && (
        <div
          role="status"
          aria-live="polite"
          className={`flex items-start justify-between gap-4 rounded-lg border px-4 py-3 text-sm ${
            publishNotice.tone === 'success'
              ? 'border-emerald-200 bg-emerald-50 text-emerald-900'
              : publishNotice.tone === 'error'
                ? 'border-rose-200 bg-rose-50 text-rose-900'
                : 'border-amber-200 bg-amber-50 text-amber-900'
          }`}
        >
          <span>{publishNotice.message}</span>
          <button type="button" onClick={() => setPublishNotice(null)} className="shrink-0 font-semibold underline">Đóng</button>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_330px]">
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <div className="mb-6 flex items-center justify-between">
            <button
              type="button"
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200"
              onClick={() => setVisibleMonth(addMonths(visibleMonth, -1))}
            >
              <ChevronLeft size={16} />
            </button>
            <h2 className="inline-flex items-center gap-2 font-bold"><CalendarDays size={17} />{formatMonth(visibleMonth)}</h2>
            <button
              type="button"
              className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200"
              onClick={() => setVisibleMonth(addMonths(visibleMonth, 1))}
            >
              <ChevronRight size={16} />
            </button>
          </div>

          <div className="grid grid-cols-7 gap-2">
            {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((label) => (
              <div key={label} className="text-center text-xs font-medium text-slate-400">
                {label}
              </div>
            ))}
            {calendarDays.map((day) => {
              const key = toDateKey(day.date)
              const selected = key === toDateKey(selectedDate)
              const hasPosts = postsByDate.has(key)

              return (
                <button
                  key={key}
                  type="button"
                  onClick={() => setSelectedDate(stripTime(day.date))}
                  className={[
                    'relative flex h-20 flex-col items-center justify-start rounded-xl pt-3 text-xs transition',
                    selected ? 'bg-blue-600 font-bold text-white' : 'hover:bg-[var(--soft-bg)]',
                    day.inCurrentMonth ? '' : 'text-slate-300',
                  ].join(' ')}
                >
                  {day.date.getDate()}
                  {hasPosts && (
                    <span className={`mt-2 h-1.5 w-1.5 rounded-full ${selected ? 'bg-white' : 'bg-blue-400'}`} />
                  )}
                </button>
              )
            })}
          </div>
        </div>

        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <h2 className="text-lg font-bold">{formatDate(selectedDate)}</h2>
          <p className="mt-1 text-sm text-[var(--muted-text)]">{selectedPosts.length} {t('bài chờ đăng')}</p>
          <div className="mt-5 space-y-3">
            {selectedPosts.length === 0 ? (
              <p className="rounded-xl border border-slate-100 bg-slate-50 p-4 text-sm text-slate-500">
                {t('Không có bài chờ đăng trong ngày này.')}
              </p>
            ) : (
              selectedPosts.map((post) => (
                <PostCard key={postRowKey(post)} post={post} />
              ))
            )}
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
        <h2 className="border-b border-[var(--app-border)] p-5 text-base font-bold">{t('Lịch đăng và bài cần xử lý')}</h2>
        <div className="divide-y divide-slate-100">
          {scheduleRows.length === 0 ? (
            <div className="px-5 py-6 text-sm text-[var(--muted-text)]">{t('Chưa có lịch đăng hoặc bài nào cần xử lý.')}</div>
          ) : (
            paginatedSchedule.pageItems.map((post) => (
              <div key={postRowKey(post)} className="grid grid-cols-[72px_minmax(0,1fr)] items-center gap-2 px-4 py-4 text-sm lg:grid-cols-[90px_minmax(0,1fr)_110px_150px_32px_32px_32px_auto] lg:gap-3 lg:px-5">
                <PlatformBadge platform={post.platform} />
                {post.permalinkUrl ? (
                  <div className="min-w-0">
                    <a href={post.permalinkUrl} target="_blank" rel="noreferrer" className="block truncate hover:text-violet-700">
                      {post.content}
                    </a>
                    {post.nextAttemptAt && <p className="mt-1 text-xs text-amber-700">Thử lại lúc {formatPostDateTime(post.nextAttemptAt)}</p>}
                    {post.status === 'Unknown' && <p className="mt-1 text-xs text-amber-800">Kiểm tra bài trên nền tảng trước khi chọn kết quả.</p>}
                    {post.attemptCount > 0 && <p className="mt-1 text-xs text-slate-500">Đã thử đăng {post.attemptCount} lần.</p>}
                    {post.lastError && <p className="mt-1 line-clamp-2 text-xs text-rose-700">Lỗi gần nhất: {post.lastError}</p>}
                  </div>
                ) : (
                  <div className="min-w-0">
                    <p className="truncate">{post.content}</p>
                    {post.nextAttemptAt && <p className="mt-1 text-xs text-amber-700">Thử lại lúc {formatPostDateTime(post.nextAttemptAt)}</p>}
                    {post.status === 'Unknown' && <p className="mt-1 text-xs text-amber-800">Kiểm tra bài trên nền tảng trước khi chọn kết quả.</p>}
                    {post.attemptCount > 0 && <p className="mt-1 text-xs text-slate-500">Đã thử đăng {post.attemptCount} lần.</p>}
                    {post.lastError && <p className="mt-1 line-clamp-2 text-xs text-rose-700">Lỗi gần nhất: {post.lastError}</p>}
                  </div>
                )}
                <StatusBadge status={post.status} />
                <span className="text-xs text-[var(--muted-text)]">{formatPostDateTime(post.time)}</span>
                <button
                  type="button"
                  onClick={() => setDetailPost(post)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-50 hover:text-slate-700"
                >
                  <Eye size={15} />
                </button>
                <button
                  type="button"
                  onClick={() => openEditPost(post)}
                  disabled={!isEditableScheduledPost(post)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-300 hover:bg-violet-50 hover:text-violet-700 disabled:cursor-not-allowed disabled:opacity-30 disabled:hover:bg-transparent disabled:hover:text-slate-300"
                  title={isEditableScheduledPost(post) ? 'Sửa bài đã đặt lịch' : 'Chỉ sửa được bài đang chờ đăng'}
                >
                  <Pencil size={15} />
                </button>
                <button
                  type="button"
                  onClick={() => setDeleteCandidate(post)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-300 hover:bg-rose-50 hover:text-rose-500"
                >
                  <Trash2 size={15} />
                </button>
                {post.status === 'Unknown' ? (
                  <div className="flex flex-wrap gap-1">
                    <button type="button" onClick={() => void resolveUnknown(post, true)} className="rounded border border-emerald-200 px-2 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50">Đã đăng</button>
                    <button type="button" onClick={() => void resolveUnknown(post, false)} className="rounded border border-amber-300 px-2 py-1 text-xs font-semibold text-amber-800 hover:bg-amber-50">Chưa đăng · thử lại</button>
                  </div>
                ) : post.status === 'Failed' ? (
                  <button type="button" onClick={() => void retryFailed(post)} className="rounded border border-blue-200 px-2 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50">Thử lại</button>
                ) : <span />}
              </div>
            ))
          )}
        </div>
        <PaginationControls
          page={paginatedSchedule.currentPage}
          pageSize={schedulePageSize}
          totalItems={scheduleRows.length}
          totalPages={paginatedSchedule.totalPages}
          onPageChange={setSchedulePage}
        />
      </div>

      {editingPost && (
        <EditPostModal
          title={t('Sửa bài đã đặt lịch')}
          message={editingMessage}
          error={editError}
          saving={isSavingEdit}
          saveLabel={t('Lưu thay đổi')}
          savingLabel={t('Đang lưu...')}
          onChange={setEditingMessage}
          onClose={closeEditPost}
          onSubmit={(event) => void submitEditPost(event)}
        />
      )}

      {detailPost && (
        <PostDetailModal
          post={detailPost}
          showMediaGallery
          onClose={() => setDetailPost(null)}
          onEdit={isEditableScheduledPost(detailPost) ? () => {
            openEditPost(detailPost)
            setDetailPost(null)
          } : undefined}
          onDelete={() => {
            setDeleteCandidate(detailPost)
          }}
        />
      )}

      {deleteCandidate && (
        <DeletePostConfirmModal
          post={deleteCandidate}
          deleting={isDeleting}
          onCancel={() => setDeleteCandidate(null)}
          onConfirm={() => void deletePost(deleteCandidate)}
        />
      )}
    </section>
  )

  function openEditPost(post: AppPost) {
    setEditingPost(post)
    setEditingMessage(post.content)
    setEditError('')
  }

  function closeEditPost() {
    setEditingPost(null)
    setEditingMessage('')
    setEditError('')
  }

  async function submitEditPost(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!editingPost || !editingMessage.trim()) {
      setEditError(t('Nội dung bài đăng là bắt buộc.'))
      return
    }

    setIsSavingEdit(true)
    setEditError('')

    try {
      await api.put(`/app-data/posts/${editingPost.id}`, {
        message: editingMessage.trim(),
        platform: editingPost.platform.toUpperCase(),
        scheduleId: editingPost.scheduleId || undefined,
      })
      closeEditPost()
      reload()
    } catch (error) {
      setEditError(getErrorMessage(error, 'Không thể cập nhật bài đã đặt lịch.'))
    } finally {
      setIsSavingEdit(false)
    }
  }

  async function deletePost(post: AppPost) {
    setIsDeleting(true)
    try {
      const response = await api.delete<DeletePostResponse>(`/app-data/posts/${post.id}`)
      if (!response.data.deleted) {
        window.alert(response.data.message || response.data.facebookMessage || 'Không thể xóa bài viết.')
        return
      }
      setDeleteCandidate(null)
      setDetailPost((current) => current?.id === post.id ? null : current)
      reload()
    } catch (error) {
      window.alert(getErrorMessage(error, 'Không thể xóa bài viết.'))
    } finally {
      setIsDeleting(false)
    }
  }

  async function resolveUnknown(post: AppPost, published: boolean) {
    if (!post.scheduleId) return
    const confirmed = window.confirm(published
      ? 'Hãy xác nhận bạn đã mở nền tảng và tìm thấy bài đăng. Đánh dấu lịch là đã đăng?'
      : 'Chỉ tiếp tục nếu bạn đã kiểm tra Page/tài khoản đích và chắc chắn bài chưa xuất hiện. Postiva sẽ thử đăng lại; nếu kết quả trước còn chưa rõ, thao tác này có thể tạo bài trùng.')
    if (!confirmed) return
    try {
      await api.post(`/publishing/${post.scheduleId}/resolve`, {
        result: published ? 'CONFIRMED_PUBLISHED' : 'CONFIRMED_NOT_PUBLISHED',
      })
      if (!published) {
        await api.post(`/publishing/${post.scheduleId}/retry`)
        setPublishNotice({ tone: 'info', message: 'Đã xác nhận bài chưa được đăng và đưa yêu cầu thử lại vào hàng đợi.' })
      } else {
        setPublishNotice({ tone: 'success', message: 'Đã ghi nhận bài đăng thành công theo xác nhận của bạn.' })
      }
      reload()
    } catch (error) {
      setPublishNotice({ tone: 'error', message: getErrorMessage(error, 'Không thể cập nhật kết quả đăng.') })
    }
  }

  async function retryFailed(post: AppPost) {
    if (!post.scheduleId || !window.confirm('Thử đăng lại bài này ngay? Hãy kiểm tra trước rằng bài chưa được đăng trên nền tảng.')) return
    try {
      await api.post(`/publishing/${post.scheduleId}/retry`)
      setPublishNotice({ tone: 'info', message: 'Đã nhận yêu cầu thử lại. Trạng thái sẽ tự cập nhật khi worker xử lý xong.' })
      reload()
    } catch (error) {
      setPublishNotice({ tone: 'error', message: getErrorMessage(error, 'Không thể lên lịch thử lại bài đăng.') })
    }
  }
}

function PostCard({ post }: { post: AppPost }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
      <div className="mb-2 flex items-center gap-2">
        <PlatformBadge platform={post.platform} />
        <StatusBadge status={post.status} />
        <span className="ml-auto text-xs text-slate-400">{formatPostDateTime(post.time)}</span>
      </div>
      <PostSummary post={post} showMediaBadge={false} />
    </div>
  )
}

function postRowKey(post: AppPost) {
  return post.scheduleId || `${post.id}:${post.platform}`
}

function buildCalendarDays(month: Date) {
  const firstDay = startOfMonth(month)
  const mondayOffset = (firstDay.getDay() + 6) % 7
  const startDate = addDays(firstDay, -mondayOffset)
  const days: CalendarDay[] = []

  for (let index = 0; index < 42; index += 1) {
    const date = addDays(startDate, index)
    days.push({
      date,
      inCurrentMonth: date.getMonth() === month.getMonth(),
    })
  }

  return days
}

function startOfMonth(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), 1)
}

function stripTime(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function addMonths(date: Date, months: number) {
  return new Date(date.getFullYear(), date.getMonth() + months, 1)
}

function getDateKey(value: string | null) {
  if (!value) {
    return null
  }

  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return null
  }

  return toDateKey(date)
}

function isEditableScheduledPost(post: AppPost) {
  return post.status === 'Scheduled'
}

function isScheduleVisible(post: AppPost) {
  return ['Scheduled', 'Publishing', 'Failed', 'Unknown'].includes(post.status)
    || (post.status === 'Canceled' && post.lastError.includes('Tài khoản đã ngắt kết nối'))
}

function formatMonth(date: Date) {
  return new Intl.DateTimeFormat('vi-VN', { month: 'long', year: 'numeric' }).format(date)
}

function formatDate(date: Date) {
  return new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
  }).format(date)
}

function formatPostDateTime(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return value
  }

  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    timeZoneName: 'short',
  }).format(date)
}
