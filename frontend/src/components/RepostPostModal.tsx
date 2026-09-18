import { CheckCircle2, Image, Link as LinkIcon, Send, Upload, Video, X } from 'lucide-react'
import { useEffect, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { api } from '../lib/api'
import { type AppChannel, type AppPost, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { formatFileSize } from '../lib/format'
import { resolveMediaUrl } from '../lib/media'
import { usePreferences } from '../lib/preferences'
import { PostMedia } from './PostDetails'

type UploadedMedia = {
  mediaId: string
  mediaType: 'IMAGE' | 'VIDEO'
  mediaUrl: string
  originalFilename: string
  contentType: string
  size: number
}

type RepostMediaType = 'NONE' | 'IMAGE' | 'VIDEO'

const EMPTY_CHANNELS: AppChannel[] = []

export function RepostPostModal({
  post,
  onClose,
  onReposted,
}: {
  post: AppPost
  onClose: () => void
  onReposted: () => void
}) {
  const { t } = usePreferences()
  const channelsState = useAppDataResource<AppChannel[]>('/app-data/channels')
  const facebookChannels = (channelsState.data ?? EMPTY_CHANNELS).filter((channel) => channel.badge === 'Facebook' && channel.active)
  const [selectedChannelId, setSelectedChannelId] = useState('')
  const [message, setMessage] = useState(post.content)
  const [selectedMediaType, setSelectedMediaType] = useState<RepostMediaType>('NONE')
  const [mediaUrl, setMediaUrl] = useState('')
  const [uploadedMedia, setUploadedMedia] = useState<UploadedMedia | null>(null)
  const [isUploadingMedia, setIsUploadingMedia] = useState(false)
  const [isPublishing, setIsPublishing] = useState(false)
  const [status, setStatus] = useState(() => t('Chọn media mới nếu muốn thay ảnh/video của bài đăng lại.'))
  const [statusTone, setStatusTone] = useState<'info' | 'success' | 'error'>('info')

  useEffect(() => {
    if (!selectedChannelId && facebookChannels[0]) {
      setSelectedChannelId(facebookChannels[0].id)
    }
  }, [facebookChannels, selectedChannelId])

  const selectedChannel = facebookChannels.find((channel) => channel.id === selectedChannelId)

  async function submitRepost(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!selectedChannel || !message.trim()) {
      setStatusTone('error')
      setStatus(t('Hãy chọn Facebook Page và nhập nội dung trước khi đăng lại.'))
      return
    }

    const resolvedMediaUrl = mediaUrl.trim()
    const resolvedMediaId = uploadedMedia?.mediaType === selectedMediaType ? uploadedMedia.mediaId : ''
    if (selectedMediaType !== 'NONE' && !resolvedMediaUrl && !resolvedMediaId) {
      setStatusTone('error')
      setStatus(t('Hãy chọn file từ máy hoặc nhập URL media công khai.'))
      return
    }

    setIsPublishing(true)
    setStatusTone('info')
    setStatus(t(`Đang đăng lại lên ${selectedChannel.name}...`))
    try {
      await api.post<string>('/facebook-business/posts', {
        pageId: selectedChannel.accountId,
        message: message.trim(),
        mediaType: selectedMediaType,
        mediaUrl: selectedMediaType === 'NONE' || resolvedMediaId ? undefined : resolvedMediaUrl,
        mediaId: selectedMediaType === 'NONE' ? undefined : resolvedMediaId || undefined,
      })
      setStatusTone('success')
      setStatus(t('Đã đăng lại thành công.'))
      console.info('[FacebookPost] Repost completed', {
        channelId: selectedChannel.id,
        channelName: selectedChannel.name,
      })
      onReposted()
      onClose()
    } catch (error) {
      setStatusTone('error')
      setStatus(getErrorMessage(error, 'Không thể đăng lại lên Trang Facebook.'))
    } finally {
      setIsPublishing(false)
    }
  }

  async function handleMediaFileChange(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (!file) {
      return
    }

    const inferredMediaType = file.type.startsWith('image/') ? 'IMAGE' : file.type.startsWith('video/') ? 'VIDEO' : null
    if (!inferredMediaType) {
      setStatusTone('error')
      setStatus(t('Chỉ hỗ trợ ảnh JPG/PNG/WebP hoặc video MP4/MOV/WebM.'))
      event.target.value = ''
      return
    }

    setSelectedMediaType(inferredMediaType)
    setUploadedMedia(null)
    setMediaUrl('')
    setIsUploadingMedia(true)
    setStatusTone('info')
    setStatus(inferredMediaType === 'IMAGE'
      ? t('Đang tải ảnh lên hệ thống...')
      : t('Đang tải video lên hệ thống...'))

    try {
      const formData = new FormData()
      formData.append('file', file)
      const response = await api.post<UploadedMedia>('/media/uploads', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setUploadedMedia(response.data)
      setStatusTone('success')
      setStatus(inferredMediaType === 'IMAGE'
        ? t('Ảnh đã tải lên. Có thể đăng lại.')
        : t('Video đã tải lên. Có thể đăng lại.'))
      console.info('[MediaUpload] Repost media upload completed', {
        mediaType: inferredMediaType,
        mediaId: response.data.mediaId,
      })
    } catch (error) {
      setUploadedMedia(null)
      setStatusTone('error')
      setStatus(getErrorMessage(error, 'Không thể tải tệp lên.'))
    } finally {
      setIsUploadingMedia(false)
      event.target.value = ''
    }
  }

  function resetMedia() {
    setSelectedMediaType('NONE')
    setMediaUrl('')
    setUploadedMedia(null)
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 px-4">
      <form onSubmit={submitRepost} className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold">Đăng lại bài</h2>
            <p className="mt-1 text-xs text-slate-400">Bài mới sẽ có permalink và tương tác riêng.</p>
          </div>
          <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg hover:bg-slate-100">
            <X size={17} />
          </button>
        </div>

        <div className="space-y-5">
          <label className="block text-sm font-bold">
            Facebook Page
            <select
              value={selectedChannelId}
              onChange={(event) => setSelectedChannelId(event.target.value)}
              className="mt-2 h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm outline-none focus:border-violet-500"
            >
              {facebookChannels.length === 0 ? (
                <option value="">Chưa có Facebook Page</option>
              ) : (
                facebookChannels.map((channel) => (
                  <option key={channel.id} value={channel.id}>
                    {channel.name}
                  </option>
                ))
              )}
            </select>
          </label>

          <label className="block text-sm font-bold">
            Nội dung mới
            <textarea
              value={message}
              onChange={(event) => setMessage(event.target.value)}
              className="mt-2 h-48 w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm leading-6 outline-none focus:border-violet-500"
            />
          </label>

          {(post.mediaUrl || post.mediaThumbnailUrl) && (
            <div>
              <p className="mb-2 text-sm font-bold">Media bài cũ để tham khảo</p>
              <PostMedia post={post} />
            </div>
          )}

          <div>
            <p className="text-sm font-bold">Media mới</p>
            <div className="mt-3 grid gap-3 sm:grid-cols-3">
              <MediaOption active={selectedMediaType === 'NONE'} icon={<LinkIcon size={16} />} label="Không media" onClick={resetMedia} />
              <MediaOption
                active={selectedMediaType === 'IMAGE'}
                icon={<Image size={16} />}
                label="Ảnh"
                onClick={() => {
                  setSelectedMediaType('IMAGE')
                  setUploadedMedia(null)
                }}
              />
              <MediaOption
                active={selectedMediaType === 'VIDEO'}
                icon={<Video size={16} />}
                label="Video"
                onClick={() => {
                  setSelectedMediaType('VIDEO')
                  setUploadedMedia(null)
                }}
              />
            </div>

            {selectedMediaType !== 'NONE' && (
              <div className="mt-4 space-y-3">
                <label className="flex min-h-28 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center hover:bg-slate-100">
                  <Upload size={20} className="text-violet-700" />
                  <span className="mt-2 text-sm font-bold text-slate-800">
                    {isUploadingMedia ? 'Đang tải file...' : 'Chọn ảnh/video từ máy'}
                  </span>
                  <span className="mt-1 text-xs text-slate-500">JPG, PNG, WebP, MP4, MOV hoặc WebM.</span>
                  <input
                    type="file"
                    accept={selectedMediaType === 'IMAGE' ? 'image/jpeg,image/png,image/webp' : 'video/mp4,video/quicktime,video/webm'}
                    onChange={(event) => void handleMediaFileChange(event)}
                    disabled={isUploadingMedia}
                    className="hidden"
                  />
                </label>

                {uploadedMedia && (
                  <div className="flex items-center justify-between gap-3 rounded-xl border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                    <span className="min-w-0 truncate font-semibold">
                      {uploadedMedia.originalFilename} ({formatFileSize(uploadedMedia.size)})
                    </span>
                    <button
                      type="button"
                      onClick={() => setUploadedMedia(null)}
                      className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg hover:bg-emerald-100"
                    >
                      <X size={15} />
                    </button>
                  </div>
                )}

                <div className="flex items-center gap-3">
                  <div className="h-px flex-1 bg-slate-200" />
                  <span className="text-xs font-semibold text-slate-400">hoặc URL công khai</span>
                  <div className="h-px flex-1 bg-slate-200" />
                </div>
                <input
                  value={mediaUrl}
                  onChange={(event) => {
                    setMediaUrl(event.target.value)
                    setUploadedMedia(null)
                  }}
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm outline-none focus:border-violet-500"
                  placeholder={selectedMediaType === 'IMAGE' ? 'https://.../anh.jpg' : 'https://.../video.mp4'}
                />
                <MediaPreview mediaType={selectedMediaType} mediaUrl={uploadedMedia?.mediaUrl || mediaUrl} />
              </div>
            )}
          </div>

          <p className={`rounded-lg border px-3 py-2 text-sm ${statusTone === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : statusTone === 'error' ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>{status}</p>

          <button
            disabled={channelsState.loading || !selectedChannel || !message.trim() || isUploadingMedia || isPublishing}
            className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 text-sm font-bold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {isPublishing ? <CheckCircle2 size={16} /> : <Send size={16} />}
            {isPublishing ? 'Đang đăng lại...' : 'Đăng lại thành bài mới'}
          </button>
        </div>
      </form>
    </div>
  )
}

function MediaOption({
  active,
  icon,
  label,
  onClick,
}: {
  active: boolean
  icon: ReactNode
  label: string
  onClick: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={[
        'flex h-11 items-center justify-center gap-2 rounded-xl border text-sm font-bold transition',
        active ? 'border-violet-200 bg-violet-50 text-violet-700' : 'border-slate-200 bg-slate-50 text-slate-500 hover:bg-slate-100',
      ].join(' ')}
    >
      {icon}
      {label}
    </button>
  )
}

function MediaPreview({ mediaType, mediaUrl }: { mediaType: 'IMAGE' | 'VIDEO'; mediaUrl: string }) {
  const previewMediaUrl = resolveMediaUrl(mediaUrl)
  if (!previewMediaUrl) {
    return null
  }

  if (mediaType === 'IMAGE') {
    return <img src={previewMediaUrl} alt="Media xem trước" className="mx-auto h-auto max-h-72 max-w-full rounded-xl border border-slate-200 object-contain" />
  }

  return <video src={previewMediaUrl} controls className="max-h-72 w-full rounded-xl border border-slate-200 bg-slate-950" />
}
