import { CalendarDays, ChevronLeft, ChevronRight, Eye, Pencil, Trash2 } from 'lucide-react'
import { useEffect, useMemo, useState, type FormEvent } from 'react'
import { EditPostModal } from '../components/EditPostModal'
import { getPaginatedItems, PaginationControls } from '../components/PaginationControls'
import { PostDetailModal, PostSummary } from '../components/PostDetails'
import { api } from '../lib/api'
import { type AppPost, useAppDataResource } from '../lib/appDataApi'
import { addDays, toDateKey } from '../lib/dates'
import { getErrorMessage } from '../lib/errors'
import { sortPostsNewestFirst } from '../lib/posts'
import { usePreferences } from '../lib/preferences'
import { useFacebookRealtimeRefetch } from '../lib/realtime'
import { PageHeader, PageState, PlatformBadge, StatusBadge } from '../components/PageUi'

type CalendarDay = {
  date: Date
  inCurrentMonth: boolean
}

const schedulePageSize = 8

export function SchedulePage() {
  const { t } = usePreferences()
  const { data: posts, loading, error, reload } = useAppDataResource<AppPost[]>('/app-data/posts')
  useFacebookRealtimeRefetch(() => reload())
  const [visibleMonth, setVisibleMonth] = useState(() => startOfMonth(new Date()))
  const [selectedDate, setSelectedDate] = useState(() => stripTime(new Date()))
  const [schedulePage, setSchedulePage] = useState(1)
  const [editingPost, setEditingPost] = useState<AppPost | null>(null)
  const [editingMessage, setEditingMessage] = useState('')
  const [editError, setEditError] = useState('')
  const [isSavingEdit, setIsSavingEdit] = useState(false)
  const [detailPost, setDetailPost] = useState<AppPost | null>(null)

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

  const scheduledPosts = sortPostsNewestFirst(posts.filter(isEditableScheduledPost))
  const paginatedSchedule = getPaginatedItems(scheduledPosts, schedulePage, schedulePageSize)
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
                <PostCard key={post.id} post={post} />
              ))
            )}
          </div>
        </div>
      </div>

      <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
        <h2 className="border-b border-[var(--app-border)] p-5 text-base font-bold">{t('Bài đang chờ đăng')}</h2>
        <div className="divide-y divide-slate-100">
          {scheduledPosts.length === 0 ? (
            <div className="px-5 py-6 text-sm text-[var(--muted-text)]">{t('Chưa có bài nào đang chờ đăng.')}</div>
          ) : (
            paginatedSchedule.pageItems.map((post) => (
              <div key={post.id} className="grid grid-cols-[90px_1fr_110px_90px_32px_32px_32px] items-center gap-4 px-5 py-4 text-sm">
                <PlatformBadge platform={post.platform} />
                {post.permalinkUrl ? (
                  <a href={post.permalinkUrl} target="_blank" rel="noreferrer" className="truncate hover:text-violet-700">
                    {post.content}
                  </a>
                ) : (
                  <p className="truncate">{post.content}</p>
                )}
                <StatusBadge status={post.status} />
                <span className="text-xs text-[var(--muted-text)]">{post.time}</span>
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
                  onClick={() => void deletePost(post)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-300 hover:bg-rose-50 hover:text-rose-500"
                >
                  <Trash2 size={15} />
                </button>
              </div>
            ))
          )}
        </div>
        <PaginationControls
          page={paginatedSchedule.currentPage}
          pageSize={schedulePageSize}
          totalItems={scheduledPosts.length}
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
            const post = detailPost
            setDetailPost(null)
            void deletePost(post)
          }}
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
    if (!window.confirm(t('Xóa bài này khỏi lịch đăng?'))) {
      return
    }

    try {
      await api.delete(`/app-data/posts/${post.id}`)
      reload()
    } catch (error) {
      window.alert(getErrorMessage(error, 'Không thể xóa bài viết.'))
    }
  }
}

function PostCard({ post }: { post: AppPost }) {
  return (
    <div className="rounded-xl border border-slate-100 bg-slate-50 p-4">
      <div className="mb-2 flex items-center gap-2">
        <PlatformBadge platform={post.platform} />
        <StatusBadge status={post.status} />
        <span className="ml-auto text-xs text-slate-400">{post.time.split(' ')[1] ?? ''}</span>
      </div>
      <PostSummary post={post} showEngagement={false} showMediaBadge={false} />
    </div>
  )
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
