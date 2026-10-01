import { Camera, CheckCircle2, Music, RefreshCw, Smartphone, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { FacebookBadge } from '../components/FacebookBadge'
import { api, FACEBOOK_SYNC_TIMEOUT_MS } from '../lib/api'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { usePreferences } from '../lib/preferences'
import { PageHeader, PageState } from '../components/PageUi'

const channelTypes = [
  { title: 'Trang Facebook', type: 'Facebook Page', icon: Smartphone, available: true, oauthPath: '/social/meta/connect-url' },
  { title: 'Instagram Business', type: 'Instagram Business', icon: Camera, available: true, oauthPath: '/social/instagram/connect-url' },
  { title: 'Threads Business', type: 'Threads', icon: Music, available: true, oauthPath: '/social/threads/connect-url' },
]

type StatusTone = 'info' | 'success' | 'error'
type OAuthPlatform = 'facebook' | 'instagram' | 'threads'
const oauthPlatforms: OAuthPlatform[] = ['facebook', 'instagram', 'threads']
const oauthPlatformLabels: Record<OAuthPlatform, string> = {
  facebook: 'Facebook',
  instagram: 'Instagram',
  threads: 'Threads',
}
type SocialConnectUrlResponse = {
  url: string | null
  note: string
}

export function ConnectionsPage() {
  const { t } = usePreferences()
  const { data: channels, loading, error, reload, updateData } = useAppDataResource<AppChannel[]>('/app-data/channels')
  const [connectStatus, setConnectStatus] = useState('')
  const [connectStatusTone, setConnectStatusTone] = useState<StatusTone>('info')
  const [postingPageId, setPostingPageId] = useState<string | null>(null)
  const [deletingPageId, setDeletingPageId] = useState<string | null>(null)
  const [syncingPageId, setSyncingPageId] = useState<string | null>(null)
  const [connectingType, setConnectingType] = useState<string | null>(null)

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    const callbackPlatform = oauthPlatforms.find((platform) => {
      const result = params.get(platform)
      return result === 'connected' || result === 'error'
    })
    if (!callbackPlatform) {
      return
    }

    const callbackResult = params.get(callbackPlatform)
    const platformLabel = oauthPlatformLabels[callbackPlatform]
    if (callbackResult === 'connected') {
      const pageCountValue = params.get('pages')
      const pageCount = pageCountValue === null ? null : Number(pageCountValue)
      setConnectStatusTone('success')
      setConnectStatus(callbackPlatform === 'facebook' && pageCount !== null && Number.isFinite(pageCount)
        ? `${t('Đã kết nối Facebook')} (${pageCount} ${t('trang')}).`
        : `${t('Đã kết nối')} ${platformLabel}.`)
      reload()
    } else {
      setConnectStatusTone('error')
      setConnectStatus(`${t('Không thể kết nối')} ${platformLabel}. ${oauthErrorMessage(params.get('reason'), t)}`)
    }
    window.history.replaceState({}, '', window.location.pathname + window.location.hash)
  }, [reload, t])

  if (loading) {
    return <PageState title={t('Đang tải kênh kết nối...', )} />
  }

  if (error || !channels) {
    return <PageState title="Không thể tải danh sách kết nối" description={error ?? undefined} />
  }

  const connectedChannels = channels.filter((channel) => ['Facebook', 'Instagram', 'Threads'].includes(channel.badge))

  async function handleTestPost(channel: AppChannel) {
    const isFacebook = channel.badge === 'Facebook'
    setPostingPageId(channel.id)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang kiểm tra kết nối với')} ${channel.name}...`)

    try {
      if (isFacebook) {
        await api.post('/social/meta/facebook/test-post', {
          pageId: channel.accountId,
          message: `Postiva demo: kết nối Facebook Business thành công lúc ${new Date().toLocaleString('vi-VN')}.`,
        })
      } else {
        await api.post(`/social/meta/accounts/${channel.id}/check`)
      }
      setConnectStatusTone('success')
      setConnectStatus(`${t('Kết nối hoạt động tốt trên')} ${channel.name}.`)
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, `Không thể kiểm tra ${channel.type}.`))
    } finally {
      setPostingPageId(null)
    }
  }

  async function handleOAuthConnect(type: string, path: string) {
    setConnectingType(type)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang mở trang kết nối')} ${type}...`)

    try {
      const response = await api.get<SocialConnectUrlResponse>(path)
      if (!response.data.url) {
        throw new Error(response.data.note || `${type} chưa được cấu hình.`)
      }
      window.location.href = response.data.url
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, `Không thể mở kết nối ${type}.`))
      setConnectingType(null)
    }
  }

  async function handleDelete(channel: AppChannel) {
    const confirmed = window.confirm(`${t('Xóa kết nối')} "${channel.name}"?`)
    if (!confirmed) {
      return
    }

    setDeletingPageId(channel.id)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang xóa kết nối')} ${channel.name}...`)

    try {
      await api.delete(`/social/meta/accounts/${channel.id}`)
      updateData((currentChannels) => currentChannels.filter((currentChannel) => currentChannel.id !== channel.id))
      resetConnectionState()
      reload()
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, `Không thể ngắt kết nối ${channel.type}.`))
    } finally {
      setDeletingPageId(null)
    }
  }

  async function handleSync(channel: AppChannel) {
    const isFacebook = channel.badge === 'Facebook'
    setSyncingPageId(channel.id)
    setConnectStatusTone('info')
    setConnectStatus(isFacebook
      ? `${t('Đang cập nhật bài đăng từ')} ${channel.name}...`
      : `${t('Đang cập nhật thông tin từ')} ${channel.name}...`)

    try {
      if (isFacebook) {
        const response = await api.post<{ syncedPosts: number; removedPosts?: number }>('/social/meta/facebook/sync-posts', null, {
          params: { pageId: channel.accountId },
          timeout: FACEBOOK_SYNC_TIMEOUT_MS,
        })
        const removedPosts = response.data.removedPosts ?? 0
        setConnectStatusTone('success')
        setConnectStatus(`${t('Đã cập nhật')} ${response.data.syncedPosts} ${t('bài đăng từ')} ${channel.name}.${removedPosts > 0 ? ` ${t(`Đã gỡ ${removedPosts} bài không còn trên Facebook.`)}` : ''}`)
      } else {
        await api.post(`/social/meta/accounts/${channel.id}/refresh`)
        setConnectStatusTone('success')
        setConnectStatus(`${t('Đã cập nhật thông tin từ')} ${channel.name}.`)
        reload()
      }
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, `Không thể cập nhật ${channel.type}.`))
    } finally {
      setSyncingPageId(null)
    }
  }

  function resetConnectionState() {
    setConnectStatus('')
    setConnectStatusTone('info')
    setPostingPageId(null)
    setSyncingPageId(null)
    setConnectingType(null)
  }

  return (
    <section className="space-y-6">
      <PageHeader title={t('Kết nối tài khoản')} />

      <section className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
          <div className="border-b border-[var(--app-border)] p-5">
            <h2 className="text-base font-bold">{t('Kênh đã thêm', )} ({connectedChannels.length})</h2>
          </div>
          <div className="divide-y divide-[var(--app-border)]">
            {connectedChannels.length === 0 ? (
              <div className="px-5 py-6 text-sm text-[var(--muted-text)]">{t('Chưa có kênh nào được kết nối.')}</div>
            ) : (
              connectedChannels.map((channel) => (
                <div key={channel.id} className="grid gap-4 px-5 py-4 md:grid-cols-[40px_1fr_112px_190px_40px] md:items-center">
                  <ChannelAvatar channel={channel} />
                  <div className="min-w-0">
                    <h3 className="truncate text-sm font-bold">{channel.name}</h3>
                    <p className="mt-1 truncate text-xs text-[var(--muted-text)]">
                      {channel.type} · ID {channel.accountId}
                    </p>
                  </div>
                  <span className={`w-fit rounded-full px-3 py-1 text-xs font-semibold ${channel.active ? 'bg-emerald-50 text-emerald-700' : 'bg-slate-100 text-slate-500'}`}>
                    {channel.active ? t('Đang kết nối' ) : t('Tạm ngừng')}
                  </span>
                  <div className="flex items-center gap-2 md:justify-end">
                    <button
                      type="button"
                      onClick={() => void handleSync(channel)}
                      disabled={syncingPageId === channel.id}
                      className="inline-flex h-8 items-center gap-1 rounded-lg border border-[var(--app-border)] px-3 text-xs font-bold text-[var(--muted-text)] hover:bg-[var(--soft-bg)] disabled:bg-slate-100"
                    >
                      <RefreshCw size={13} className={syncingPageId === channel.id ? 'animate-spin' : ''} />
                      {syncingPageId === channel.id ? t('Đang cập nhật') : t('Cập nhật')}
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleTestPost(channel)}
                      disabled={postingPageId === channel.id}
                      className="h-8 rounded-lg bg-blue-600 px-3 text-xs font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
                    >
                      {postingPageId === channel.id ? t('Đang kiểm tra') : t('Kiểm tra')}
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleDelete(channel)}
                    disabled={deletingPageId === channel.id}
                    className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-300 hover:bg-rose-50 hover:text-rose-500 disabled:cursor-not-allowed disabled:opacity-50"
                    title={t('Xóa kết nối')}
                  >
                    <Trash2 size={16} />
                  </button>
                </div>
              ))
            )}
          </div>
      </section>

      {connectStatus && (
        <p
          role="status"
          className={`rounded-lg px-3 py-2 text-sm font-semibold ${getStatusClassName(connectStatusTone)}`}
        >
          {connectStatus}
        </p>
      )}

      <div className="grid gap-4 md:grid-cols-3">
        {channelTypes.map((type) => {
          const Icon = type.icon
          const count = type.available
            ? connectedChannels.filter((channel) => channel.type === type.type).length
            : 0
          return (
            <div key={type.title} className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <Icon className="text-blue-600" size={22} />
                {type.available ? <CheckCircle2 className="text-emerald-500" size={18} /> : <span className="rounded-full bg-[var(--soft-bg)] px-2 py-1 text-[10px] font-bold text-[var(--muted-text)]">Sắp ra mắt</span>}
              </div>
              <h2 className="text-sm font-bold">{type.title}</h2>
              <p className={`mt-2 text-xs font-semibold ${type.available && count > 0 ? 'text-emerald-600' : 'text-[var(--muted-text)]'}`}>
                {type.available ? (count > 0 ? `${count} ${t('kết nối')}` : t('Chưa kết nối')) : t('Sắp ra mắt')}
              </p>
              <p className="mt-4 text-xs leading-5 text-[var(--muted-text)]">
                {t('Đăng nhập và cấp quyền trực tiếp. Bạn không cần tự lấy hoặc nhập access token.')}
              </p>
              {type.oauthPath && (
                <button
                  type="button"
                  onClick={() => void handleOAuthConnect(type.title, type.oauthPath)}
                  disabled={connectingType === type.title}
                  className="mt-3 h-9 w-full rounded-lg bg-blue-600 px-3 text-xs font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
                >
                  {connectingType === type.title ? t('Đang chuyển hướng...') : `${t('Kết nối với')} ${type.title}`}
                </button>
              )}
            </div>
          )
        })}
      </div>
    </section>
  )
}

function ChannelAvatar({ channel }: { channel: AppChannel }) {
  const initial = channel.name.trim().replace(/^@/, '').charAt(0).toUpperCase() || channel.badge.charAt(0)

  return (
    <span
      className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-[var(--app-border)] bg-[var(--soft-bg)] text-sm font-bold leading-none"
      style={{ color: channel.color }}
    >
      {initial}
      {channel.badge === 'Facebook' && <FacebookBadge className="absolute -bottom-1 -right-1 h-[18px] w-[18px] rounded-full ring-2 ring-white" />}
    </span>
  )
}

function getStatusClassName(tone: StatusTone) {
  if (tone === 'success') {
    return 'border border-emerald-200 bg-emerald-50 text-emerald-700'
  }
  if (tone === 'error') {
    return 'border border-rose-200 bg-rose-50 text-rose-700'
  }

  return 'border border-[var(--app-border)] bg-[var(--soft-bg)] text-[var(--muted-text)]'
}

function oauthErrorMessage(reason: string | null, t: (vi: string, en?: string) => string) {
  switch (reason) {
    case 'access_denied':
      return t('Bạn đã hủy hoặc từ chối cấp quyền. Hãy thử lại khi sẵn sàng.')
    case 'missing_state':
    case 'invalid_state':
      return t('Phiên kết nối không hợp lệ hoặc đã hết hạn. Vui lòng bắt đầu lại.')
    case 'missing_code':
      return t('Nhà cung cấp không trả về mã xác thực. Vui lòng thử lại.')
    case 'provider_error':
      return t('Nhà cung cấp từ chối yêu cầu kết nối. Vui lòng thử lại.')
    case 'no_pages':
      return t('Không tìm thấy Facebook Page có quyền kết nối. Hãy kiểm tra quyền quản lý Trang rồi thử lại.')
    default:
      return t('Đã xảy ra lỗi khi hoàn tất OAuth. Vui lòng thử lại.')
  }
}
