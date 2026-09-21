import { Eye, X } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { EditPostModal } from '../components/EditPostModal'
import { DeletePostConfirmModal } from '../components/DeletePostConfirmModal'
import { getPaginatedItems, PaginationControls } from '../components/PaginationControls'
import { PostDetailModal, PostThumbnail } from '../components/PostDetails'
import { RepostPostModal } from '../components/RepostPostModal'
import { api } from '../lib/api'
import { normalizeAppOverview, type AppOverview, type AppPost, useAppDataResource } from '../lib/appDataApi'
import { addDays, toDateKey } from '../lib/dates'
import { getErrorMessage } from '../lib/errors'
import { sortPostsNewestFirst } from '../lib/posts'
import { usePreferences } from '../lib/preferences'
import { useFacebookRealtimeRefetch } from '../lib/realtime'
import { PageHeader, PageState, PlatformBadge, StatusBadge } from '../components/PageUi'

const reportPageSize = 5
type ChartPeriod = 'DAY' | 'WEEK' | 'MONTH'
type DeletePostResponse = {
  deleted: boolean
  facebookDeleted: boolean
  facebookMessage: string
  message?: string
}

export function ReportsPage() {
  const { t } = usePreferences()
  const { data: overview, loading, error, reload } = useAppDataResource<AppOverview>('/app-data/overview')
  const postsState = useAppDataResource<AppPost[]>('/app-data/posts')
  useFacebookRealtimeRefetch(() => {
    reload()
    postsState.reload()
  })
  const [reportPage, setReportPage] = useState(1)
  const [editingPost, setEditingPost] = useState<AppPost | null>(null)
  const [editingMessage, setEditingMessage] = useState('')
  const [editError, setEditError] = useState('')
  const [isSavingEdit, setIsSavingEdit] = useState(false)
  const [detailPost, setDetailPost] = useState<AppPost | null>(null)
  const [deleteCandidate, setDeleteCandidate] = useState<AppPost | null>(null)
  const [isDeleting, setIsDeleting] = useState(false)
  const [repostingPost, setRepostingPost] = useState<AppPost | null>(null)
  const [chartPeriod, setChartPeriod] = useState<ChartPeriod>('DAY')
  const [selectedChartBucket, setSelectedChartBucket] = useState<string | null>(null)

  useEffect(() => {
    setReportPage(1)
  }, [postsState.data?.length])

  if (loading || postsState.loading) {
    return <PageState title={t('Đang tải báo cáo...', 'Loading reports...')} />
  }

  if (error || postsState.error || !overview || !postsState.data) {
    return <PageState title="Không thể tải báo cáo" description={error ?? postsState.error ?? undefined} />
  }

  const normalizedOverview = normalizeAppOverview(overview)
  const reportPosts = sortPostsNewestFirst(postsState.data
    .filter((post) => post.status === 'Published' || post.status === 'Failed'))
  const paginatedReports = getPaginatedItems(reportPosts, reportPage, reportPageSize)
  const publishedPosts = sortPostsNewestFirst(postsState.data.filter((post) => post.status === 'Published'))
  const chartBuckets = getChartBuckets(publishedPosts, chartPeriod)
  const maxPostsInBucket = Math.max(...chartBuckets.map((bucket) => bucket.count), 1)
  const selectedBucket = chartBuckets.find((bucket) => bucket.key === selectedChartBucket) ?? null
  const platformCounts = getPlatformCounts(reportPosts)
  const totalPostsForPlatformChart = Math.max(reportPosts.length, 1)
  const finishedPosts = normalizedOverview.publishedPosts + normalizedOverview.failedPosts
  const successRate = finishedPosts === 0 ? 0 : Math.round((normalizedOverview.publishedPosts / finishedPosts) * 100)

  return (
    <section className="space-y-6">
      <PageHeader title={t('Báo cáo & Phân tích', 'Reports & Analytics')} />

      <div className="grid gap-6 lg:grid-cols-2">
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <div className="mb-6 flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-base font-bold">{getChartTitle(chartPeriod, t)}</h2>
            <div className="flex items-center gap-1 rounded-full border border-[var(--app-border)] p-1">
              {([['DAY', t('Ngày', 'Day')], ['WEEK', t('Tuần', 'Week')], ['MONTH', t('Tháng', 'Month')]] as const).map(([value, label]) => (
                <button
                  key={value}
                  type="button"
                  onClick={() => {
                    setChartPeriod(value)
                    setSelectedChartBucket(null)
                  }}
                  className={`flex h-10 w-10 items-center justify-center rounded-full p-0 text-[10px] font-bold transition ${chartPeriod === value ? 'bg-blue-600 text-white shadow-sm' : 'text-[var(--muted-text)] hover:bg-[var(--soft-bg)]'}`}
                >
                  {label}
                </button>
              ))}
            </div>
          </div>
          <div className="overflow-x-auto pb-1">
          <div className={`flex h-40 items-end justify-around border-b border-l border-dashed border-slate-200 px-2 sm:px-5 ${chartPeriod === 'MONTH' ? 'min-w-[620px]' : 'min-w-[420px]'}`}>
            {chartBuckets.map((bucket) => (
              <button
                key={bucket.key}
                type="button"
                onClick={() => setSelectedChartBucket(bucket.key)}
                aria-label={`${bucket.count} ${t('bài đã đăng', 'published posts')}, ${bucket.detailLabel}`}
                aria-pressed={selectedChartBucket === bucket.key}
                className="group flex h-full min-w-9 flex-col justify-end gap-2 rounded-t px-1 outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
              >
                <span className="flex h-[calc(100%-1.5rem)] items-end justify-center">
                  <span
                    className={`block w-5 rounded-t transition group-hover:bg-blue-500 ${selectedChartBucket === bucket.key ? 'bg-blue-500 ring-2 ring-blue-200' : 'bg-blue-600'}`}
                    style={{ height: bucket.count === 0 ? '2px' : `${Math.round((bucket.count / maxPostsInBucket) * 100)}%` }}
                  />
                </span>
                <span className={`whitespace-nowrap text-xs ${selectedChartBucket === bucket.key ? 'font-bold text-blue-600' : 'text-slate-400 group-hover:text-blue-600'}`}>{bucket.label}</span>
              </button>
            ))}
          </div>
          </div>
          {selectedBucket && (
            <div className="mt-5 border-t border-[var(--app-border)] pt-4">
              <div className="mb-3 flex items-center justify-between gap-3">
                <div>
                  <h3 className="text-sm font-bold">{selectedBucket.detailLabel}</h3>
                  <p className="mt-1 text-xs text-[var(--muted-text)]">{selectedBucket.posts.length} {t('bài đã đăng', 'published posts')}</p>
                </div>
                <button
                  type="button"
                  onClick={() => setSelectedChartBucket(null)}
                  className="flex h-8 w-8 items-center justify-center rounded-lg text-[var(--muted-text)] hover:bg-[var(--soft-bg)]"
                  aria-label={t('Đóng danh sách', 'Close list')}
                >
                  <X size={16} />
                </button>
              </div>
              {selectedBucket.posts.length === 0 ? (
                <p className="rounded-lg bg-[var(--soft-bg)] px-4 py-3 text-sm text-[var(--muted-text)]">
                  {t('Không có bài đăng trong ngày này.', 'No published posts on this day.')}
                </p>
              ) : (
                <div className="max-h-72 space-y-2 overflow-y-auto pr-1">
                  {selectedBucket.posts.map((post) => (
                    <button
                      key={postRowKey(post)}
                      type="button"
                      onClick={() => setDetailPost(post)}
                      className="grid w-full grid-cols-[minmax(0,1fr)_auto] items-center gap-3 rounded-lg border border-[var(--app-border)] px-3 py-2 text-left hover:bg-[var(--soft-bg)] focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-500"
                    >
                      <span className="flex min-w-0 items-center gap-3">
                        <PostThumbnail post={post} className="h-10 w-10" />
                        <span className="min-w-0">
                          <span className="block truncate text-sm text-slate-700">{post.content || t('Không có nội dung chữ.', 'No text content.')}</span>
                          <span className="mt-1 block text-xs text-[var(--muted-text)]">{post.platform} · {formatPostDateTime(post.time)}</span>
                        </span>
                      </span>
                      <span className="inline-flex items-center gap-1 text-xs font-bold text-blue-600">
                        <Eye size={14} /> {t('Chi tiết', 'Details')}
                      </span>
                    </button>
                  ))}
                </div>
              )}
            </div>
          )}
        </div>

        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <h2 className="mb-6 text-base font-bold">{t('Phân bổ theo nền tảng', 'Platform breakdown')}</h2>
          <div className="space-y-5">
            {platformCounts.length === 0 ? (
              <p className="text-sm text-[var(--muted-text)]">{t('Chưa có dữ liệu nền tảng.', 'No platform data yet.')}</p>
            ) : (
              platformCounts.map((item) => (
                <div key={item.platform} className="grid grid-cols-[90px_1fr] items-center gap-3">
                  <span className="text-xs text-slate-500">{item.platform}</span>
                  <div className="h-3 rounded-full bg-slate-100">
                    <div
                      className="h-3 rounded-full bg-blue-600"
                      style={{ width: `${Math.round((item.count / totalPostsForPlatformChart) * 100)}%` }}
                    />
                  </div>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <p className="text-base font-bold">{t('Tỷ lệ thành công', 'Success rate')}</p>
          <p className="mt-3 text-4xl font-bold">{successRate}%</p>
          <div className="mt-3 h-2 rounded-full bg-emerald-100">
            <div className="h-2 rounded-full bg-emerald-500" style={{ width: `${successRate}%` }} />
          </div>
          <p className="mt-2 text-xs text-[var(--muted-text)]">{normalizedOverview.publishedPosts} {t('đã đăng', 'published')} / {normalizedOverview.failedPosts} {t('thất bại', 'failed')}</p>
        </div>
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <p className="text-base font-bold">{t('Đang chờ đăng', 'Scheduled')}</p>
          <p className="mt-3 text-4xl font-bold">{normalizedOverview.scheduledPosts}</p>
          <p className="mt-2 text-xs text-[var(--muted-text)]">{t('bài đã lên lịch', 'scheduled posts')}</p>
        </div>
        <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
          <p className="text-base font-bold">{t('Tổng bài đăng', 'Total posts')}</p>
          <p className="mt-3 text-4xl font-bold">{normalizedOverview.totalPosts}</p>
          <p className="mt-2 text-xs text-[var(--muted-text)]">{t('trên tất cả kênh', 'across all channels')}</p>
        </div>
      </div>

      <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
        <h2 className="border-b border-[var(--app-border)] p-5 text-base font-bold">{t('Lịch sử bài đăng', 'Post history')}</h2>
        <div className="divide-y divide-slate-100">
          {reportPosts.length === 0 ? (
            <div className="px-5 py-6 text-sm text-[var(--muted-text)]">{t('Chưa có lịch sử bài đăng.', 'No post history yet.')}</div>
          ) : (
            paginatedReports.pageItems.map((post) => (
              <button
                key={postRowKey(post)}
                type="button"
                onClick={() => setDetailPost(post)}
                className="grid w-full grid-cols-[90px_minmax(0,1fr)_110px_90px_32px] items-center gap-4 px-5 py-4 text-left text-sm hover:bg-[var(--soft-bg)]"
              >
                <PlatformBadge platform={post.platform} />
                <span className="truncate text-slate-700" title={post.content}>{post.content || 'Không có nội dung chữ.'}</span>
                <StatusBadge status={post.status} />
                <span className="text-xs leading-5 text-[var(--muted-text)]">{formatPostDateTime(post.time)}</span>
                <span className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400">
                  <Eye size={15} />
                </span>
              </button>
            ))
          )}
        </div>
        <PaginationControls
          page={paginatedReports.currentPage}
          pageSize={reportPageSize}
          totalItems={reportPosts.length}
          totalPages={paginatedReports.totalPages}
          onPageChange={setReportPage}
        />
      </div>

      {editingPost && (
        <EditPostModal
          title={t('Sửa bài đã đăng', 'Edit published post')}
          message={editingMessage}
          error={editError}
          saving={isSavingEdit}
          saveLabel={t('Lưu thay đổi', 'Save changes')}
          savingLabel={t('Đang lưu...', 'Saving...')}
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
          onEdit={canEditReportedPost(detailPost) ? () => {
            openEditPost(detailPost)
            setDetailPost(null)
          } : undefined}
          onDelete={canDeleteReportedPost(detailPost) ? () => {
            setDeleteCandidate(detailPost)
          } : undefined}
          onRepost={detailPost.status === 'Published' && detailPost.platform.toLowerCase() === 'facebook' ? () => {
            setRepostingPost(detailPost)
            setDetailPost(null)
          } : undefined}
        />
      )}

      {repostingPost && (
        <RepostPostModal
          post={repostingPost}
          onClose={() => setRepostingPost(null)}
          onReposted={() => {
            reload()
            postsState.reload()
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
      setEditError(t('Nội dung bài đăng là bắt buộc.', 'Post content is required.'))
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
      postsState.reload()
    } catch (error) {
      setEditError(getErrorMessage(error, 'Không thể cập nhật bài viết trên Facebook.'))
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
      postsState.reload()
    } catch (error) {
      window.alert(getErrorMessage(error, 'Không thể xóa bài viết.'))
    } finally {
      setIsDeleting(false)
    }
  }

}

function canEditReportedPost(post: AppPost) {
  return post.status !== 'Published' || post.platform.toLowerCase() === 'facebook'
}

function canDeleteReportedPost(post: AppPost) {
  return post.status !== 'Published' || post.platform.toLowerCase() === 'facebook'
}

function postRowKey(post: AppPost) {
  return post.scheduleId || `${post.id}:${post.platform}`
}

function getChartBuckets(posts: AppPost[], period: ChartPeriod) {
  const locale = 'vi-VN'
  const today = startOfDay(new Date())
  const bucketCount = period === 'DAY' ? 7 : period === 'WEEK' ? 8 : 12

  const buckets = Array.from({ length: bucketCount }, (_, index) => {
    const offset = bucketCount - 1 - index
    let start: Date
    let end: Date
    let label: string
    let detailLabel: string

    if (period === 'DAY') {
      start = addDays(today, -offset)
      end = addDays(start, 1)
      label = `${start.getDate()}/${start.getMonth() + 1}`
      detailLabel = new Intl.DateTimeFormat(locale, {
        weekday: 'long', day: '2-digit', month: '2-digit', year: 'numeric',
      }).format(start)
    } else if (period === 'WEEK') {
      start = addDays(startOfWeek(today), -offset * 7)
      end = addDays(start, 7)
      label = `${start.getDate()}/${start.getMonth() + 1}`
      detailLabel = `${formatShortDate(start, locale)} – ${formatShortDate(addDays(end, -1), locale)}`
    } else {
      start = new Date(today.getFullYear(), today.getMonth() - offset, 1)
      end = new Date(start.getFullYear(), start.getMonth() + 1, 1)
      label = `T${start.getMonth() + 1}/${String(start.getFullYear()).slice(-2)}`
      detailLabel = new Intl.DateTimeFormat(locale, { month: 'long', year: 'numeric' }).format(start)
    }

    return { key: toDateKey(start), start, end, label, detailLabel, count: 0, posts: [] as AppPost[] }
  })

  for (const post of posts) {
    if (!post.scheduledTime) continue
    const postDate = new Date(post.scheduledTime)
    if (Number.isNaN(postDate.getTime())) continue
    const bucket = buckets.find((item) => postDate >= item.start && postDate < item.end)
    if (bucket) {
      bucket.count += 1
      bucket.posts.push(post)
    }
  }

  return buckets
}

function getChartTitle(period: ChartPeriod, t: (vi: string, en: string) => string) {
  if (period === 'WEEK') return t('Bài đăng 8 tuần gần nhất', 'Posts in the last 8 weeks')
  if (period === 'MONTH') return t('Bài đăng 12 tháng gần nhất', 'Posts in the last 12 months')
  return t('Bài đăng 7 ngày gần nhất', 'Posts in the last 7 days')
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate())
}

function startOfWeek(date: Date) {
  const day = date.getDay()
  return addDays(startOfDay(date), -(day === 0 ? 6 : day - 1))
}

function formatShortDate(date: Date, locale: string) {
  return new Intl.DateTimeFormat(locale, { day: '2-digit', month: '2-digit', year: 'numeric' }).format(date)
}

function formatPostDateTime(value: string) {
  const match = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/)
  if (match) {
    return `${match[3]}/${match[2]}/${match[1]} ${match[4]}:${match[5]}`
  }

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
  }).format(date)
}

function getPlatformCounts(posts: AppPost[]) {
  const counts = new Map<string, number>()
  for (const post of posts) {
    counts.set(post.platform, (counts.get(post.platform) ?? 0) + 1)
  }

  return Array.from(counts, ([platform, count]) => ({ platform, count }))
}
