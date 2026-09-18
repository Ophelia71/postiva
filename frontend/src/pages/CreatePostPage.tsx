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
  X,
} from 'lucide-react'
import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent, type ReactNode } from 'react'
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
}

type GeneratedPostResponse = {
  postId: string
  briefId: string
  title: string
  platformContents: Partial<Record<Platform, PlatformContent>>
  hashtags: string[]
  cta: string
  imageSuggestion: string
  imageUrl?: string
  status: string
}

type UploadedMedia = {
  id: string
  fileUrl: string
  fileName: string
  mimeType: string
  fileSize: number
}

type FacebookBusinessPostResponse = {
  postId: string
  scheduledPostId: string
  status: string
}

type MediaType = 'NONE' | 'IMAGE'
type PublishMode = 'NOW' | 'SCHEDULE'
type AiTone = 'FRIENDLY' | 'PROFESSIONAL' | 'PERSUASIVE' | 'CASUAL' | 'INSPIRATIONAL' | 'HUMOROUS'

type AiChatMessage = {
  id: string
  role: 'user' | 'assistant'
  content: string
}

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
  const facebookChannels = (channelsState.data ?? EMPTY_CHANNELS).filter((channel) => channel.badge === 'Facebook' && channel.active)

  const [selectedChannelId, setSelectedChannelId] = useState('')
  const [productName, setProductName] = useState('')
  const [productDescription, setProductDescription] = useState('')
  const [price, setPrice] = useState('')
  const [targetAudience, setTargetAudience] = useState('')
  const [highlights, setHighlights] = useState('')
  const [goal, setGoal] = useState('Giới thiệu sản phẩm và thúc đẩy khách hàng hành động')
  const [selectedPlatforms, setSelectedPlatforms] = useState<Platform[]>(ALL_PLATFORMS)
  const [caption, setCaption] = useState('')
  const [cta, setCta] = useState('')
  const [hashtagsText, setHashtagsText] = useState('')
  const [aiTopic, setAiTopic] = useState('')
  const [aiTone, setAiTone] = useState<AiTone>('FRIENDLY')
  const [aiMessages, setAiMessages] = useState<AiChatMessage[]>([])
  const [uploadedMedia, setUploadedMedia] = useState<UploadedMedia[]>([])
  const [publishMode, setPublishMode] = useState<PublishMode>('NOW')
  const [scheduledTime, setScheduledTime] = useState(function () {
    return toDateTimeLocalValue(getNextActiveTime())
  })
  const [previewDevice, setPreviewDevice] = useState<'DESKTOP' | 'MOBILE'>('DESKTOP')
  const [generatedPost, setGeneratedPost] = useState<GeneratedPostResponse | null>(null)
  const [status, setStatus] = useState(function () {
    return t('Sẵn sàng tạo bài.', 'Ready to create a post.')
  })
  const [statusTone, setStatusTone] = useState<'info' | 'success' | 'error'>('info')
  const [isGenerating, setIsGenerating] = useState(false)
  const [isUploadingMedia, setIsUploadingMedia] = useState(false)
  const [isPublishing, setIsPublishing] = useState(false)

  useEffect(() => {
    if (!selectedChannelId && facebookChannels[0]) {
      setSelectedChannelId(facebookChannels[0].id)
    }
  }, [facebookChannels, selectedChannelId])

  const selectedChannel = facebookChannels.find((channel) => channel.id === selectedChannelId)
  const mediaType: MediaType = uploadedMedia.length > 0 ? 'IMAGE' : 'NONE'
  const mediaUrls = uploadedMedia.map((media) => media.fileUrl)
  const actionLabel = publishMode === 'SCHEDULE' ? t('Đặt lịch', 'Schedule') : t('Tạo bài', 'Create post')
  const canPublish = Boolean(
    selectedChannel
      && caption.trim()
      && selectedPlatforms.includes('FACEBOOK')
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

  const previewMessage = composeMessage(caption, cta, hashtagsText)

  if (channelsState.loading) {
    return <PageState title={t('Đang tải dữ liệu...', 'Loading...')} />
  }

  if (channelsState.error) {
    return <PageState title={t('Không thể tải Facebook Page', 'Could not load Facebook Pages')} description={channelsState.error} />
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

  async function handleGenerate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!validateProductInput()) {
      return
    }

    const instruction = aiTopic.trim()
    const conversationContext = aiMessages
      .slice(-2)
      .map((message) => (message.role === 'user' ? 'Người dùng' : 'AI') + ': ' + message.content)
      .join('\n')
    const additionalInfo = [instruction ? 'Yêu cầu thêm: ' + instruction : '', conversationContext]
      .filter(Boolean)
      .join('\n')

    if (instruction) {
      setAiMessages((current) => [...current, {
        id: String(Date.now()) + '-user',
        role: 'user',
        content: instruction,
      }])
    }
    setAiTopic('')
    setIsGenerating(true)
    setStatusTone('info')
    setStatus(t('Đang tạo nội dung...', 'Generating content...'))

    try {
      const response = await api.post<GeneratedPostResponse>('/posts/generate', {
        productName: productName.trim(),
        productDescription: productDescription.trim(),
        price: price.trim() || undefined,
        industryCode: 'GENERAL',
        targetAudience: targetAudience.trim(),
        highlights: highlights.trim(),
        goal: goal.trim(),
        tone: aiTone,
        platforms: selectedPlatforms,
        additionalInfo: additionalInfo || undefined,
        imageIds: uploadedMedia.map((media) => media.id),
      })
      const post = response.data
      const facebookContent = getPlatformContent(post.platformContents, 'FACEBOOK')
      const firstContent = facebookContent ?? getFirstPlatformContent(post.platformContents)
      setGeneratedPost(post)
      setCaption(getContentText(firstContent))
      setCta(post.cta ?? '')
      setHashtagsText((post.hashtags ?? []).join(' '))
      setAiMessages((current) => [...current, {
        id: String(Date.now()) + '-assistant',
        role: 'assistant',
        content: getContentText(firstContent) || t('Đã tạo nội dung.', 'Content generated.'),
      }])
      setStatusTone('success')
      setStatus(t('Đã tạo nội dung. Bạn có thể chỉnh sửa trước khi đăng.', 'Content generated. Edit it before publishing.'))
    } catch (error) {
      const errorMessage = getErrorMessage(error, t('Không thể tạo nội dung. Kiểm tra cấu hình OpenAI.', 'Could not generate content. Check the OpenAI configuration.'))
      setAiMessages((current) => [...current, {
        id: String(Date.now()) + '-assistant-error',
        role: 'assistant',
        content: errorMessage,
      }])
      setError(errorMessage)
    } finally {
      setIsGenerating(false)
    }
  }

  async function syncGeneratedPost() {
    if (!generatedPost) {
      return null
    }

    const content = composeMessage(caption, cta, hashtagsText)
    const facebookContent: PlatformContent = {
      ...(getPlatformContent(generatedPost.platformContents, 'FACEBOOK') ?? {}),
      title: generatedPost.title,
      content,
    }
    const response = await api.put<GeneratedPostResponse>('/posts/' + generatedPost.postId, {
      title: generatedPost.title,
      platformContents: {
        ...generatedPost.platformContents,
        FACEBOOK: facebookContent,
      },
      hashtags: parseHashtags(hashtagsText),
      cta: cta.trim(),
    })
    setGeneratedPost(response.data)
    return response.data
  }

  async function handlePublish() {
    if (!selectedChannel) {
      setError(t('Chọn Facebook Page trước.', 'Select a Facebook Page first.'))
      return
    }
    if (!caption.trim()) {
      setError(t('Nhập nội dung bài viết.', 'Enter post content.'))
      return
    }
    if (!selectedPlatforms.includes('FACEBOOK')) {
      setError(t('Chọn Facebook để đăng bài.', 'Select Facebook to publish.'))
      return
    }
    if (publishMode === 'SCHEDULE' && (!scheduledTime || new Date(scheduledTime).getTime() <= Date.now() + 30_000)) {
      setError(t('Chọn thời gian đăng trong tương lai.', 'Choose a future publishing time.'))
      return
    }

    setIsPublishing(true)
    setStatusTone('info')
    setStatus(publishMode === 'SCHEDULE' ? t('Đang đặt lịch...', 'Scheduling...') : t('Đang tạo bài...', 'Creating post...'))

    try {
      const savedPost = await syncGeneratedPost()
      const response = await api.post<FacebookBusinessPostResponse>('/facebook-business/posts', {
        postId: savedPost?.postId,
        pageId: selectedChannel.accountId,
        message: composeMessage(caption, cta, hashtagsText),
        mediaType,
        mediaIds: uploadedMedia.map((media) => media.id),
        scheduledTime: publishMode === 'SCHEDULE' ? new Date(scheduledTime).toISOString() : undefined,
      })
      if (response.data.status === 'FAILED') {
        setError(t('Đăng bài thất bại.', 'Post publishing failed.'))
      } else {
        setStatusTone('success')
        setStatus(response.data.status === 'PUBLISHED'
          ? t('Đã đăng bài.', 'Post published.')
          : t('Đã đặt lịch.', 'Post scheduled.'))
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
    if (!files.every((file) => file.type.startsWith('image/'))) {
      setError(t('Chỉ hỗ trợ ảnh JPG, PNG hoặc WebP.', 'Only JPG, PNG or WebP images are supported.'))
      event.target.value = ''
      return
    }
    if (files.length > 10) {
      setError(t('Mỗi bài tối đa 10 ảnh.', 'Select up to 10 images.'))
      event.target.value = ''
      return
    }

    setIsUploadingMedia(true)
    setStatusTone('info')
    setStatus(t('Đang tải ảnh...', 'Uploading images...'))
    try {
      const uploaded = await Promise.all(files.map(async (file) => {
        const formData = new FormData()
        formData.append('file', file)
        const response = await api.post<UploadedMedia>('/files/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' },
        })
        return response.data
      }))
      setUploadedMedia(uploaded)
      setStatusTone('success')
      setStatus(t('Đã tải ' + uploaded.length + ' ảnh.', uploaded.length + ' image(s) uploaded.'))
    } catch (error) {
      setUploadedMedia([])
      setError(getErrorMessage(error, t('Không thể tải ảnh.', 'Could not upload images.')))
    } finally {
      setIsUploadingMedia(false)
      event.target.value = ''
    }
  }

  function removeMedia(mediaId: string) {
    setUploadedMedia((current) => current.filter((media) => media.id !== mediaId))
  }

  function resetAiAssistant() {
    setAiTopic('')
    setAiMessages([])
    setGeneratedPost(null)
    setCaption('')
    setCta('')
    setHashtagsText('')
    setStatusTone('info')
    setStatus(t('Đã xóa nội dung AI.', 'AI content cleared.'))
  }

  function togglePlatform(platform: Platform) {
    setSelectedPlatforms((current) => current.includes(platform)
      ? current.filter((value) => value !== platform)
      : [...current, platform])
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
              <label className="block">
                <span className="sr-only">Facebook Page</span>
                <div className="relative">
                  <select
                    value={selectedChannelId}
                    onChange={(event) => setSelectedChannelId(event.target.value)}
                    className="h-11 w-full appearance-none rounded-[6px] border border-slate-300 bg-white px-4 pr-10 text-sm font-semibold outline-none focus:border-blue-500"
                  >
                    {facebookChannels.length === 0
                      ? <option value="">{t('Chưa có Facebook Page', 'No Facebook Page')}</option>
                      : facebookChannels.map((channel) => (
                        <option key={channel.id} value={channel.id}>{channel.name}</option>
                      ))}
                  </select>
                  <ChevronDown className="pointer-events-none absolute right-3 top-3.5 text-slate-700" size={18} />
                </div>
              </label>
            </ComposerSection>

            <ComposerSection title={t('Thông tin sản phẩm', 'Product information')}>
              <div className="grid gap-3 sm:grid-cols-2">
                <TextField label={t('Tên sản phẩm *', 'Product name *')} value={productName} onChange={setProductName} className="sm:col-span-2" />
                <TextAreaField label={t('Mô tả *', 'Description *')} value={productDescription} onChange={setProductDescription} rows={3} className="sm:col-span-2" />
                <TextField label={t('Giá', 'Price')} value={price} onChange={setPrice} />
                <TextField label={t('Khách hàng mục tiêu *', 'Target audience *')} value={targetAudience} onChange={setTargetAudience} />
                <TextAreaField label={t('Điểm nổi bật *', 'Highlights *')} value={highlights} onChange={setHighlights} rows={2} />
                <TextField label={t('Mục tiêu', 'Goal')} value={goal} onChange={setGoal} />
              </div>
              <div className="mt-4">
                <p className="mb-2 text-xs font-bold uppercase tracking-wide text-slate-500">{t('Nền tảng', 'Platforms')}</p>
                <PlatformPicker selected={selectedPlatforms} onToggle={togglePlatform} />
              </div>
            </ComposerSection>

            <ComposerSection title={t('Ảnh sản phẩm', 'Product image')}>
              <div className="flex flex-wrap items-center gap-3">
                <label className="inline-flex h-10 cursor-pointer items-center gap-2 rounded-[6px] border border-slate-300 bg-white px-4 text-sm font-semibold hover:bg-slate-50">
                  <Image size={17} />
                  {t('Thêm ảnh', 'Add image')}
                  <input
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    multiple
                    onChange={(event) => void handleMediaFileChange(event)}
                    disabled={isUploadingMedia}
                    className="hidden"
                  />
                </label>
                {uploadedMedia.length > 0 && (
                  <span className="text-xs text-slate-500">{uploadedMedia.length}/10</span>
                )}
              </div>
              {uploadedMedia.length > 0 && (
                <div className="mt-3 space-y-2">
                  {uploadedMedia.map((media) => (
                    <div key={media.id} className="flex items-center justify-between gap-3 rounded-[6px] border border-emerald-200 bg-emerald-50 px-3 py-2 text-sm text-emerald-700">
                      <span className="min-w-0 truncate font-semibold">{media.fileName} ({formatFileSize(media.fileSize)})</span>
                      <button type="button" onClick={() => removeMedia(media.id)} className="flex h-7 w-7 shrink-0 items-center justify-center rounded-[6px] hover:bg-emerald-100" aria-label={t('Xóa ảnh', 'Remove image')}>
                        <X size={15} />
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </ComposerSection>

            <ComposerSection title={t('Nội dung bài đăng', 'Post content')}>
              <label className="block text-sm font-bold">
                {t('Caption', 'Caption')}
                <textarea
                  value={caption}
                  onChange={(event) => setCaption(event.target.value)}
                  className="mt-2 h-36 w-full resize-none rounded-[6px] border border-slate-300 bg-white px-3 py-2 text-sm leading-6 outline-none focus:border-blue-500"
                  placeholder={t('Nội dung sẽ hiển thị ở đây.', 'Your caption will appear here.')}
                />
              </label>
              <div className="mt-3 grid gap-3 sm:grid-cols-2">
                <TextField label={t('CTA', 'CTA')} value={cta} onChange={setCta} />
                <TextField label={t('Hashtag', 'Hashtags')} value={hashtagsText} onChange={setHashtagsText} />
              </div>

              <form onSubmit={(event) => void handleGenerate(event)} className="mt-4 overflow-hidden rounded-2xl border border-violet-200 bg-white shadow-sm shadow-violet-100/70">
                <div className="flex items-center gap-3 border-b border-violet-100 bg-gradient-to-r from-violet-50 via-white to-blue-50 px-4 py-3">
                  <span className="flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-violet-600 text-white">
                    <Sparkles size={16} />
                  </span>
                  <p className="text-sm font-bold text-slate-900">{t('Viết bằng AI', 'Write with AI')}</p>
                </div>
                <div className="border-b border-slate-100 bg-slate-50/70 p-3">
                  <label className="block rounded-xl border border-slate-200 bg-white px-3 py-2 shadow-sm">
                    <span className="block text-[10px] font-bold uppercase tracking-wider text-slate-400">{t('Giọng văn', 'Tone')}</span>
                    <select
                      value={aiTone}
                      onChange={(event) => setAiTone(event.target.value as AiTone)}
                      className="mt-0.5 h-7 w-full cursor-pointer bg-transparent text-sm font-semibold text-slate-700 outline-none"
                    >
                      <option value="FRIENDLY">{t('Thân thiện', 'Friendly')}</option>
                      <option value="PROFESSIONAL">{t('Chuyên nghiệp', 'Professional')}</option>
                      <option value="PERSUASIVE">{t('Thuyết phục', 'Persuasive')}</option>
                      <option value="CASUAL">{t('Gần gũi', 'Casual')}</option>
                      <option value="INSPIRATIONAL">{t('Truyền cảm hứng', 'Inspirational')}</option>
                      <option value="HUMOROUS">{t('Hài hước', 'Humorous')}</option>
                    </select>
                  </label>
                </div>

                {(aiMessages.length > 0 || isGenerating) && (
                  <div className="max-h-72 space-y-3 overflow-y-auto bg-slate-50/40 p-3">
                    {aiMessages.map((message) => (
                      <div key={message.id} className={message.role === 'user' ? 'ml-10' : 'mr-10'}>
                        <div className={cn('whitespace-pre-wrap rounded-xl px-3 py-3 text-sm leading-6', message.role === 'user' ? 'bg-violet-600 text-white' : 'border border-slate-100 bg-white text-slate-700 shadow-sm')}>
                          {message.content}
                        </div>
                      </div>
                    ))}
                    {isGenerating && (
                      <div className="mr-10 flex items-center gap-2 rounded-xl border border-slate-100 bg-white px-3 py-3 text-sm text-slate-500 shadow-sm">
                        <span className="h-2 w-2 animate-pulse rounded-full bg-violet-500" />
                        {t('AI đang viết...', 'AI is writing...')}
                      </div>
                    )}
                  </div>
                )}

                <div className="bg-white p-3">
                  <div className="relative rounded-xl border border-slate-200 bg-slate-50/60 transition focus-within:border-violet-400 focus-within:bg-white focus-within:ring-2 focus-within:ring-violet-100">
                    <textarea
                      value={aiTopic}
                      onChange={(event) => setAiTopic(event.target.value)}
                      rows={2}
                      className="min-h-16 w-full resize-none bg-transparent px-3 py-3 pr-14 text-sm leading-5 outline-none"
                      placeholder={t('Yêu cầu thêm cho AI (không bắt buộc)', 'Extra instruction for AI (optional)')}
                    />
                    <button
                      type="submit"
                      disabled={isGenerating}
                      className="absolute bottom-2.5 right-2.5 flex h-9 w-9 items-center justify-center rounded-xl bg-violet-600 text-white shadow-sm hover:bg-violet-700 disabled:bg-slate-300 disabled:shadow-none"
                      title={t('Tạo nội dung', 'Generate content')}
                    >
                      <Send size={16} />
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={resetAiAssistant}
                    disabled={isGenerating}
                    className="mt-2 text-xs font-semibold text-slate-500 hover:text-violet-700 disabled:opacity-50"
                  >
                    {t('Xóa nội dung AI', 'Clear AI content')}
                  </button>
                </div>
              </form>
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
              onClick={() => setStatus(generatedPost ? t('Bản nháp đã được lưu.', 'Draft saved.') : t('Hãy tạo nội dung trước.', 'Generate content first.'))}
              disabled={!generatedPost}
              className="inline-flex h-10 items-center justify-center rounded-[6px] border border-slate-300 px-4 text-sm font-semibold text-slate-500 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {t('Để sau', 'Later')}
            </button>
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
          <FacebookFeedPreview
            caption={previewMessage}
            channel={selectedChannel}
            mediaUrls={mediaUrls}
            scheduleLabel={scheduleLabel}
            compact={previewDevice === 'MOBILE'}
            labels={{
              facebookPage: t('Facebook Page', 'Facebook Page'),
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
  onToggle,
}: {
  selected: Platform[]
  onToggle: (platform: Platform) => void
}) {
  return (
    <div className="flex flex-wrap gap-2">
      {ALL_PLATFORMS.map((platform) => (
        <label key={platform} className={cn('inline-flex cursor-pointer items-center gap-2 rounded-full border px-3 py-1.5 text-xs font-semibold transition', selected.includes(platform) ? 'border-violet-300 bg-violet-50 text-violet-700' : 'border-slate-200 bg-white text-slate-500 hover:border-violet-200')}>
          <input
            type="checkbox"
            checked={selected.includes(platform)}
            onChange={() => onToggle(platform)}
            className="sr-only"
          />
          {PLATFORM_LABELS[platform]}
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

function FacebookFeedPreview({
  channel,
  caption,
  mediaUrls,
  scheduleLabel,
  compact,
  labels,
}: {
  channel?: AppChannel
  caption: string
  mediaUrls: string[]
  scheduleLabel: string
  compact: boolean
  labels: {
    facebookPage: string
    like: string
    comment: string
    share: string
  }
}) {
  const previewMediaUrls = mediaUrls.map(resolveMediaUrl).filter(Boolean)
  const visiblePreviewMediaUrls = previewMediaUrls.slice(0, 4)
  const hiddenImageCount = Math.max(0, previewMediaUrls.length - visiblePreviewMediaUrls.length)

  return (
    <article className={cn('mx-auto overflow-hidden rounded-[6px] bg-white shadow-xl shadow-slate-200/80', compact ? 'max-w-[360px]' : 'max-w-[620px]')}>
      <header className="flex items-center gap-3 px-5 py-4">
        <PageIdentity channel={channel} large />
        <div className="min-w-0">
          <p className="truncate text-sm font-bold">{channel?.name ?? labels.facebookPage}</p>
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

      <div className={cn('bg-slate-100', previewMediaUrls.length > 1 ? '' : 'flex min-h-[430px] items-center justify-center')}>
        {previewMediaUrls.length > 0 ? (
          <div className={cn('grid w-full', visiblePreviewMediaUrls.length > 1 ? 'grid-cols-2 gap-0.5' : 'grid-cols-1')}>
            {visiblePreviewMediaUrls.map((previewMediaUrl, index) => (
              <div key={previewMediaUrl + '-' + index} className="relative">
                <img
                  src={previewMediaUrl}
                  alt={'Facebook post media preview ' + (index + 1)}
                  className="mx-auto h-auto max-h-[430px] w-full object-contain"
                />
                {index === visiblePreviewMediaUrls.length - 1 && hiddenImageCount > 0 && (
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

function PageIdentity({ channel, large = false }: { channel?: AppChannel; large?: boolean }) {
  const initial = getPageInitial(channel?.name ?? 'Facebook')
  return (
    <span className={cn('relative flex shrink-0 items-center justify-center rounded-full border border-amber-200 bg-amber-50 font-bold leading-none text-amber-600', large ? 'h-12 w-12 text-base' : 'h-9 w-9 text-sm')}>
      {initial}
      <FacebookBadge className="absolute -bottom-1 -right-1 h-[18px] w-[18px] rounded-full ring-2 ring-white" />
    </span>
  )
}

function getPlatformContent(contents: Partial<Record<Platform, PlatformContent>>, platform: Platform) {
  return contents[platform] ?? contents[platform.toLowerCase() as Platform]
}

function getFirstPlatformContent(contents: Partial<Record<Platform, PlatformContent>>) {
  return ALL_PLATFORMS.map((platform) => getPlatformContent(contents, platform)).find(Boolean)
}

function getContentText(content?: PlatformContent) {
  return content?.content?.trim() || content?.caption?.trim() || ''
}

function composeMessage(caption: string, cta: string, hashtagsText: string) {
  const base = caption.trim()
  const callToAction = cta.trim()
  const hashtags = parseHashtags(hashtagsText)
  const parts = [base]
  if (callToAction && !base.includes(callToAction)) {
    parts.push(callToAction)
  }
  if (hashtags.length > 0 && !hashtags.every((hashtag) => base.includes(hashtag))) {
    parts.push(hashtags.join(' '))
  }
  return parts.filter(Boolean).join('\n\n')
}

function parseHashtags(value: string) {
  return value
    .split(/[\s,]+/)
    .map((item) => item.trim())
    .filter(Boolean)
    .map((item) => item.startsWith('#') ? item : '#' + item.replace(/^#+/, ''))
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
