import { Camera, Check, Link2, Music, RefreshCw, Smartphone, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { EmptyState } from '../components/EmptyState'
import { FacebookBadge } from '../components/FacebookBadge'
import { HelpTip } from '../components/HelpTip'
import { api, FACEBOOK_SYNC_TIMEOUT_MS } from '../lib/api'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { trackOnboardingStep } from '../lib/webAnalytics'
import { usePreferences } from '../lib/preferences'
import { PageHeader, PageState } from '../components/PageUi'

type StatusTone = 'info' | 'success' | 'error'
type SocialPlatform = 'facebook' | 'instagram' | 'threads'
type ManualConnectionForm = {
  accountId: string
  accountName: string
  accessToken: string
}
type PendingConfirm = { kind: 'delete' | 'test'; channelId: string }

const channelTypes = [
  {
    title: 'Trang Facebook',
    type: 'Facebook Page',
    icon: Smartphone,
    platform: 'facebook',
    manualPath: '/social/meta/facebook/manual',
    idLabel: 'Page ID',
    nameLabel: 'Tên Page',
    tokenLabel: 'Page Access Token',
    steps: [
      'Mở Meta for Developers, chọn đúng ứng dụng của Postiva và mở Graph API Explorer.',
      'Lấy danh sách Page qua /me/accounts (trường id, name, access_token), rồi chọn Page của bạn.',
      'Dán Page ID, tên Page và Page Access Token của đúng Page đó vào form. Đừng dán token người dùng.',
    ],
    note: 'Postiva kiểm tra token với Meta trước khi lưu. Nút Kiểm tra sau đó tạo một bài thử không công khai để xác nhận quyền đăng.',
  },
  {
    title: 'Instagram Business',
    type: 'Instagram Business',
    icon: Camera,
    platform: 'instagram',
    manualPath: '/social/instagram/manual',
    idLabel: 'Account ID',
    nameLabel: 'Tên tài khoản',
    tokenLabel: 'Access Token',
    steps: [
      'Dùng tài khoản Instagram Business hoặc Creator đã liên kết trong ứng dụng Meta của bạn.',
      'Tạo access token có quyền đăng nội dung cho tài khoản đó.',
      'Dán Account ID, tên tài khoản và token vào form.',
    ],
    note: 'Instagram cần ảnh hoặc video, không đăng được bài chỉ có chữ. Hiện đang thử nghiệm giới hạn.',
  },
  {
    title: 'Threads Business',
    type: 'Threads',
    icon: Music,
    platform: 'threads',
    manualPath: '/social/threads/manual',
    idLabel: 'Account ID',
    nameLabel: 'Tên tài khoản',
    tokenLabel: 'Access Token',
    steps: [
      'Mở ứng dụng Meta có sản phẩm Threads và tạo token cho tài khoản Threads của bạn.',
      'Chọn quyền đăng nội dung cho token.',
      'Dán Account ID, tên tài khoản và token vào form.',
    ],
    note: 'Threads đăng được chữ, ảnh và video. Hiện đang thử nghiệm giới hạn.',
  },
] as const

type ConnectedAccount = { pageId: string; pageName: string }
const emptyManualConnection = (): ManualConnectionForm => ({
  accountId: '',
  accountName: '',
  accessToken: '',
})

const inputClass = 'h-11 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20'

export function ConnectionsPage() {
  const { t } = usePreferences()
  const { data: channels, loading, error, reload, updateData } = useAppDataResource<AppChannel[]>('/app-data/channels')
  const [connectStatus, setConnectStatus] = useState('')
  const [connectStatusTone, setConnectStatusTone] = useState<StatusTone>('info')
  const [postingPageId, setPostingPageId] = useState<string | null>(null)
  const [deletingPageId, setDeletingPageId] = useState<string | null>(null)
  const [syncingPageId, setSyncingPageId] = useState<string | null>(null)
  const [connectingType, setConnectingType] = useState<SocialPlatform | null>(null)
  const [pendingConfirm, setPendingConfirm] = useState<PendingConfirm | null>(null)
  const [manualConnections, setManualConnections] = useState<Record<SocialPlatform, ManualConnectionForm>>({
    facebook: emptyManualConnection(),
    instagram: emptyManualConnection(),
    threads: emptyManualConnection(),
  })

  if (loading) {
    return <PageState title={t('Đang tải kênh kết nối...')} />
  }

  if (error || !channels) {
    return <PageState title="Không thể tải danh sách kết nối" description={error ?? undefined} onRetry={reload} />
  }

  const connectedChannels = channels.filter((channel) => ['Facebook', 'Instagram', 'Threads'].includes(channel.badge))

  async function handleTestPost(channel: AppChannel) {
    const isFacebook = channel.badge === 'Facebook'
    setPendingConfirm(null)
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
      setConnectStatus(isFacebook
        ? `${t('Kết nối hoạt động tốt trên')} ${channel.name}.`
        : `${t('Token truy cập được tài khoản', 'Token can access account')} ${channel.name}. ${t('Chưa kiểm tra quyền đăng bài và URL media công khai.', 'Publishing permission and public media URL have not been tested.')}`)
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getSocialErrorMessage(error, `Không thể kiểm tra ${channel.type}.`))
    } finally {
      setPostingPageId(null)
    }
  }

  async function handleManualConnect(platform: SocialPlatform, title: string, path: string) {
    const current = manualConnections[platform]
    const accountId = current.accountId.trim()
    const accountName = current.accountName.trim()
    const accessToken = current.accessToken.trim()
    if (!accountId || !accountName || !accessToken) {
      return
    }
    setConnectingType(platform)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang kiểm tra access token cho')} ${title}...`)

    try {
      const body = platform === 'facebook'
        ? { pageId: accountId, pageName: accountName, pageAccessToken: accessToken }
        : { accountId, accountName, accessToken }
      const response = await api.post<ConnectedAccount>(path, body)
      setManualConnections((connections) => ({
        ...connections,
        [platform]: emptyManualConnection(),
      }))
      setConnectStatusTone('success')
      setConnectStatus(`${t('Đã kết nối')} ${response.data.pageName} (ID ${response.data.pageId}). Bước tiếp theo: tạo bài đầu tiên.`)
      reload()
    } catch (error) {
      trackOnboardingStep(`connect-failed/${platform}`)
      setConnectStatusTone('error')
      setConnectStatus(getSocialErrorMessage(error, `Không thể kết nối ${title}.`))
    } finally {
      setConnectingType(null)
    }
  }

  async function handleDelete(channel: AppChannel) {
    setPendingConfirm(null)
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
      setConnectStatus(getSocialErrorMessage(error, `Không thể cập nhật ${channel.type}.`))
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
    setPendingConfirm(null)
  }

  function updateForm(platform: SocialPlatform, field: keyof ManualConnectionForm, value: string) {
    setManualConnections((connections) => ({
      ...connections,
      [platform]: { ...connections[platform], [field]: value },
    }))
  }

  return (
    <section className="space-y-6">
      <PageHeader
        title={t('Kết nối tài khoản')}
        subtitle="Thêm kênh để Postiva đăng bài thay bạn. Mỗi kênh cần một token do Meta cấp."
      />

      <section className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm" aria-labelledby="connected-heading">
        <div className="border-b border-[var(--app-border)] p-5">
          <h2 id="connected-heading" className="text-base font-bold">{t('Kênh đã thêm')} ({connectedChannels.length})</h2>
        </div>
        <div className="divide-y divide-[var(--app-border)]">
          {connectedChannels.length === 0 ? (
            <EmptyState
              icon={<Link2 size={20} />}
              title="Chưa có kênh nào"
              description="Điền thông tin vào một trong ba thẻ bên dưới; bấm dấu ? ở mỗi thẻ để xem cách lấy token. Facebook Page là lựa chọn ổn định nhất hiện nay."
            />
          ) : (
            connectedChannels.map((channel) => {
              const confirming = pendingConfirm?.channelId === channel.id ? pendingConfirm.kind : null
              return (
                <div key={channel.id} className="px-5 py-4">
                  <div className="grid gap-4 md:grid-cols-[40px_minmax(0,1fr)_max-content_max-content_44px] md:items-center">
                    <ChannelAvatar channel={channel} />
                    <div className="min-w-0">
                      <h3 className="truncate text-sm font-bold">{channel.name}</h3>
                      <p className="mt-1 truncate text-xs text-[var(--muted-text)]">
                        {channel.type} · ID {channel.accountId}
                      </p>
                    </div>
                    <span className={`w-fit whitespace-nowrap rounded-full px-3 py-1 text-xs font-semibold ${channel.active ? 'bg-emerald-50 text-emerald-800' : 'bg-slate-100 text-slate-600'}`}>
                      {channel.active ? 'Đã lưu · cần kiểm tra hiệu lực' : t('Tạm ngừng')}
                    </span>
                    <div className="flex items-center gap-2 md:justify-end">
                      <button
                        type="button"
                        onClick={() => void handleSync(channel)}
                        disabled={syncingPageId === channel.id}
                        className="inline-flex h-11 items-center gap-1 rounded-lg border border-[var(--app-border)] px-3 text-xs font-bold text-[var(--muted-text)] hover:bg-[var(--soft-bg)] disabled:bg-slate-100"
                      >
                        <RefreshCw size={13} aria-hidden="true" className={syncingPageId === channel.id ? 'animate-spin' : ''} />
                        {syncingPageId === channel.id ? t('Đang cập nhật') : t('Cập nhật')}
                      </button>
                      <button
                        type="button"
                        onClick={() => channel.badge === 'Facebook'
                          ? setPendingConfirm({ kind: 'test', channelId: channel.id })
                          : void handleTestPost(channel)}
                        disabled={postingPageId === channel.id}
                        className="h-11 rounded-lg bg-blue-600 px-3 text-xs font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
                      >
                        {postingPageId === channel.id ? t('Đang kiểm tra') : t('Kiểm tra')}
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={() => setPendingConfirm({ kind: 'delete', channelId: channel.id })}
                      disabled={deletingPageId === channel.id}
                      className="flex h-11 w-11 items-center justify-center rounded-lg text-slate-600 hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label={`${t('Xóa kết nối')} ${channel.name}`}
                    >
                      <Trash2 size={16} aria-hidden="true" />
                    </button>
                  </div>

                  {confirming && (
                    <div role="alertdialog" aria-label="Xác nhận" className="mt-3 flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900 sm:flex-row sm:items-center sm:justify-between">
                      <p>
                        {confirming === 'delete'
                          ? `Xóa kết nối "${channel.name}"? Lịch đăng đang chờ của kênh này sẽ không chạy nữa.`
                          : 'Postiva sẽ tạo một bài thử không công khai trên Page này để kiểm tra quyền đăng. Tiếp tục?'}
                      </p>
                      <div className="flex gap-2">
                        <button type="button" onClick={() => setPendingConfirm(null)} className="min-h-11 rounded-lg border border-amber-300 px-4 text-sm font-semibold hover:bg-amber-100">
                          Hủy
                        </button>
                        <button
                          type="button"
                          onClick={() => void (confirming === 'delete' ? handleDelete(channel) : handleTestPost(channel))}
                          className={`min-h-11 rounded-lg px-4 text-sm font-bold text-white ${confirming === 'delete' ? 'bg-rose-600 hover:bg-rose-700' : 'bg-blue-600 hover:bg-blue-700'}`}
                        >
                          {confirming === 'delete' ? 'Xóa kết nối' : 'Tạo bài thử'}
                        </button>
                      </div>
                    </div>
                  )}
                </div>
              )
            })
          )}
        </div>
      </section>

      {connectStatus && (
        <div
          role="status"
          className={`flex flex-wrap items-center justify-between gap-3 rounded-lg px-4 py-3 text-sm font-semibold ${getStatusClassName(connectStatusTone)}`}
        >
          <p>{connectStatus}</p>
          {connectStatusTone === 'success' && connectStatus.startsWith(t('Đã kết nối')) && (
            <Link to="/app/create" className="inline-flex min-h-11 items-center rounded-full bg-emerald-700 px-4 text-sm font-bold text-white hover:bg-emerald-800">
              Tạo bài đầu tiên
            </Link>
          )}
        </div>
      )}

      <section aria-labelledby="add-channel-heading">
        <h2 id="add-channel-heading" className="mb-3 text-base font-bold">Thêm kênh mới</h2>
        <div className="grid gap-4 md:grid-cols-3">
          {channelTypes.map((type) => {
            const Icon = type.icon
            const count = connectedChannels.filter((channel) => channel.type === type.type).length
            const form = manualConnections[type.platform]
            const idPrefix = `connect-${type.platform}`
            return (
              <div key={type.platform} className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm">
                <div className="flex items-center justify-between">
                  <Icon className="text-blue-600" size={22} aria-hidden="true" />
                  <div className="flex items-center gap-1">
                    {count > 0 && <Check className="text-emerald-600" size={18} aria-label="Đã kết nối" />}
                    <HelpTip label={`Cách lấy thông tin cho ${type.title}`}>
                      <ol className="space-y-2">
                        {type.steps.map((step, index) => (
                          <li key={step}>
                            <span className="font-bold">Bước {index + 1}.</span> {step}
                          </li>
                        ))}
                      </ol>
                      <p className="mt-3 text-xs text-[var(--muted-text)]">{type.note}</p>
                      <p className="mt-2 text-xs text-[var(--muted-text)]">Chỉ dán token của Page hoặc tài khoản do bạn quản trị. Postiva mã hóa token khi lưu và không hiển thị lại.</p>
                    </HelpTip>
                  </div>
                </div>
                <h3 className="text-sm font-bold">{type.title}</h3>
                <p className={`mt-1 text-xs font-semibold ${count > 0 ? 'text-emerald-700' : 'text-[var(--muted-text)]'}`}>
                  {count > 0 ? `${count} ${t('kết nối')}` : t('Chưa kết nối')}
                </p>

                <form
                  autoComplete="off"
                  data-form-type="other"
                  className="mt-3 space-y-3"
                  onSubmit={(event) => {
                    event.preventDefault()
                    void handleManualConnect(type.platform, type.title, type.manualPath)
                  }}
                >
                  <div>
                    <label htmlFor={`${idPrefix}-account-id`} className="block text-xs font-semibold">{type.idLabel}</label>
                    <input
                      id={`${idPrefix}-account-id`}
                      name={`${type.platform}-connection-id`}
                      type="text"
                      autoComplete="off"
                      spellCheck={false}
                      required
                      value={form.accountId}
                      onChange={(event) => updateForm(type.platform, 'accountId', event.target.value)}
                      data-1p-ignore="true"
                      data-bwignore="true"
                      data-lpignore="true"
                      className={`mt-1 ${inputClass}`}
                    />
                  </div>
                  <div>
                    <label htmlFor={`${idPrefix}-account-name`} className="block text-xs font-semibold">{type.nameLabel}</label>
                    <input
                      id={`${idPrefix}-account-name`}
                      type="text"
                      autoComplete="off"
                      required
                      value={form.accountName}
                      onChange={(event) => updateForm(type.platform, 'accountName', event.target.value)}
                      className={`mt-1 ${inputClass}`}
                    />
                  </div>
                  <div>
                    <label htmlFor={`${idPrefix}-access-token`} className="block text-xs font-semibold">{type.tokenLabel}</label>
                    <input
                      id={`${idPrefix}-access-token`}
                      name={`${type.platform}-connection-token`}
                      type="password"
                      autoComplete="new-password"
                      spellCheck={false}
                      required
                      value={form.accessToken}
                      onChange={(event) => updateForm(type.platform, 'accessToken', event.target.value)}
                      data-1p-ignore="true"
                      data-bwignore="true"
                      data-lpignore="true"
                      className={`mt-1 ${inputClass}`}
                    />
                  </div>
                  <button
                    type="submit"
                    disabled={connectingType !== null}
                    className="min-h-11 w-full rounded-full bg-blue-600 px-4 text-sm font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
                  >
                    {connectingType === type.platform ? t('Đang kết nối...') : `${t('Kết nối')} ${type.title}`}
                  </button>
                </form>
              </div>
            )
          })}
        </div>
      </section>
    </section>
  )
}

function ChannelAvatar({ channel }: { channel: AppChannel }) {
  const initial = channel.name.trim().replace(/^@/, '').charAt(0).toUpperCase() || channel.badge.charAt(0)

  return (
    <span
      className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-[var(--app-border)] bg-[var(--soft-bg)] text-sm font-bold leading-none"
      style={{ color: channel.color }}
      aria-hidden="true"
    >
      {initial}
      {channel.badge === 'Facebook' && <FacebookBadge className="absolute -bottom-1 -right-1 h-[18px] w-[18px] rounded-full ring-2 ring-white" />}
    </span>
  )
}

function getStatusClassName(tone: StatusTone) {
  if (tone === 'success') {
    return 'border border-emerald-200 bg-emerald-50 text-emerald-800'
  }
  if (tone === 'error') {
    return 'border border-rose-200 bg-rose-50 text-rose-700'
  }

  return 'border border-[var(--app-border)] bg-[var(--soft-bg)] text-[var(--muted-text)]'
}

function getSocialErrorMessage(error: unknown, fallback: string) {
  const message = getErrorMessage(error, fallback)
  if (/invalid oauth access token|access token.*(expired|invalid|revoked)|session has expired|token.*(hết hạn|không hợp lệ|bị thu hồi)/i.test(message)) {
    return 'Token đã hết hạn hoặc bị Meta thu hồi. Hãy lấy Page Access Token mới, kết nối lại rồi bấm Kiểm tra.'
  }
  if (/permission|permissions|thiếu quyền|không có quyền/i.test(message)) {
    return 'Token chưa có quyền cần thiết cho thao tác này. Hãy kiểm tra quyền Page trong cấu hình Meta; nếu vừa cấp quyền, hãy tạo token mới rồi kết nối lại.'
  }
  return message
}
