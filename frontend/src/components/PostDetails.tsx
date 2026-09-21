import { CopyPlus, ExternalLink, Eye, Heart, MessageCircle, Pencil, Share2, ThumbsUp, Trash2, X } from 'lucide-react'
import { type FormEvent, type ReactNode, useState } from 'react'
import { api } from '../lib/api'
import { type AppComment, type AppPost, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { formatNumber } from '../lib/format'
import { resolveMediaUrl } from '../lib/media'
import { usePreferences } from '../lib/preferences'
import { useFacebookCommentsRealtimeRefetch } from '../lib/realtime'

export function PostSummary({
  post,
  showEngagement = true,
  showMediaBadge = true,
}: {
  post: AppPost
  showEngagement?: boolean
  showMediaBadge?: boolean
}) {
  return (
    <div className="flex min-w-0 gap-3">
      <PostThumbnail post={post} />
      <div className="min-w-0 flex-1">
        <p className="line-clamp-2 text-sm leading-6 text-slate-700">{post.content || 'Không có nội dung chữ.'}</p>
        {(showMediaBadge || showEngagement) && (
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {showMediaBadge && (post.mediaUrl || post.mediaThumbnailUrl) ? (
              <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-600">
                {post.mediaType?.toLowerCase().includes('video') ? 'Có video' : 'Có ảnh'}
              </span>
            ) : null}
            {showEngagement && engagementMetrics(post).map((metric) => <EngagementMetricChip key={metric.label} metric={metric} />)}
          </div>
        )}
      </div>
    </div>
  )
}

export function PostThumbnail({ post, className = 'h-20 w-20' }: { post: AppPost; className?: string }) {
  const isVideo = post.mediaType?.toLowerCase().includes('video')
  const thumbnailUrl = resolveMediaUrl(post.mediaThumbnailUrl || (isVideo ? '' : post.mediaUrl))
  if (!thumbnailUrl) {
    return null
  }

  return (
    <img
      src={thumbnailUrl}
      alt={post.mediaTitle || (isVideo ? `Thumbnail video ${post.platform}` : `Ảnh bài đăng ${post.platform}`)}
      className={`${className} shrink-0 rounded-lg border border-slate-200 bg-slate-100 object-cover`}
      referrerPolicy="no-referrer"
    />
  )
}

export function PostMedia({ post, showMediaGallery = false }: { post: AppPost; showMediaGallery?: boolean }) {
  const mediaUrl = resolveMediaUrl(post.mediaUrl)
  const thumbnailUrl = resolveMediaUrl(post.mediaThumbnailUrl || post.mediaUrl)
  const galleryUrls = (post.mediaUrls ?? []).map(resolveMediaUrl).filter(Boolean)
  if (!mediaUrl && !thumbnailUrl && galleryUrls.length === 0) {
    return null
  }

  const mediaType = post.mediaType.toLowerCase()
  const isVideo = mediaType.includes('video')
  const facebookEmbedUrl = isVideo ? facebookVideoEmbedUrl(post.permalinkUrl) : ''

  return (
    <div className="overflow-hidden rounded-xl border border-slate-200 bg-slate-100">
      {isVideo ? (
        facebookEmbedUrl ? (
          <iframe
            title={post.mediaTitle || `${post.platform} video`}
            src={facebookEmbedUrl}
            className="h-[405px] w-full bg-slate-950"
            allow="autoplay; clipboard-write; encrypted-media; picture-in-picture; web-share"
            allowFullScreen
          />
        ) : mediaUrl ? (
          <video
            src={mediaUrl}
            poster={thumbnailUrl || undefined}
            controls
            preload="metadata"
            className="max-h-72 w-full bg-slate-950 object-contain"
          />
        ) : (
          <div className="relative bg-slate-950">
            <img
              src={thumbnailUrl}
              alt={post.mediaTitle || `Thumbnail video ${post.platform}`}
              className="mx-auto h-auto max-h-72 max-w-full object-contain opacity-80"
            />
            <div className="absolute inset-x-0 bottom-0 bg-slate-950/75 px-3 py-2 text-xs font-semibold text-white">
              Video này chưa có nguồn phát trực tiếp. Mở trên {post.platform} để xem đầy đủ.
            </div>
          </div>
        )
      ) : showMediaGallery && galleryUrls.length > 1 ? (
        <div className="grid grid-cols-2 gap-1 p-1">
          {galleryUrls.map((galleryUrl, index) => (
            <img
              key={`${galleryUrl}-${index}`}
              src={galleryUrl}
              alt={`Ảnh đã chọn ${index + 1}`}
              className="mx-auto h-auto max-h-72 w-full object-contain"
            />
          ))}
        </div>
      ) : (
        <img
          src={thumbnailUrl || mediaUrl}
          alt={post.mediaTitle || 'Media bài đăng'}
          className="mx-auto h-auto max-h-72 max-w-full object-contain"
        />
      )}
      {(post.mediaTitle || post.mediaType) && (
        <div className="flex items-center justify-between gap-3 bg-white px-3 py-2 text-xs text-slate-500">
          <span className="truncate">{post.mediaTitle || 'Media bài đăng'}</span>
          {post.mediaType && <span className="shrink-0 font-semibold">{post.mediaType}</span>}
        </div>
      )}
    </div>
  )
}

function facebookVideoEmbedUrl(permalinkUrl: string) {
  const value = permalinkUrl.trim()
  if (!value || !/^https?:\/\/(www\.)?facebook\.com\//i.test(value)) {
    return ''
  }
  return `https://www.facebook.com/plugins/video.php?href=${encodeURIComponent(value)}&show_text=false&width=720`
}

export function ReactionBreakdown({ post }: { post: AppPost }) {
  return (
    <div className="flex flex-wrap gap-2">
      {engagementMetrics(post).map((metric) => <EngagementMetricChip key={metric.label} metric={metric} />)}
    </div>
  )
}

function engagementMetrics(post: AppPost) {
  return [
    { label: 'Like', count: post.reactionLikeCount || post.likeCount, icon: <FacebookReactionIcon type="LIKE" />, tone: '' },
    { label: 'Love', count: post.reactionLoveCount, icon: <FacebookReactionIcon type="LOVE" />, tone: '' },
    { label: 'Care', count: post.reactionCareCount, icon: <FacebookReactionIcon type="CARE" />, tone: '' },
    { label: 'Haha', count: post.reactionHahaCount, icon: <FacebookReactionIcon type="HAHA" />, tone: '' },
    { label: 'Wow', count: post.reactionWowCount, icon: <FacebookReactionIcon type="WOW" />, tone: '' },
    { label: 'Sad', count: post.reactionSadCount, icon: <FacebookReactionIcon type="SAD" />, tone: '' },
    { label: 'Angry', count: post.reactionAngryCount, icon: <FacebookReactionIcon type="ANGRY" />, tone: '' },
    { label: 'Bình luận', count: post.commentCount, icon: <MessageCircle size={14} />, tone: 'text-emerald-600' },
    { label: 'Chia sẻ', count: post.shareCount, icon: <Share2 size={14} />, tone: 'text-violet-600' },
  ]
}

type FacebookReactionType = 'LIKE' | 'LOVE' | 'CARE' | 'HAHA' | 'WOW' | 'SAD' | 'ANGRY'

function FacebookReactionIcon({ type }: { type: FacebookReactionType }) {
  if (type === 'LIKE') {
    return (
      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-gradient-to-br from-blue-500 to-violet-600 text-white">
        <ThumbsUp size={12} fill="currentColor" strokeWidth={2.5} />
      </span>
    )
  }
  if (type === 'LOVE') {
    return (
      <span className="flex h-5 w-5 items-center justify-center rounded-full bg-red-500 text-white">
        <Heart size={12} fill="currentColor" strokeWidth={2.5} />
      </span>
    )
  }

  const emoji = {
    CARE: '🥰',
    HAHA: '😆',
    WOW: '😮',
    SAD: '😢',
    ANGRY: '😡',
  }[type]

  return <span className="text-[20px] leading-none">{emoji}</span>
}

function EngagementMetricChip({
  metric,
}: {
  metric: { label: string; count: number; icon: ReactNode; tone: string }
}) {
  return (
    <span
      className="inline-flex min-h-8 items-center gap-1.5 rounded-full border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-600"
      title={metric.label}
      aria-label={`${metric.label}: ${metric.count}`}
    >
      <span className={metric.tone}>{metric.icon}</span>
      <span>{metric.count}</span>
    </span>
  )
}

export function PostDetailModal({
  post,
  onClose,
  onEdit,
  onDelete,
  onRepost,
  showMediaGallery = false,
}: {
  post: AppPost
  onClose: () => void
  onEdit?: () => void
  onDelete?: () => void
  onRepost?: () => void
  showMediaGallery?: boolean
}) {
  const {
    data: comments,
    loading: commentsLoading,
    error: commentsError,
    reload: reloadComments,
  } = useAppDataResource<AppComment[]>(`/app-data/posts/${post.id}/comments`)
  useFacebookCommentsRealtimeRefetch(
    post.platform.toLowerCase() === 'facebook' ? post.platformPostId : '',
    reloadComments,
  )

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 px-4">
      <div className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold">Chi tiết bài đăng</h2>
            <p className="mt-1 text-xs text-slate-400">{post.platform} - {post.time}</p>
          </div>
          <div className="flex items-center gap-1">
            {onEdit && (
              <button type="button" onClick={onEdit} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-violet-50 hover:text-violet-700" title="Sửa bài">
                <Pencil size={16} />
              </button>
            )}
            {onRepost && post.status === 'Published' && (
              <button type="button" onClick={onRepost} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-emerald-50 hover:text-emerald-700" title="Tạo bài mới từ bài này">
                <CopyPlus size={16} />
              </button>
            )}
            {onDelete && (
              <button type="button" onClick={onDelete} className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-rose-50 hover:text-rose-600" title="Xóa bài">
                <Trash2 size={16} />
              </button>
            )}
            <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg hover:bg-slate-100" title="Đóng">
              <X size={17} />
            </button>
          </div>
        </div>

        <div className="space-y-5">
          <PostMedia post={post} showMediaGallery={showMediaGallery} />
          <div>
            <p className="whitespace-pre-wrap text-sm leading-6 text-slate-700">{post.content || 'Không có nội dung chữ.'}</p>
            {post.permalinkUrl && (
              <a
                href={post.permalinkUrl}
                target="_blank"
                rel="noreferrer"
                className="mt-4 inline-flex items-center gap-2 text-sm font-bold text-violet-700 hover:text-violet-800"
              >
                <ExternalLink size={15} />
                 Mở trên {post.platform}
              </a>
            )}
          </div>
          <div>
            <h3 className="mb-3 text-sm font-bold text-slate-700">Tương tác</h3>
            <div className="mb-3 inline-flex items-center gap-2 rounded-xl border border-blue-100 bg-blue-50 px-3 py-2 text-sm font-bold text-blue-700">
              <Eye size={16} />
              Người xem: {formatNumber(post.viewCount)}
            </div>
            <ReactionBreakdown post={post} />
          </div>
          <div>
            <h3 className="mb-3 text-sm font-bold text-slate-700">Bình luận</h3>
            <CommentList
              comments={comments ?? []}
              platform={post.platform}
              loading={commentsLoading}
              error={commentsError}
              onReplied={reloadComments}
            />
          </div>
        </div>
      </div>
    </div>
  )
}

function CommentList({
  comments,
  platform,
  loading,
  error,
  onReplied,
}: {
  comments: AppComment[]
  platform: string
  loading: boolean
  error: string | null
  onReplied: () => void
}) {
  if (loading) {
    return <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">Đang tải bình luận...</p>
  }

  if (error) {
    return <p className="rounded-xl bg-rose-50 p-4 text-sm text-rose-600">{error}</p>
  }

  if (comments.length === 0) {
    return (
      <p className="rounded-xl bg-slate-50 p-4 text-sm text-slate-500">
        Chưa có bình luận nào được đồng bộ từ {platform} cho bài này.
      </p>
    )
  }

  const commentIds = new Set(comments.map((comment) => comment.platformCommentId))
  const repliesByParent = new Map<string, AppComment[]>()
  const rootComments: AppComment[] = []

  comments.forEach((comment) => {
    const parentId = comment.parentCommentId?.trim()
    if (parentId && commentIds.has(parentId)) {
      const replies = repliesByParent.get(parentId) ?? []
      replies.push(comment)
      repliesByParent.set(parentId, replies)
      return
    }
    rootComments.push(comment)
  })

  return (
    <div className="space-y-3">
      {rootComments.map((comment) => (
        <CommentItem
          key={comment.id}
          comment={comment}
          repliesByParent={repliesByParent}
          onReplied={onReplied}
        />
      ))}
    </div>
  )
}

function CommentItem({
  comment,
  repliesByParent,
  onReplied,
  nested = false,
  ancestorIds = new Set<string>(),
}: {
  comment: AppComment
  repliesByParent: Map<string, AppComment[]>
  onReplied: () => void
  nested?: boolean
  ancestorIds?: Set<string>
}) {
  const { t } = usePreferences()
  const [replyOpen, setReplyOpen] = useState(false)
  const [replyMessage, setReplyMessage] = useState('')
  const [replying, setReplying] = useState(false)
  const [replyError, setReplyError] = useState('')
  const avatarUrl = resolveMediaUrl(comment.commenterAvatarUrl)
  const attachmentUrl = resolveMediaUrl(comment.attachmentUrl)
  const directReplies = repliesByParent.get(comment.platformCommentId) ?? []
  const nextAncestorIds = new Set(ancestorIds).add(comment.platformCommentId)

  async function submitReply(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const message = replyMessage.trim()
    if (!message || replying) {
      return
    }

    setReplying(true)
    setReplyError('')
    try {
      await api.post('/comments/reply', {
        commentId: comment.platformCommentId,
        message,
      })
      setReplyMessage('')
      setReplyOpen(false)
      onReplied()
    } catch (error) {
      setReplyError(getErrorMessage(error, 'Không thể trả lời bình luận.'))
    } finally {
      setReplying(false)
    }
  }

  return (
    <article className={`relative rounded-xl border bg-slate-50 p-4 ${nested ? 'border-blue-100 bg-white/70' : 'border-slate-100'}`}>
      <div className="mb-2 flex items-center gap-2">
        {avatarUrl ? (
          <img
            src={avatarUrl}
            alt={comment.commenterName || 'Người dùng Facebook'}
            className="h-7 w-7 shrink-0 rounded-full object-cover"
            referrerPolicy="no-referrer"
          />
        ) : (
          <span className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-blue-50 text-xs font-bold text-blue-700">
            {getCommentInitial(comment.commenterName)}
          </span>
        )}
        <div className="min-w-0">
          <p className="truncate text-sm font-bold text-slate-700">{comment.commenterName || 'Người dùng Facebook'}</p>
          <p className="text-xs text-slate-400">{formatCommentTime(comment.commentedAt)}</p>
        </div>
      </div>

      {comment.message ? (
        <p className="whitespace-pre-wrap text-sm leading-6 text-slate-700">{comment.message}</p>
      ) : !attachmentUrl ? (
        <p className="text-sm text-slate-500">(Bình luận không có nội dung chữ)</p>
      ) : null}

      {attachmentUrl && (
        <img
          src={attachmentUrl}
          alt={comment.attachmentType || 'Nội dung đính kèm của bình luận'}
          className="mt-3 max-h-48 max-w-full rounded-xl object-contain"
          referrerPolicy="no-referrer"
        />
      )}

      <div className="mt-3 flex items-center gap-3 text-xs font-semibold text-slate-500">
        <span className="inline-flex items-center gap-1">
          <MessageCircle size={13} />
          {t('Phản hồi')}: {Math.max(comment.replyCount, directReplies.length)}
        </span>
        <button type="button" onClick={() => setReplyOpen((value) => !value)} className="text-blue-600 hover:text-blue-700">
          {t('Trả lời')}
        </button>
      </div>

      {replyOpen && (
        <form onSubmit={submitReply} className="mt-3 flex gap-2">
          <input
            value={replyMessage}
            onChange={(event) => setReplyMessage(event.target.value)}
            placeholder={t('Nhập nội dung trả lời...')}
            className="h-9 min-w-0 flex-1 rounded-lg border border-slate-200 bg-white px-3 text-sm outline-none focus:border-blue-500"
            autoFocus
          />
          <button
            type="submit"
            disabled={replying || !replyMessage.trim()}
            className="h-9 rounded-lg bg-blue-600 px-3 text-xs font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
          >
            {replying ? t('Đang gửi') : t('Gửi')}
          </button>
        </form>
      )}
      {replyError && <p className="mt-2 text-xs font-semibold text-rose-600">{replyError}</p>}

      {directReplies.length > 0 && (
        <div className="mt-4 space-y-2 border-l-2 border-blue-200 pl-3 sm:pl-4">
          {directReplies
            .filter((reply) => !nextAncestorIds.has(reply.platformCommentId))
            .map((reply) => (
              <CommentItem
                key={reply.id}
                comment={reply}
                repliesByParent={repliesByParent}
                onReplied={onReplied}
                nested
                ancestorIds={nextAncestorIds}
              />
            ))}
        </div>
      )}
    </article>
  )
}

function getCommentInitial(value: string) {
  return value.trim().charAt(0).toUpperCase() || 'F'
}

function formatCommentTime(value: string | null) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  return new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
