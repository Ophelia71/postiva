import { CheckCircle2, Image, Link as LinkIcon, Send, Upload, Video, X } from 'lucide-react'
import { useEffect, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
import { api } from '../lib/api'
import { type AppChannel, type AppPost, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { formatFileSize } from '../lib/format'
import { resolveMediaUrl } from '../lib/media'
import { usePreferences } from '../lib/preferences'

type UploadedMedia = {
  id: string
  fileUrl: string
  fileName: string
  mimeType: string
  fileSize: number
}

type RepostMedia = UploadedMedia & {
  source: 'OLD' | 'NEW'
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
  const [newMediaType, setNewMediaType] = useState<RepostMediaType>('NONE')
  const [mediaUrl, setMediaUrl] = useState('')
  const [selectedMedia, setSelectedMedia] = useState<RepostMedia[]>(() => getPostMedia(post))
  const [isUploadingMedia, setIsUploadingMedia] = useState(false)
  const [isPublishing, setIsPublishing] = useState(false)
  const [status, setStatus] = useState(() => t('Nội dung được sao chép từ bài cũ. Bạn có thể chỉnh sửa trước khi tạo bài mới.'))
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
    const selectedMediaIds = selectedMedia.map((media) => media.id).filter(Boolean)
    const hasVideo = selectedMedia.some((media) => media.mimeType.startsWith('video/'))
    const mediaType: RepostMediaType = selectedMedia.length > 0
      ? hasVideo ? 'VIDEO' : 'IMAGE'
      : resolvedMediaUrl ? newMediaType : 'NONE'
    if (newMediaType !== 'NONE' && selectedMedia.length === 0 && !resolvedMediaUrl) {
      setStatusTone('error')
      setStatus(t('Hãy chọn file từ máy hoặc nhập URL media công khai.'))
      return
    }
    if (selectedMedia.some((media) => !media.id)) {
      setStatusTone('error')
      setStatus(t('Một số media cũ không thể tái sử dụng. Hãy xóa media đó rồi chọn lại file từ máy.'))
      return
    }
    if (selectedMedia.length > 0 && resolvedMediaUrl) {
      setStatusTone('error')
      setStatus(t('Không thể trộn media đã chọn với URL. Hãy tải URL media về máy rồi chọn file.'))
      return
    }

    setIsPublishing(true)
    setStatusTone('info')
    setStatus(t(`Đang tạo bài mới trên ${selectedChannel.name}...`))
    try {
      await api.post<string>('/facebook-business/posts', {
        pageId: selectedChannel.accountId,
        message: message.trim(),
        mediaType,
        mediaUrl: selectedMediaIds.length > 0 || mediaType === 'NONE' ? undefined : resolvedMediaUrl,
        mediaIds: selectedMediaIds.length > 0 ? selectedMediaIds : undefined,
      })
      setStatusTone('success')
      setStatus(t('Đã tạo và đăng bài mới thành công.'))
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
    const files = Array.from(event.target.files ?? [])
    if (files.length === 0) {
      return
    }

    const allImages = files.every((file) => file.type.startsWith('image/'))
    const allVideos = files.every((file) => file.type.startsWith('video/'))
    if (!allImages && !allVideos) {
      setStatusTone('error')
      setStatus(t('Không thể trộn ảnh và video trong một bài đăng.'))
      event.target.value = ''
      return
    }

    if (allVideos && (files.length > 1 || selectedMedia.length > 0)) {
      setStatusTone('error')
      setStatus(t('Facebook chỉ hỗ trợ 1 video cho mỗi bài đăng.'))
      event.target.value = ''
      return
    }
    if (allImages && selectedMedia.some((media) => media.mimeType.startsWith('video/'))) {
      setStatusTone('error')
      setStatus(t('Không thể trộn ảnh và video trong một bài đăng.'))
      event.target.value = ''
      return
    }
    if (allImages && selectedMedia.length + files.length > 10) {
      setStatusTone('error')
      setStatus(t('Mỗi bài tối đa 10 ảnh.'))
      event.target.value = ''
      return
    }

    setMediaUrl('')
    setIsUploadingMedia(true)
    setStatusTone('info')
    const inferredMediaType: RepostMediaType = allVideos ? 'VIDEO' : 'IMAGE'
    setStatus(inferredMediaType === 'IMAGE'
      ? t('Đang tải ảnh lên hệ thống...')
      : t('Đang tải video lên hệ thống...'))

    try {
      const uploaded = await Promise.all(files.map(async (file) => {
        const formData = new FormData()
        formData.append('file', file)
        const response = await api.post<UploadedMedia>('/files/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
        return { ...response.data, source: 'NEW' as const }
      }))
      setSelectedMedia((current) => [...current, ...uploaded])
      setStatusTone('success')
      setStatus(inferredMediaType === 'IMAGE'
        ? t('Ảnh đã tải lên. Có thể đăng lại.')
        : t('Video đã tải lên. Có thể đăng lại.'))
    } catch (error) {
      setStatusTone('error')
      setStatus(getErrorMessage(error, 'Không thể tải tệp lên.'))
    } finally {
      setIsUploadingMedia(false)
      event.target.value = ''
    }
  }

  function resetMedia() {
    setNewMediaType('NONE')
    setMediaUrl('')
  }

  function removeMedia(mediaId: string) {
    setSelectedMedia((current) => current.filter((media) => (media.id || media.fileUrl) !== mediaId))
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 px-4">
      <form onSubmit={submitRepost} className="max-h-[90vh] w-full max-w-3xl overflow-auto rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-5 flex items-start justify-between gap-4">
          <div>
            <h2 className="text-lg font-bold">Tạo bài mới từ bài này</h2>
            <p className="mt-1 text-xs text-slate-400">Bài cũ không bị thay đổi. Bài mới sẽ có permalink và tương tác riêng.</p>
          </div>
          <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg hover:bg-slate-100">
            <X size={17} />
          </button>
        </div>

        <div className="space-y-5">
          <label className="block text-sm font-bold">
            Facebook Page đăng bài mới
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

          <div>
            <p className="text-sm font-bold">Media sẽ đăng lại</p>
            {selectedMedia.length > 0 ? (
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                {selectedMedia.map((media, index) => (
                  <div key={media.id || media.fileUrl + index} className="relative overflow-hidden rounded-xl border border-slate-200 bg-slate-50">
                    {media.mimeType.startsWith('video/') ? (
                      <video src={resolveMediaUrl(media.fileUrl)} controls className="h-36 w-full bg-slate-950 object-contain" />
                    ) : (
                      <img src={resolveMediaUrl(media.fileUrl)} alt={'Media đăng lại ' + (index + 1)} className="h-36 w-full object-contain" />
                    )}
                    <div className="flex items-center justify-between gap-2 px-3 py-2 text-xs">
                      <span className="truncate font-semibold text-slate-600">
                        {media.source === 'OLD' ? 'Media cũ' : media.fileName + ' (' + formatFileSize(media.fileSize) + ')'}
                      </span>
                      <button
                        type="button"
                        onClick={() => removeMedia(media.id || media.fileUrl)}
                        className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg text-slate-500 hover:bg-rose-50 hover:text-rose-600"
                        aria-label="Xóa media"
                      >
                        <X size={15} />
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <p className="mt-2 text-xs text-slate-500">Chưa có media. Bạn có thể chọn thêm bên dưới.</p>
            )}

            <p className="mt-5 text-sm font-bold">Thêm media mới</p>
            <div className="mt-3 grid gap-3 sm:grid-cols-3">
              <MediaOption active={newMediaType === 'NONE'} icon={<LinkIcon size={16} />} label="Không thêm" onClick={resetMedia} />
              <MediaOption
                active={newMediaType === 'IMAGE'}
                icon={<Image size={16} />}
                label="Ảnh"
                onClick={() => setNewMediaType('IMAGE')}
              />
              <MediaOption
                active={newMediaType === 'VIDEO'}
                icon={<Video size={16} />}
                label="Video"
                onClick={() => setNewMediaType('VIDEO')}
              />
            </div>

            {newMediaType !== 'NONE' && (
              <div className="mt-4 space-y-3">
                <label className="flex min-h-28 cursor-pointer flex-col items-center justify-center rounded-xl border border-dashed border-slate-300 bg-slate-50 px-4 py-5 text-center hover:bg-slate-100">
                  <Upload size={20} className="text-violet-700" />
                  <span className="mt-2 text-sm font-bold text-slate-800">
                    {isUploadingMedia ? 'Đang tải file...' : 'Chọn ảnh/video từ máy'}
                  </span>
                  <span className="mt-1 text-xs text-slate-500">JPG, PNG, WebP, MP4, MOV hoặc WebM.</span>
                  <input
                    type="file"
                    accept={newMediaType === 'IMAGE' ? 'image/jpeg,image/png,image/webp' : 'video/mp4,video/quicktime,video/webm'}
                    multiple={newMediaType === 'IMAGE'}
                    onChange={(event) => void handleMediaFileChange(event)}
                    disabled={isUploadingMedia}
                    className="hidden"
                  />
                </label>

                <div className="flex items-center gap-3">
                  <div className="h-px flex-1 bg-slate-200" />
                  <span className="text-xs font-semibold text-slate-400">hoặc URL công khai</span>
                  <div className="h-px flex-1 bg-slate-200" />
                </div>
                <input
                  value={mediaUrl}
                  onChange={(event) => {
                    setMediaUrl(event.target.value)
                  }}
                  className="h-11 w-full rounded-xl border border-slate-200 bg-slate-50 px-3 text-sm outline-none focus:border-violet-500"
                  placeholder={newMediaType === 'IMAGE' ? 'https://.../anh.jpg' : 'https://.../video.mp4'}
                />
                <MediaPreview mediaType={newMediaType} mediaUrl={mediaUrl} />
              </div>
            )}
          </div>

          <p className={`rounded-lg border px-3 py-2 text-sm ${statusTone === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : statusTone === 'error' ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-50 text-slate-600'}`}>{status}</p>

          <button
            disabled={channelsState.loading || !selectedChannel || !message.trim() || isUploadingMedia || isPublishing}
            className="flex h-11 w-full items-center justify-center gap-2 rounded-xl bg-emerald-600 text-sm font-bold text-white hover:bg-emerald-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {isPublishing ? <CheckCircle2 size={16} /> : <Send size={16} />}
            {isPublishing ? 'Đang tạo bài mới...' : 'Tạo bài mới và đăng'}
          </button>
        </div>
      </form>
    </div>
  )
}

function getPostMedia(post: AppPost): RepostMedia[] {
  const urls = post.mediaUrls?.length > 0
    ? post.mediaUrls
    : post.mediaUrl
      ? [post.mediaUrl]
      : []
  const isVideo = post.mediaType?.toLowerCase().includes('video')

  return urls.map((fileUrl, index) => ({
    id: post.mediaIds?.[index] ?? '',
    fileUrl,
    fileName: 'Media cũ ' + (index + 1),
    mimeType: isVideo ? 'video/mp4' : 'image/jpeg',
    fileSize: 0,
    source: 'OLD' as const,
  }))
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
