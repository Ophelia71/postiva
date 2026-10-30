import { CalendarDays, ChevronLeft, ChevronRight, Eye, Pencil, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { EditPostModal } from '../components/EditPostModal'
import { EmptyState } from '../components/EmptyState'
import { SetupReminder } from '../components/SetupReminder'
import { DeletePostConfirmModal } from '../components/DeletePostConfirmModal'
import { getPaginatedItems, PaginationControls } from '../components/PaginationControls'
import { PostDetailModal, PostSummary } from '../components/PostDetails'
import { api } from '../lib/api'
import { clearDraft } from '../lib/idleSession'
import { type AppPost, useAppDataResource } from '../lib/appDataApi'
import { addDays, toDateKey } from '../lib/dates'
import { getErrorMessage } from '../lib/errors'
import { sortPostsNewestFirst } from '../lib/posts'
import { usePreferences } from '../lib/preferences'
import { PageHeader, PageState, PlatformBadge, StatusBadge } from '../components/PageUi'

type PendingScheduleAction = { kind: 'resolvePublished' | 'resolveNotPublished' | 'retry'; post: AppPost }

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
  const [pendingAction, setPendingAction] = useState<PendingScheduleAction | null>(null)
  const [deleteError, setDeleteError] = useState('')

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
      <SetupReminder />

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
          <button type="button" onClick={() => setPublishNotice(null)} className="min-h-11 shrink-0 font-semibold underline">Đóng</button>
        </div>
      )}

      {pendingAction && (
        <div role="alertdialog" aria-label="Xác nhận" className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 sm:flex-row sm:items-center sm:justify-between">
          <p>{confirmMessage(pendingAction)}</p>
          <div className="flex shrink-0 gap-2">
            <button type="button" onClick={() => setPendingAction(null)} className="min-h-11 rounded-lg border border-amber-300 px-4 font-semibold hover:bg-amber-100">Hủy</button>
            <button type="button" onClick={() => void runPendingAction()} className="min-h-11 rounded-lg bg-amber-700 px-4 font-bold text-white hover:bg-amber-800">Xác nhận</button>
          </div>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-[1fr_330px]">
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <div className="mb-6 flex items-center justify-between">
            <button
              type="button"
              aria-label="Tháng trước"
              className="flex h-11 w-11 items-center justify-center rounded-lg border border-slate-200"
              onClick={() => setVisibleMonth(addMonths(visibleMonth, -1))}
            >
              <ChevronLeft size={16} aria-hidden="true" />
            </button>
            <h2 className="inline-flex items-center gap-2 font-bold"><CalendarDays size={17} />{formatMonth(visibleMonth)}</h2>
            <button
              type="button"
              aria-label="Tháng sau"
              className="flex h-11 w-11 items-center justify-center rounded-lg border border-slate-200"
              onClick={() => setVisibleMonth(addMonths(visibleMonth, 1))}
            >
              <ChevronRight size={16} aria-hidden="true" />
            </button>
          </div>

          <div className="grid grid-cols-7 gap-2">
            {['T2', 'T3', 'T4', 'T5', 'T6', 'T7', 'CN'].map((label) => (
              <div key={label} className="text-center text-xs font-medium text-slate-600">
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
                  aria-label={`${formatDate(day.date)}${hasPosts ? ', có bài chờ đăng' : ''}`}
                  aria-pressed={selected}
                  className={[
                    'relative flex h-14 flex-col items-center justify-start rounded-xl pt-3 text-xs transition sm:h-20',
                    selected ? 'bg-blue-600 font-bold text-white' : 'hover:bg-[var(--soft-bg)]',
                    day.inCurrentMonth ? '' : 'text-slate-500',
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
            <EmptyState
              icon={<CalendarDays size={20} />}
              title="Chưa có bài nào trong lịch"
              description="Bài đã lên lịch, bài đang đăng và bài cần xử lý sẽ hiện ở đây. Tạo bài và chọn giờ đăng để bắt đầu."
              actionLabel="Tạo bài và lên lịch"
              actionTo="/app/create"
            />
          ) : (
            paginatedSchedule.pageItems.map((post) => (
              // Phones get a card (labels, text, then actions); from lg the groups dissolve into one table row.
              <div key={postRowKey(post)} className="flex flex-col gap-2 px-4 py-4 text-sm lg:grid lg:grid-cols-[90px_minmax(0,1fr)_110px_150px_32px_32px_32px_auto] lg:items-center lg:gap-3 lg:px-5">
                <div className="flex flex-wrap items-center gap-2 lg:contents">
                  <span className="lg:order-1"><PlatformBadge platform={post.platform} /></span>
                  <span className="whitespace-nowrap lg:order-3"><StatusBadge status={post.status} /></span>
                  <span className="text-xs text-[var(--muted-text)] lg:order-4">{formatPostDateTime(post.time)}</span>
                </div>
                {post.permalinkUrl ? (
                  <div className="min-w-0 lg:order-2">
                    <a href={post.permalinkUrl} target="_blank" rel="noreferrer" className="line-clamp-2 hover:text-violet-700 lg:block lg:truncate">
                      {post.content}
                    </a>
                    {post.nextAttemptAt && <p className="mt-1 text-xs text-amber-700">Thử lại lúc {formatPostDateTime(post.nextAttemptAt)}</p>}
                    {post.status === 'Unknown' && <p className="mt-1 text-xs text-amber-800">Kiểm tra bài trên nền tảng trước khi chọn kết quả.</p>}
                    {post.attemptCount > 0 && <p className="mt-1 text-xs text-slate-500">Đã thử đăng {post.attemptCount} lần.</p>}
                    {post.lastError && <p className="mt-1 line-clamp-2 text-xs text-rose-700">Lỗi gần nhất: {post.lastError}</p>}
                  </div>
                ) : (
                  <div className="min-w-0 lg:order-2">
                    <p className="line-clamp-2 lg:truncate">{post.content}</p>
                    {post.nextAttemptAt && <p className="mt-1 text-xs text-amber-700">Thử lại lúc {formatPostDateTime(post.nextAttemptAt)}</p>}
                    {post.status === 'Unknown' && <p className="mt-1 text-xs text-amber-800">Kiểm tra bài trên nền tảng trước khi chọn kết quả.</p>}
                    {post.attemptCount > 0 && <p className="mt-1 text-xs text-slate-500">Đã thử đăng {post.attemptCount} lần.</p>}
                    {post.lastError && <p className="mt-1 line-clamp-2 text-xs text-rose-700">Lỗi gần nhất: {post.lastError}</p>}
                  </div>
                )}
                <div className="-ml-3 flex flex-wrap items-center gap-1 lg:contents lg:[&>*]:order-5">
                  <button
                    type="button"
                    onClick={() => setDetailPost(post)}
                    aria-label="Xem chi tiết bài"
                    className="flex h-11 w-11 items-center justify-center rounded-lg text-slate-600 hover:bg-slate-50 hover:text-slate-900 lg:h-8 lg:w-8"
                  >
                    <Eye size={15} aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    onClick={() => openEditPost(post)}
                    disabled={!isEditableScheduledPost(post)}
                    aria-label="Sửa bài đã đặt lịch"
                    className="flex h-11 w-11 items-center justify-center rounded-lg text-slate-600 hover:bg-violet-50 hover:text-violet-700 disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent disabled:hover:text-slate-600 lg:h-8 lg:w-8"
                    title={isEditableScheduledPost(post) ? 'Sửa bài đã đặt lịch' : 'Chỉ sửa được bài đang chờ đăng'}
                  >
                    <Pencil size={15} aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    onClick={() => setDeleteCandidate(post)}
                    aria-label="Xóa bài"
                    className="flex h-11 w-11 items-center justify-center rounded-lg text-slate-600 hover:bg-rose-50 hover:text-rose-600 lg:h-8 lg:w-8"
                  >
                    <Trash2 size={15} aria-hidden="true" />
                  </button>
                  {post.status === 'Unknown' ? (
                    <div className="flex flex-wrap gap-1">
                      <button type="button" onClick={() => setPendingAction({ kind: 'resolvePublished', post })} className="min-h-11 rounded border border-emerald-200 px-3 py-1 text-xs font-semibold text-emerald-700 hover:bg-emerald-50">Đã đăng</button>
                      <button type="button" onClick={() => setPendingAction({ kind: 'resolveNotPublished', post })} className="min-h-11 rounded border border-amber-300 px-3 py-1 text-xs font-semibold text-amber-800 hover:bg-amber-50">Chưa đăng · thử lại</button>
                    </div>
                  ) : post.status === 'Failed' ? (
                    <button type="button" onClick={() => setPendingAction({ kind: 'retry', post })} className="min-h-11 rounded border border-blue-200 px-3 py-1 text-xs font-semibold text-blue-700 hover:bg-blue-50">Thử lại</button>
                  ) : <span className="hidden lg:block" />}
                </div>
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
          draftKey={`edit:${editingPost.id}`}
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
          error={deleteError}
          onCancel={() => { setDeleteError(''); setDeleteCandidate(null) }}
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
    // Saved or dropped on purpose: the kept copy of unsaved edits is no longer needed.
    if (editingPost) clearDraft(`edit:${editingPost.id}`)
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
    setDeleteError('')
    try {
      const response = await api.delete<DeletePostResponse>(`/app-data/posts/${post.id}`, { params: post.scheduleId ? { scheduleId: post.scheduleId } : undefined })
      if (!response.data.deleted) {
        setDeleteError(response.data.message || response.data.facebookMessage || 'Không thể xóa bài viết.')
        return
      }
      setDeleteCandidate(null)
      setDetailPost((current) => current?.scheduleId === post.scheduleId ? null : current)
      reload()
    } catch (error) {
      setDeleteError(getErrorMessage(error, 'Không thể xóa bài viết.'))
    } finally {
      setIsDeleting(false)
    }
  }

  function confirmMessage(action: PendingScheduleAction) {
    if (action.kind === 'resolvePublished') return 'Hãy xác nhận bạn đã mở nền tảng và tìm thấy bài đăng. Đánh dấu lịch là đã đăng?'
    if (action.kind === 'resolveNotPublished') return 'Chỉ tiếp tục nếu bạn đã kiểm tra Page/tài khoản đích và chắc chắn bài chưa xuất hiện. Postiva sẽ thử đăng lại; nếu kết quả trước còn chưa rõ, thao tác này có thể tạo bài trùng.'
    return 'Thử đăng lại bài này ngay? Hãy kiểm tra trước rằng bài chưa được đăng trên nền tảng.'
  }

  async function runPendingAction() {
    const action = pendingAction
    setPendingAction(null)
    if (!action) return
    if (action.kind === 'retry') await retryFailed(action.post)
    else await resolveUnknown(action.post, action.kind === 'resolvePublished')
  }

  async function resolveUnknown(post: AppPost, published: boolean) {
    if (!post.scheduleId) return
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
    if (!post.scheduleId) return
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
