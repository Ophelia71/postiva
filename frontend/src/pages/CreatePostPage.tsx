import {
  ArrowLeft,
  Calendar,
  CalendarClock,
  ChevronDown,
  Clock3,
  Globe2,
  Image,
  MessageCircle,
  Monitor,
  Send,
  Share2,
  Sparkles,
  Smartphone,
  ThumbsUp,
  Video,
  X,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState, type ChangeEvent, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { FacebookBadge } from '../components/FacebookBadge'
import { PageState } from '../components/PageUi'
import { api } from '../lib/api'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { formatFileSize } from '../lib/format'
import { resolveMediaUrl } from '../lib/media'
import { usePreferences } from '../lib/preferences'

type Platform = 'FACEBOOK' | 'INSTAGRAM' | 'THREADS'

type PlatformContent = {
  title?: string
  content?: string
  caption?: string
  mediaType?: MediaType
}

type UploadedMedia = {
  id: string
  fileUrl: string
  fileName: string
  mimeType: string
  fileSize: number
}

type SocialPostTargetResponse = {
  socialAccountId: string
  platform: Platform
  scheduledPostId: string
  status: 'SCHEDULED' | 'PUBLISHING' | 'PUBLISHED' | 'FAILED' | 'CANCELLED'
  errorMessage?: string | null
}

type SocialPostResponse = {
  postId: string
  targets: SocialPostTargetResponse[]
}

type MediaType = 'NONE' | 'IMAGE' | 'VIDEO' | 'MIXED'
type PublishMode = 'NOW' | 'SCHEDULE'
const EMPTY_CHANNELS: AppChannel[] = []
const ALL_PLATFORMS: Platform[] = ['FACEBOOK', 'INSTAGRAM', 'THREADS']
const PLATFORM_LABELS: Record<Platform, string> = {
  FACEBOOK: 'Facebook',
  INSTAGRAM: 'Instagram',
  THREADS: 'Threads',
}

export function CreatePostPage() {
  const { t } = usePreferences()
  const channelsState = useAppDataResource<AppChannel[]>('/app-data/channels')
  const activeChannels = useMemo(
    () => (channelsState.data ?? EMPTY_CHANNELS).filter((channel) => channel.active),
    [channelsState.data],
  )
  const channelsByPlatform = useMemo(() => {
    const result: Record<Platform, AppChannel[]> = {
      FACEBOOK: [],
      INSTAGRAM: [],
      THREADS: [],
    }
    activeChannels.forEach((channel) => {
      const platform = getChannelPlatform(channel)
      if (platform) {
        result[platform].push(channel)
      }
    })
    return result
  }, [activeChannels])
  const availablePlatforms = useMemo(
    () => ALL_PLATFORMS.filter((platform) => channelsByPlatform[platform].length > 0),
    [channelsByPlatform],
  )
  const didInitializeTargets = useRef(false)

  const [selectedAccountIds, setSelectedAccountIds] = useState<Partial<Record<Platform, string>>>({})
  const [productName, setProductName] = useState('')
  const [productDescription, setProductDescription] = useState('')
  const [price, setPrice] = useState('')
  const [targetAudience, setTargetAudience] = useState('')
  const [highlights, setHighlights] = useState('')
  const [goal, setGoal] = useState('')
  const [selectedPlatforms, setSelectedPlatforms] = useState<Platform[]>([])
  const [sourceContent, setSourceContent] = useState('')
  const [platformCaptions, setPlatformCaptions] = useState<Partial<Record<Platform, string>>>({})
  const [platformCtas, setPlatformCtas] = useState<Partial<Record<Platform, string>>>({})
  const [platformHashtags, setPlatformHashtags] = useState<Partial<Record<Platform, string>>>({})
  const [activePreviewPlatform, setActivePreviewPlatform] = useState<Platform>('FACEBOOK')
  const [uploadedMedia, setUploadedMedia] = useState<UploadedMedia[]>([])
  const [publishMode, setPublishMode] = useState<PublishMode>('NOW')
  const [scheduledTime, setScheduledTime] = useState(function () {
    return toDateTimeLocalValue(getNextActiveTime())
  })
  const [previewDevice, setPreviewDevice] = useState<'DESKTOP' | 'MOBILE'>('DESKTOP')
  const [status, setStatus] = useState(function () {
    return t('Sẵn sàng tạo bài.', 'Ready to create a post.')
  })
  const [statusTone, setStatusTone] = useState<'info' | 'success' | 'error'>('info')
  const [isUploadingMedia, setIsUploadingMedia] = useState(false)
  const [isPublishing, setIsPublishing] = useState(false)

  useEffect(() => {
    if (channelsState.loading || didInitializeTargets.current) {
      return
    }
    const initialAccounts: Partial<Record<Platform, string>> = {}
    availablePlatforms.forEach((platform) => {
      initialAccounts[platform] = channelsByPlatform[platform][0]?.id
    })
    setSelectedAccountIds(initialAccounts)
    setSelectedPlatforms(availablePlatforms)
    setActivePreviewPlatform(availablePlatforms[0] ?? 'FACEBOOK')
    didInitializeTargets.current = true
  }, [availablePlatforms, channelsByPlatform, channelsState.loading])

  useEffect(() => {
    if (!selectedPlatforms.includes(activePreviewPlatform) && selectedPlatforms[0]) {
      setActivePreviewPlatform(selectedPlatforms[0])
    }
  }, [activePreviewPlatform, selectedPlatforms])

  const activeCaption = platformCaptions[activePreviewPlatform] ?? ''
  const activeCta = platformCtas[activePreviewPlatform] ?? ''
  const activeHashtags = platformHashtags[activePreviewPlatform] ?? ''
  const activePlatformLimit = getPlatformTextLimit(activePreviewPlatform)
  const selectedChannel = channelsByPlatform[activePreviewPlatform]
    .find((channel) => channel.id === selectedAccountIds[activePreviewPlatform])
  const hasImages = uploadedMedia.some((media) => media.mimeType.startsWith('image/'))
  const hasVideos = uploadedMedia.some((media) => media.mimeType.startsWith('video/'))
  const mediaType: MediaType = !hasImages && !hasVideos
    ? 'NONE'
    : hasImages && hasVideos ? 'MIXED' : hasVideos ? 'VIDEO' : 'IMAGE'
  const mediaUrls = uploadedMedia.map((media) => media.fileUrl)
  const actionLabel = publishMode === 'SCHEDULE' ? t('Đặt lịch', 'Schedule') : t('Tạo bài', 'Create post')
  const canPublish = Boolean(
    selectedPlatforms.length > 0
      && selectedPlatforms.every((platform) => selectedAccountIds[platform])
      && selectedPlatforms.every((platform) => (platformCaptions[platform] ?? '').trim())
      && !isUploadingMedia
      && !isPublishing,
  )

  const scheduleLabel = useMemo(() => {
    if (publishMode === 'NOW') {
      return t('Vừa xong', 'Just now')
    }
    const date = new Date(scheduledTime)
    if (Number.isNaN(date.getTime())) {
      return t('Đã lên lịch', 'Scheduled')
    }
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    }).format(date)
  }, [publishMode, scheduledTime, t])

  const previewMessage = composeMessage(activeCaption, activeCta, activeHashtags, activePreviewPlatform)

  if (channelsState.loading) {
    return <PageState title={t('Đang tải dữ liệu...', 'Loading...')} />
  }

  if (channelsState.error) {
    return <PageState title={t('Không thể tải tài khoản mạng xã hội', 'Could not load social accounts')} description={channelsState.error} />
  }

  function setError(message: string) {
    setStatusTone('error')
    setStatus(message)
  }

  function validateProductInput() {
    if (!productName.trim()) {
      setError(t('Nhập tên sản phẩm.', 'Enter a product name.'))
      return false
    }
    if (!productDescription.trim()) {
      setError(t('Nhập mô tả sản phẩm.', 'Enter a product description.'))
      return false
    }
    if (!targetAudience.trim()) {
      setError(t('Nhập khách hàng mục tiêu.', 'Enter the target audience.'))
      return false
    }
    if (!highlights.trim()) {
      setError(t('Nhập điểm nổi bật.', 'Enter the product highlights.'))
      return false
    }
    if (selectedPlatforms.length === 0) {
      setError(t('Chọn ít nhất một nền tảng.', 'Choose at least one platform.'))
      return false
    }
    return true
  }

  async function handlePublish() {
    if (!validateProductInput()) {
      return
    }
    if (selectedPlatforms.length === 0) {
      setError(t('Chọn ít nhất một nền tảng.', 'Choose at least one platform.'))
      return
    }
    const missingAccount = selectedPlatforms.find((platform) => !selectedAccountIds[platform])
    if (missingAccount) {
      setError(t(`Chọn tài khoản ${PLATFORM_LABELS[missingAccount]} trước.`, `Select a ${PLATFORM_LABELS[missingAccount]} account first.`))
      return
    }
    const missingContent = selectedPlatforms.find((platform) => !(platformCaptions[platform] ?? '').trim())
    if (missingContent) {
      setError(t(`Nhập nội dung ${PLATFORM_LABELS[missingContent]}.`, `Enter ${PLATFORM_LABELS[missingContent]} content.`))
      return
    }
    if (publishMode === 'SCHEDULE' && (!scheduledTime || new Date(scheduledTime).getTime() <= Date.now() + 30_000)) {
      setError(t('Chọn thời gian đăng trong tương lai.', 'Choose a future publishing time.'))
      return
    }
    const mediaIssue = validateMediaSelection(selectedPlatforms, uploadedMedia)
    if (mediaIssue) {
      setActivePreviewPlatform(mediaIssue.platform)
      setError(t(mediaIssue.vi, mediaIssue.en))
      return
    }

    const platformContents: Partial<Record<Platform, PlatformContent>> = {}
    for (const platform of selectedPlatforms) {
      const finalContent = composeMessage(
        platformCaptions[platform] ?? '',
        platformCtas[platform] ?? '',
        platformHashtags[platform] ?? '',
        platform,
      )
      const limit = getPlatformTextLimit(platform)
      if (finalContent.length > limit) {
        setError(t(
          `${PLATFORM_LABELS[platform]} vượt quá giới hạn ${limit.toLocaleString('vi-VN')} ký tự.`,
          `${PLATFORM_LABELS[platform]} exceeds the ${limit.toLocaleString('en-US')} character limit.`,
        ))
        setActivePreviewPlatform(platform)
        return
      }
      platformContents[platform] = {
        title: productName.trim() || t('Bài đăng mới', 'New post'),
        ...(platform === 'INSTAGRAM' ? { caption: finalContent } : { content: finalContent }),
        mediaType,
      }
    }

    setIsPublishing(true)
    setStatusTone('info')
    setStatus(publishMode === 'SCHEDULE' ? t('Đang đặt lịch...', 'Scheduling...') : t('Đang tạo bài...', 'Creating post...'))

    try {
      const response = await api.post<SocialPostResponse>('/social-posts', {
        title: productName.trim(),
        targets: selectedPlatforms.map((platform) => ({
          platform,
          socialAccountId: selectedAccountIds[platform],
        })),
        platformContents,
        hashtags: uniqueHashtags(selectedPlatforms.flatMap((platform) => parseHashtags(platformHashtags[platform] ?? ''))),
        cta: selectedPlatforms.map((platform) => platformCtas[platform]?.trim()).find(Boolean) ?? '',
        mediaIds: uploadedMedia.map((media) => media.id),
        scheduledTime: publishMode === 'SCHEDULE' ? new Date(scheduledTime).toISOString() : undefined,
      })
      const failed = response.data.targets.filter((target) => target.status === 'FAILED')
      const completed = response.data.targets.filter((target) => target.status !== 'FAILED')
      if (failed.length > 0) {
        setStatusTone(completed.length > 0 ? 'info' : 'error')
        setStatus(formatTargetResults(response.data.targets, publishMode, t))
      } else {
        setStatusTone('success')
        setStatus(formatTargetResults(response.data.targets, publishMode, t))
      }
    } catch (error) {
      setError(getErrorMessage(error, t('Không thể đăng bài.', 'Could not publish the post.')))
    } finally {
      setIsPublishing(false)
    }
  }

  async function handleMediaFileChange(event: ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files ?? [])
    if (files.length === 0) {
      return
    }
    const hasVideo = files.some((file) => file.type.startsWith('video/'))
    const hasImage = files.some((file) => file.type.startsWith('image/'))
    const hasOnlySupportedTypes = files.every((file) => file.type.startsWith('image/') || file.type.startsWith('video/'))
    if (!hasOnlySupportedTypes) {
      setError(t('Chỉ hỗ trợ file ảnh hoặc video.', 'Only image or video files are supported.'))
      event.target.value = ''
      return
    }
    if (uploadedMedia.length + files.length > 20) {
      setError(t('Mỗi bài tối đa 20 file media.', 'Select up to 20 media files.'))
      event.target.value = ''
      return
    }

    setIsUploadingMedia(true)
    setStatusTone('info')
    setStatus(hasVideo && hasImage
      ? t('Đang tải ảnh và video...', 'Uploading images and videos...')
      : hasVideo ? t('Đang tải video...', 'Uploading videos...') : t('Đang tải ảnh...', 'Uploading images...'))
    try {
      const uploaded = await Promise.all(files.map(async (file) => {
        const formData = new FormData()
        formData.append('file', file)
        const response = await api.post<UploadedMedia>('/files/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
        return response.data
      }))
      setUploadedMedia((current) => [...current, ...uploaded])
      setStatusTone('success')
      setStatus(t(`Đã tải ${uploaded.length} file media.`, `${uploaded.length} media file(s) uploaded.`))
    } catch (error) {
      setError(getErrorMessage(error, t('Không thể tải file media.', 'Could not upload media.')))
    } finally {
      setIsUploadingMedia(false)
      event.target.value = ''
    }
  }

  function removeMedia(mediaId: string) {
    setUploadedMedia((current) => current.filter((media) => media.id !== mediaId))
  }

  function applyPlatformTemplates() {
    if (selectedPlatforms.length === 0) {
      setError(t('Chọn ít nhất một nền tảng.', 'Choose at least one platform.'))
      return
    }
    if (!sourceContent.trim()) {
      setError(t('Nhập nội dung gốc trước.', 'Enter source content first.'))
      return
    }

    const nextCaptions: Partial<Record<Platform, string>> = {}
    const nextCtas: Partial<Record<Platform, string>> = {}
    const nextHashtags: Partial<Record<Platform, string>> = {}
    selectedPlatforms.forEach((platform) => {
      const draft = buildPlatformDraft(platform, {
        sourceContent,
        productName,
        price,
        highlights,
        goal,
      })
      nextCaptions[platform] = draft.content
      nextCtas[platform] = draft.cta
      nextHashtags[platform] = draft.hashtags
    })

    setPlatformCaptions((current) => ({ ...current, ...nextCaptions }))
    setPlatformCtas((current) => ({ ...current, ...nextCtas }))
    setPlatformHashtags((current) => ({ ...current, ...nextHashtags }))
    setActivePreviewPlatform(selectedPlatforms[0] ?? 'FACEBOOK')
    setStatusTone('success')
    setStatus(t('Đã tạo nội dung theo từng nền tảng.', 'Platform-specific content generated.'))
  }

  function togglePlatform(platform: Platform) {
    if (!availablePlatforms.includes(platform)) {
      setError(t(
        `Hãy kết nối ${PLATFORM_LABELS[platform]} trước.`,
        `Connect ${PLATFORM_LABELS[platform]} first.`,
      ))
      return
    }
    const isRemoving = selectedPlatforms.includes(platform)
    setSelectedPlatforms((current) => {
      if (current.includes(platform)) {
        return current.filter((value) => value !== platform)
      }
      return [...current, platform]
    })
    if (!isRemoving && !selectedAccountIds[platform]) {
      setSelectedAccountIds((current) => ({
        ...current,
        [platform]: channelsByPlatform[platform][0]?.id,
      }))
    }
    if (!isRemoving && !(platformCaptions[platform] ?? '').trim()) {
      const fallbackCaption = sourceContent
        || platformCaptions[activePreviewPlatform]
        || Object.values(platformCaptions).find((value) => value?.trim())
        || ''
      setPlatformCaptions((current) => ({ ...current, [platform]: fallbackCaption }))
    }
    if (!isRemoving) {
      setPlatformCtas((current) => ({
        ...current,
        [platform]: platform === 'THREADS' ? '' : current[activePreviewPlatform] ?? '',
      }))
      setPlatformHashtags((current) => ({
        ...current,
        [platform]: (current[activePreviewPlatform] ?? '')
          .split(/[\s,]+/)
          .slice(0, getPlatformHashtagLimit(platform))
          .join(' '),
      }))
    }
    if (!isRemoving) {
      setActivePreviewPlatform(platform)
    }
  }

  return (
    <section className="create-post-page -mx-4 min-h-[calc(100svh-56px)] rounded-[8px] bg-gradient-to-br from-rose-50 via-violet-50 to-cyan-50 px-4 py-3">
      <div className="mb-5 flex items-center justify-between gap-4">
        <div className="flex items-center gap-4">
          <Link to="/app" className="flex h-9 w-9 items-center justify-center rounded-full border border-slate-200 bg-white text-slate-700">
            <ArrowLeft size={18} />
          </Link>
          <h1 className="text-2xl font-bold tracking-tight">{t('Tạo bài đăng', 'Create post')}</h1>
        </div>
        <div className="flex overflow-hidden rounded-[6px] border border-slate-300 bg-white">
          <button
            type="button"
            onClick={() => setPreviewDevice('DESKTOP')}
            className={cn('flex h-11 w-11 items-center justify-center', previewDevice === 'DESKTOP' ? 'bg-slate-100 text-slate-900' : 'text-slate-500')}
            aria-label={t('Xem desktop', 'Desktop preview')}
          >
            <Monitor size={18} />
          </button>
          <button
            type="button"
            onClick={() => setPreviewDevice('MOBILE')}
            className={cn('flex h-11 w-11 items-center justify-center border-l border-slate-300', previewDevice === 'MOBILE' ? 'bg-slate-100 text-slate-900' : 'text-slate-500')}
            aria-label={t('Xem mobile', 'Mobile preview')}
          >
            <Smartphone size={18} />
          </button>
        </div>
      </div>

      <div className="grid gap-8 lg:grid-cols-[minmax(0,520px)_minmax(420px,1fr)]">
        <div className="overflow-hidden rounded-[8px] border border-slate-200 bg-white shadow-xl shadow-slate-200/70">
          <div className="max-h-[calc(100svh-190px)] space-y-4 overflow-auto px-5 py-5">
            <ComposerSection title={t('Đăng lên', 'Post to')}>
              <PlatformPicker
                selected={selectedPlatforms}
                available={availablePlatforms}
                notConnectedLabel={t('Chưa kết nối', 'Not connected')}
                onToggle={togglePlatform}
              />
              {selectedPlatforms.length === 0 ? (
                <p className="rounded-[6px] border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-700">
                  {t('Chọn ít nhất một nền tảng.', 'Choose at least one platform.')}
                </p>
              ) : (
                <div className="mt-3 space-y-2">
                  {selectedPlatforms.map((platform) => (
                    <label key={platform} className="grid grid-cols-[92px_minmax(0,1fr)] items-center gap-3">
                      <span className="text-sm font-bold" style={{ color: getPlatformColor(platform) }}>
                        {PLATFORM_LABELS[platform]}
                      </span>
                      <div className="relative">
                        <select
                          value={selectedAccountIds[platform] ?? ''}
                          onChange={(event) => setSelectedAccountIds((current) => ({
                            ...current,
                            [platform]: event.target.value,
                          }))}
                          className="h-11 w-full appearance-none rounded-[6px] border border-slate-300 bg-white px-3 pr-9 text-sm font-semibold outline-none focus:border-blue-500"
                        >
                          {channelsByPlatform[platform].map((channel) => (
                            <option key={channel.id} value={channel.id}>{channel.name}</option>
                          ))}
                        </select>
                        <ChevronDown className="pointer-events-none absolute right-3 top-3.5 text-slate-700" size={18} />
                      </div>
                    </label>
                  ))}
                </div>
              )}
            </ComposerSection>

            <ComposerSection title={t('Thông tin sản phẩm', 'Product information')}>
              <div className="grid gap-3 sm:grid-cols-2">
                <TextField label={t('Tên sản phẩm *', 'Product name *')} value={productName} onChange={setProductName} className="sm:col-span-2" />
                <TextAreaField label={t('Mô tả *', 'Description *')} value={productDescription} onChange={setProductDescription} rows={3} className="sm:col-span-2" />
                <TextField label={t('Giá', 'Price')} value={price} onChange={setPrice} />
                <TextField label={t('Khách hàng mục tiêu *', 'Target audience *')} value={targetAudience} onChange={setTargetAudience} />
                <TextAreaField label={t('Điểm nổi bật *', 'Highlights *')} value={highlights} onChange={setHighlights} rows={2} />
                <TextAreaField label={t('Mục tiêu', 'Goal')} value={goal} onChange={setGoal} rows={2} />
              </div>
            </ComposerSection>

            <ComposerSection title={t('Ảnh/video sản phẩm', 'Product image/video')}>
              <div className="flex flex-wrap items-center gap-3">
                <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-[6px] border border-slate-300 bg-white px-4 text-sm font-semibold hover:bg-slate-50">
                  <Image size={17} />
                  {t('Thêm ảnh/video', 'Add image/video')}
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm"
                    multiple
                    onChange={(event) => void handleMediaFileChange(event)}
                    disabled={isUploadingMedia}
                    className="hidden"
                  />
                </label>
                {uploadedMedia.length > 0 && (
                  <span className="text-xs text-slate-500">{uploadedMedia.length}/20 media</span>
                )}
              </div>
              {uploadedMedia.length > 0 && (
                <div className="mt-3 space-y-2">
                  {uploadedMedia.map((media) => (
                    <div key={media.id} className="flex items-center justify-between gap-3 rounded-[6px] border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                      <span className="flex min-w-0 items-center gap-2 truncate font-semibold">
                        {media.mimeType.startsWith('video/') ? <Video size={15} /> : <Image size={15} />}
                        <span className="truncate">{media.fileName} ({formatFileSize(media.fileSize)})</span>
                      </span>
                      <button type="button" onClick={() => removeMedia(media.id)} className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[6px] hover:bg-emerald-100" aria-label={t('Xóa ảnh', 'Remove image')}>
                        <X size={15} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </ComposerSection>

            <ComposerSection
              title={t('Nội dung', 'Content')}
              headerAction={selectedPlatforms.length > 1 ? (
                <div className="flex overflow-hidden rounded-[6px] border border-slate-200 bg-slate-50 p-0.5">
                  {selectedPlatforms.map((platform) => (
                    <button
                      key={platform}
                      type="button"
                      onClick={() => setActivePreviewPlatform(platform)}
                      className={cn(
                        'h-8 px-3 text-xs font-bold transition',
                        activePreviewPlatform === platform
                          ? 'rounded-[5px] bg-white text-violet-700 shadow-sm'
                          : 'text-slate-500 hover:text-slate-800',
                      )}
                    >
                      {PLATFORM_LABELS[platform]}
                    </button>
                  ))}
                </div>
              ) : undefined}
            >
              <label className="block text-sm font-bold">
                {t('Nội dung chính', 'Main content')}
                <textarea
                  value={sourceContent}
                  onChange={(event) => setSourceContent(event.target.value)}
                  className="mt-2 h-24 w-full resize-none rounded-[6px] border border-slate-300 bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-blue-500"
                  placeholder={t('Nhập nội dung chính...', 'Enter the main content...')}
                />
              </label>
              <div className="mt-3 flex justify-end">
                <button
                  type="button"
                  onClick={applyPlatformTemplates}
                  className="inline-flex h-10 items-center justify-center gap-2 rounded-[6px] bg-violet-600 px-4 text-sm font-bold text-white hover:bg-violet-700"
                >
                  <Sparkles size={16} />
                  {t('Tự tối ưu', 'Optimize')}
                </button>
              </div>
              <div className="my-4 border-t border-slate-100" />
              <label className="block text-sm font-bold">
                {t(`Bản ${PLATFORM_LABELS[activePreviewPlatform]}`, `${PLATFORM_LABELS[activePreviewPlatform]} version`)}
                <textarea
                  value={activeCaption}
                  onChange={(event) => setPlatformCaptions((current) => ({
                    ...current,
                    [activePreviewPlatform]: event.target.value,
                  }))}
                  disabled={!selectedPlatforms.includes(activePreviewPlatform)}
                  className="mt-2 h-28 w-full resize-none rounded-[6px] border border-slate-300 bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-blue-500"
                  placeholder={getPlatformPlaceholder(activePreviewPlatform, t)}
                />
                <span className={cn(
                  'mt-1 block text-right text-xs font-medium',
                  previewMessage.length > activePlatformLimit ? 'text-rose-600' : 'text-slate-400',
                )}>
                  {previewMessage.length.toLocaleString('vi-VN')}/{activePlatformLimit.toLocaleString('vi-VN')}
                </span>
              </label>
            </ComposerSection>

            <ComposerSection
              title={t('Lịch đăng', 'Schedule')}
              headerAction={<Toggle checked={publishMode === 'SCHEDULE'} onChange={(checked) => setPublishMode(checked ? 'SCHEDULE' : 'NOW')} />}
            >
              {publishMode === 'SCHEDULE' && (
                <div className="grid gap-3 sm:grid-cols-[1fr_130px]">
                  <label className="flex h-10 items-center gap-2 rounded-[6px] border border-slate-300 bg-white px-3">
                    <Calendar size={17} />
                    <input
                      type="datetime-local"
                      value={scheduledTime}
                      min={toDateTimeLocalValue(new Date(Date.now() + 60_000))}
                      onChange={(event) => setScheduledTime(event.target.value)}
                      className="min-w-0 flex-1 bg-transparent text-sm outline-none"
                    />
                  </label>
                  <div className="flex h-10 items-center gap-2 rounded-[6px] border border-slate-300 bg-white px-3 text-sm">
                    <Clock3 size={17} />
                    {formatTimeOnly(scheduledTime)}
                  </div>
                </div>
              )}
            </ComposerSection>

            <p className={cn('rounded-[6px] border px-3 py-2 text-sm', statusTone === 'success' ? 'border-emerald-200 bg-emerald-50 text-emerald-700' : statusTone === 'error' ? 'border-rose-200 bg-rose-50 text-rose-700' : 'border-slate-200 bg-slate-50 text-slate-700')}>
              {status}
            </p>
          </div>

          <div className="flex items-center justify-end gap-2 border-t border-slate-200 bg-white px-5 py-4">
            <Link to="/app" className="inline-flex h-10 items-center justify-center rounded-[6px] border border-slate-300 px-4 text-sm font-semibold hover:bg-slate-50">
              {t('Hủy', 'Cancel')}
            </Link>
            <button
              type="button"
              onClick={() => void handlePublish()}
              disabled={!canPublish}
              className="inline-flex h-10 items-center justify-center gap-2 rounded-[6px] bg-blue-600 px-5 text-sm font-bold text-white hover:bg-blue-700 disabled:bg-blue-200"
            >
              {publishMode === 'SCHEDULE' ? <CalendarClock size={16} /> : <Send size={16} />}
              {isPublishing ? t('Đang xử lý...', 'Working...') : actionLabel}
            </button>
          </div>
        </div>

        <div className="min-w-0">
          <div className="mb-3 flex flex-wrap justify-center gap-2">
            {selectedPlatforms.map((platform) => (
              <button
                key={platform}
                type="button"
                onClick={() => setActivePreviewPlatform(platform)}
                className={cn(
                  'rounded-full border bg-white px-3 py-1.5 text-xs font-bold shadow-sm',
                  activePreviewPlatform === platform ? 'border-violet-400 text-violet-700' : 'border-slate-200 text-slate-500',
                )}
              >
                {PLATFORM_LABELS[platform]}
              </button>
            ))}
          </div>
          <SocialFeedPreview
            platform={activePreviewPlatform}
            caption={previewMessage}
            channel={selectedChannel}
            mediaUrls={mediaUrls}
            mediaTypes={uploadedMedia.map((media) => media.mimeType)}
            scheduleLabel={scheduleLabel}
            compact={previewDevice === 'MOBILE'}
            labels={{
              account: PLATFORM_LABELS[activePreviewPlatform],
              like: t('Thích', 'Like'),
              comment: t('Bình luận', 'Comment'),
              share: t('Chia sẻ', 'Share'),
            }}
          />
        </div>
      </div>
    </section>
  )
}

function ComposerSection({
  title,
  headerAction,
  children,
}: {
  title: string
  headerAction?: ReactNode
  children: ReactNode
}) {
  return (
    <section className="rounded-[6px] border border-slate-100 bg-white p-4 shadow-sm">
      <div className="mb-3 flex items-center justify-between gap-3">
        <h2 className="text-lg font-bold">{title}</h2>
        {headerAction}
      </div>
      {children}
    </section>
  )
}

function TextField({
  label,
  value,
  onChange,
  className = '',
}: {
  label: string
  value: string
  onChange: (value: string) => void
  className?: string
}) {
  return (
    <label className={cn('block text-sm font-semibold', className)}>
      {label}
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        className="mt-1.5 h-10 w-full rounded-[6px] border border-slate-300 bg-white px-3 text-sm font-normal outline-none focus:border-blue-500"
      />
    </label>
  )
}

function TextAreaField({
  label,
  value,
  onChange,
  rows,
  className = '',
}: {
  label: string
  value: string
  onChange: (value: string) => void
  rows: number
  className?: string
}) {
  return (
    <label className={cn('block text-sm font-semibold', className)}>
      {label}
      <textarea
        value={value}
        onChange={(event) => onChange(event.target.value)}
        rows={rows}
        className="mt-1.5 w-full resize-none rounded-[6px] border border-slate-300 bg-white px-3 py-2 text-sm font-normal leading-5 outline-none focus:border-blue-500"
      />
    </label>
  )
}

function PlatformPicker({
  selected,
  available,
  notConnectedLabel,
  onToggle,
}: {
  selected: Platform[]
  available: Platform[]
  notConnectedLabel: string
  onToggle: (platform: Platform) => void
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {ALL_PLATFORMS.map((platform) => (
        <label key={platform} className={cn(
          'inline-flex items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition',
          !available.includes(platform)
            ? 'cursor-not-allowed border-slate-100 bg-slate-50 text-slate-300'
            : selected.includes(platform)
              ? 'cursor-pointer border-violet-300 bg-violet-50 text-violet-700'
              : 'cursor-pointer border-slate-200 bg-white text-slate-500 hover:border-violet-200',
        )}>
          <input
            type="checkbox"
            checked={selected.includes(platform)}
            onChange={() => onToggle(platform)}
            disabled={!available.includes(platform)}
            className="sr-only"
          />
          {PLATFORM_LABELS[platform]}
          {!available.includes(platform) && ` (${notConnectedLabel})`}
        </label>
      ))}
    </div>
  )
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <button
      type="button"
      onClick={() => onChange(!checked)}
      aria-pressed={checked}
      className={cn('relative h-7 w-12 rounded-full border transition', checked ? 'border-blue-500 bg-blue-500' : 'border-slate-300 bg-white')}
    >
      <span className={cn('absolute inset-y-0 left-0.5 my-auto h-5 w-5 rounded-full shadow-sm transition', checked ? 'translate-x-5 bg-white' : 'translate-x-0 bg-slate-700')} />
    </button>
  )
}

function SocialFeedPreview({
  platform,
  channel,
  caption,
  mediaUrls,
  mediaTypes,
  scheduleLabel,
  compact,
  labels,
}: {
  platform: Platform
  channel?: AppChannel
  caption: string
  mediaUrls: string[]
  mediaTypes: string[]
  scheduleLabel: string
  compact: boolean
  labels: {
    account: string
    like: string
    comment: string
    share: string
  }
}) {
  const previewMedia = mediaUrls
    .map((url, index) => ({ url: resolveMediaUrl(url), type: mediaTypes[index] ?? '' }))
    .filter((media): media is { url: string; type: string } => Boolean(media.url))
  const visiblePreviewMedia = previewMedia.slice(0, 4)
  const hiddenImageCount = Math.max(0, previewMedia.length - visiblePreviewMedia.length)

  return (
    <article
      className={cn('mx-auto overflow-hidden rounded-[6px] border-t-4 bg-white shadow-xl shadow-slate-200/80', compact ? 'max-w-[360px]' : 'max-w-[620px]')}
      style={{ borderTopColor: getPlatformColor(platform) }}
    >
      <header className="flex items-center gap-3 px-5 py-4">
        <PageIdentity channel={channel} platform={platform} large />
        <div className="min-w-0">
          <p className="truncate text-sm font-bold">{channel?.name ?? labels.account}</p>
          <p className="flex items-center gap-1 text-xs text-slate-500">
            {scheduleLabel}
            <Globe2 size={12} />
          </p>
        </div>
      </header>

      {caption.trim() ? (
        <p className="whitespace-pre-wrap px-5 pb-4 text-sm leading-6 text-slate-900">{caption}</p>
      ) : (
        <div className="space-y-3 px-5 pb-5">
          <div className="h-3 w-full rounded-full bg-slate-100" />
          <div className="h-3 w-1/3 rounded-full bg-slate-100" />
        </div>
      )}

      <div className={cn('bg-slate-100', previewMedia.length > 1 ? '' : 'flex min-h-[430px] items-center justify-center')}>
        {previewMedia.length > 0 ? (
          <div className={cn('grid w-full', visiblePreviewMedia.length > 1 ? 'grid-cols-2 gap-0.5' : 'grid-cols-1')}>
            {visiblePreviewMedia.map((media, index) => (
              <div key={media.url + '-' + index} className="relative">
                {media.type.startsWith('video/') ? (
                  <video src={media.url} controls className="mx-auto max-h-[430px] w-full bg-slate-950 object-contain" />
                ) : (
                  <img
                    src={media.url}
                    alt={`${PLATFORM_LABELS[platform]} post media preview ${index + 1}`}
                    className="mx-auto h-auto max-h-[430px] w-full object-contain"
                  />
                )}
                {index === visiblePreviewMedia.length - 1 && hiddenImageCount > 0 && (
                  <div className="absolute inset-0 flex items-center justify-center bg-slate-950/60 text-4xl font-bold text-white">
                    +{hiddenImageCount}
                  </div>
                )}
              </div>
            ))}
          </div>
        ) : (
          <EmptyMediaPlaceholder />
        )}
      </div>

      <footer className="grid grid-cols-3 border-t border-slate-100 px-6 py-4 text-sm font-semibold text-slate-500">
        <button type="button" className="flex items-center justify-center gap-2">
          <ThumbsUp size={20} />
          {labels.like}
        </button>
        <button type="button" className="flex items-center justify-center gap-2">
          <MessageCircle size={20} />
          {labels.comment}
        </button>
        <button type="button" className="flex items-center justify-center gap-2">
          <Share2 size={20} />
          {labels.share}
        </button>
      </footer>
    </article>
  )
}

function EmptyMediaPlaceholder() {
  return (
    <div className="flex h-56 w-72 items-center justify-center border-[6px] border-dashed border-slate-200 text-slate-200">
      <Image size={120} strokeWidth={1.2} />
    </div>
  )
}

function PageIdentity({
  channel,
  platform,
  large = false,
}: {
  channel?: AppChannel
  platform: Platform
  large?: boolean
}) {
  const initial = getPageInitial(channel?.name ?? PLATFORM_LABELS[platform])
  return (
    <span className={cn('relative flex shrink-0 items-center justify-center rounded-full border border-amber-200 bg-amber-50 font-bold leading-none text-amber-600', large ? 'h-12 w-12 text-base' : 'h-9 w-9 text-sm')}>
      {initial}
      {platform === 'FACEBOOK' ? (
        <FacebookBadge className="absolute -bottom-1 -right-1 h-[18px] w-[18px] rounded-full ring-2 ring-white" />
      ) : (
        <span
          className="absolute -bottom-1 -right-1 flex h-[18px] w-[18px] items-center justify-center rounded-full text-[8px] font-black text-white ring-2 ring-white"
          style={{ background: getPlatformColor(platform) }}
        >
          {platform === 'INSTAGRAM' ? 'IG' : '@'}
        </span>
      )}
    </span>
  )
}

function validateMediaSelection(platforms: Platform[], media: UploadedMedia[]) {
  for (const platform of platforms) {
    if (platform === 'FACEBOOK') {
      if (media.length > 10) {
        return {
          platform,
          vi: 'Facebook chỉ hỗ trợ tối đa 10 ảnh trong một bài.',
          en: 'Facebook supports up to 10 images in one post.',
        }
      }
      if (media.length > 1 && media.some((item) => item.mimeType.startsWith('video/'))) {
        return {
          platform,
          vi: 'Facebook chỉ cho phép nhiều ảnh hoặc một video; không thể trộn ảnh và video.',
          en: 'Facebook supports multiple images or one video; images and videos cannot be mixed.',
        }
      }
    }

    if (platform === 'INSTAGRAM') {
      if (media.length === 0) {
        return {
          platform,
          vi: 'Instagram cần ít nhất một ảnh hoặc video.',
          en: 'Instagram requires at least one image or video.',
        }
      }
      if (media.length > 10) {
        return {
          platform,
          vi: 'Instagram carousel chỉ hỗ trợ tối đa 10 mục.',
          en: 'Instagram carousels support up to 10 items.',
        }
      }
      if (media.some((item) => item.mimeType.startsWith('image/') && item.mimeType !== 'image/jpeg')) {
        return {
          platform,
          vi: 'Instagram Content Publishing chỉ hỗ trợ ảnh JPEG.',
          en: 'Instagram Content Publishing only supports JPEG images.',
        }
      }
      if (media.some((item) => item.mimeType.startsWith('video/') && !isMetaVideo(item))) {
        return {
          platform,
          vi: 'Video Instagram phải là MP4 hoặc MOV.',
          en: 'Instagram videos must use MP4 or MOV.',
        }
      }
    }

    if (platform === 'THREADS') {
      if (media.length > 20) {
        return {
          platform,
          vi: 'Threads carousel chỉ hỗ trợ tối đa 20 mục.',
          en: 'Threads carousels support up to 20 items.',
        }
      }
      if (media.some((item) => item.mimeType.startsWith('video/') && !isMetaVideo(item))) {
        return {
          platform,
          vi: 'Video Threads phải là MP4 hoặc MOV.',
          en: 'Threads videos must use MP4 or MOV.',
        }
      }
    }
  }
  return null
}

function isMetaVideo(media: UploadedMedia) {
  return media.mimeType === 'video/mp4' || media.mimeType === 'video/quicktime'
}

function composeMessage(caption: string, cta: string, hashtagsText: string, platform: Platform) {
  const base = caption.trim()
  const callToAction = platform === 'THREADS' ? '' : cta.trim()
  const hashtagLimit = platform === 'INSTAGRAM' ? 10 : platform === 'FACEBOOK' ? 3 : 1
  const hashtags = parseHashtags(hashtagsText).slice(0, hashtagLimit)
  const parts = [base]
  if (callToAction && !base.includes(callToAction)) {
    parts.push(callToAction)
  }
  if (hashtags.length > 0 && !hashtags.every((hashtag) => base.includes(hashtag))) {
    parts.push(hashtags.join(' '))
  }
  return parts.filter(Boolean).join('\n\n')
}

type PlatformDraftInput = {
  sourceContent: string
  productName: string
  price: string
  highlights: string
  goal: string
}

function buildPlatformDraft(platform: Platform, input: PlatformDraftInput) {
  const source = normalizeMultiline(input.sourceContent)
  const highlight = normalizeMultiline(input.highlights)
  const price = input.price.trim()
  const goal = input.goal.trim()
  const product = input.productName.trim()
  const hashtags = buildPlatformHashtags(platform, input)

  if (platform === 'FACEBOOK') {
    return {
      content: compactBlocks([
        source,
        highlight ? `Điểm nổi bật: ${highlight}` : '',
        price ? `Giá: ${price}` : '',
      ]),
      cta: goal || 'Nhắn tin để được tư vấn thêm.',
      hashtags,
    }
  }

  if (platform === 'INSTAGRAM') {
    return {
      content: compactBlocks([
        product ? `${product}` : '',
        source,
        highlight,
      ]),
      cta: goal || 'Lưu lại hoặc nhắn tin để biết thêm chi tiết.',
      hashtags,
    }
  }

  return {
    content: compactBlocks([
      source,
      product ? `Bạn nghĩ sao về ${product}?` : 'Bạn nghĩ sao?',
    ]),
    cta: '',
    hashtags,
  }
}

function buildPlatformHashtags(platform: Platform, input: PlatformDraftInput) {
  const limit = getPlatformHashtagLimit(platform)
  const candidates = [
    input.productName,
    ...input.highlights.split(/[,;\n]+/),
    ...input.sourceContent.split(/\s+/).filter((word) => word.length >= 5).slice(0, 4),
  ]
  return uniqueHashtags(candidates
    .map(toHashtag)
    .filter(Boolean))
    .slice(0, limit)
    .join(' ')
}

function toHashtag(value: string) {
  const normalized = value
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/đ/g, 'd')
    .replace(/Đ/g, 'D')
    .replace(/[^a-zA-Z0-9]+/g, '')
  return normalized ? `#${normalized}` : ''
}

function compactBlocks(values: string[]) {
  return values
    .map(normalizeMultiline)
    .filter(Boolean)
    .join('\n\n')
}

function normalizeMultiline(value: string) {
  return value
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .join('\n')
}

function parseHashtags(value: string) {
  return value
    .split(/[\s,]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => item.startsWith('#') ? item : '#' + item.replace(/^#+/, ''))
}

function uniqueHashtags(values: string[]) {
  return Array.from(new Set(values))
}

function getChannelPlatform(channel: AppChannel): Platform | null {
  const value = `${channel.badge} ${channel.type}`.toUpperCase()
  if (value.includes('FACEBOOK')) {
    return 'FACEBOOK'
  }
  if (value.includes('INSTAGRAM')) {
    return 'INSTAGRAM'
  }
  if (value.includes('THREADS')) {
    return 'THREADS'
  }
  return null
}

function getPlatformColor(platform: Platform) {
  switch (platform) {
    case 'FACEBOOK':
      return '#1877F2'
    case 'INSTAGRAM':
      return '#E1306C'
    case 'THREADS':
      return '#111827'
  }
}

function getPlatformTextLimit(platform: Platform) {
  switch (platform) {
    case 'FACEBOOK':
      return 63_206
    case 'INSTAGRAM':
      return 2_200
    case 'THREADS':
      return 500
  }
}

function getPlatformHashtagLimit(platform: Platform) {
  switch (platform) {
    case 'FACEBOOK':
      return 3
    case 'INSTAGRAM':
      return 10
    case 'THREADS':
      return 1
  }
}

function getPlatformPlaceholder(
  platform: Platform,
  t: (vietnamese: string, english: string) => string,
) {
  switch (platform) {
    case 'FACEBOOK':
      return t('Nhập nội dung Facebook...', 'Write Facebook content...')
    case 'INSTAGRAM':
      return t('Nhập caption Instagram...', 'Write Instagram caption...')
    case 'THREADS':
      return t('Nhập nội dung Threads...', 'Write Threads content...')
  }
}

function formatTargetResults(
  targets: SocialPostTargetResponse[],
  mode: PublishMode,
  t: (vietnamese: string, english: string) => string,
) {
  return targets.map((target) => {
    const label = PLATFORM_LABELS[target.platform]
    if (target.status === 'FAILED') {
      return `${label}: ${target.errorMessage || t('đăng thất bại', 'publish failed')}`
    }
    if (target.status === 'PUBLISHED') {
      return `${label}: ${t('đã đăng', 'published')}`
    }
    if (target.status === 'SCHEDULED' || mode === 'SCHEDULE') {
      return `${label}: ${t('đã đặt lịch', 'scheduled')}`
    }
    return `${label}: ${t('đang xử lý', 'processing')}`
  }).join(' • ')
}

function cn(...values: Array<string | false | undefined>) {
  return values.filter(Boolean).join(' ')
}

function getPageInitial(value: string) {
  return value.trim().charAt(0).toUpperCase() || 'F'
}

function toDateTimeLocalValue(date: Date) {
  const offsetDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000)
  return offsetDate.toISOString().slice(0, 16)
}

function getNextActiveTime() {
  const next = new Date(Date.now() + 60 * 60 * 1000)
  next.setMinutes(0, 0, 0)
  return next
}

function formatTimeOnly(value: string) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return '--:--'
  }
  return new Intl.DateTimeFormat('vi-VN', {
    hour: '2-digit',
    minute: '2-digit',
  }).format(date)
}
