import { CopyPlus, ExternalLink, Pencil, Trash2, X } from 'lucide-react'
import { ProtectedMediaImage, ProtectedMediaVideo } from './ProtectedMedia'
import { type AppPost } from '../lib/appDataApi'
import { resolveMediaUrl } from '../lib/media'
import { StatusBadge } from './PageUi'

export function PostSummary({
  post,
  showMediaBadge = true,
}: {
  post: AppPost
  showMediaBadge?: boolean
}) {
  return (
    <div className="flex min-w-0 gap-3">
      <PostThumbnail post={post} />
      <div className="min-w-0 flex-1">
        <p className="line-clamp-2 text-sm leading-6 text-slate-700">{post.content || 'Không có nội dung chữ.'}</p>
        {showMediaBadge && (
          <div className="mt-3 flex flex-wrap items-center gap-2">
            {(post.mediaUrl || post.mediaThumbnailUrl) ? (
              <span className="rounded-full border border-slate-200 bg-white px-3 py-1 text-xs font-semibold text-slate-600">
                {post.mediaType?.toLowerCase().includes('video') ? 'Có video' : 'Có ảnh'}
              </span>
            ) : null}
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
    <ProtectedMediaImage
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
          <ProtectedMediaVideo
            src={mediaUrl}
            poster={thumbnailUrl || undefined}
            controls
            preload="metadata"
            className="max-h-72 w-full bg-slate-950 object-contain"
          />
        ) : (
          <div className="relative bg-slate-950">
            <ProtectedMediaImage
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
            <ProtectedMediaImage
              key={`${galleryUrl}-${index}`}
              src={galleryUrl}
              alt={`Ảnh đã chọn ${index + 1}`}
              className="mx-auto h-auto max-h-72 w-full object-contain"
            />
          ))}
        </div>
      ) : (
        <ProtectedMediaImage
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
          <div className="flex flex-wrap items-center gap-3 text-sm">
            <StatusBadge status={post.status} />
            {post.attemptCount > 0 && <span className="text-slate-500">Đã thử {post.attemptCount} lần</span>}
            {post.nextAttemptAt && <span className="text-amber-700">Thử lại lúc {new Date(post.nextAttemptAt).toLocaleString('vi-VN')}</span>}
          </div>
          {post.lastError && (
            <p role="status" className={`rounded-lg p-3 text-sm ${post.status === 'Unknown' ? 'bg-amber-50 text-amber-900' : 'bg-rose-50 text-rose-800'}`}>
              {post.status === 'Unknown' ? 'Cần kiểm tra kết quả: ' : 'Lỗi gần nhất: '}{post.lastError}
            </p>
          )}
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
          <p className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600">
            Số lượt xem, tương tác và bình luận chưa được đồng bộ. Mở bài trên nền tảng để xem số liệu hiện tại.
          </p>
        </div>
      </div>
    </div>
  )
}
