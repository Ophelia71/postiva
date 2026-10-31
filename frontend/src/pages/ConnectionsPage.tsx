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
  accessToken: string
}
type PendingConfirm = { kind: 'delete'; channelId: string }
type SyncResult = { imported: number; updated: number; removed: number; metricsAvailable: boolean; warning?: string | null }
type DeletionImpact = { publishedPosts: number; scheduledPosts: number; failedPosts: number; weeklyPlans: number }

const channelTypes = [
  {
    title: 'Trang Facebook',
    type: 'Facebook Page',
    icon: Smartphone,
    platform: 'facebook',
    manualPath: '/social/meta/facebook/manual',
    idLabel: 'Page ID',
    tokenLabel: 'Page Access Token',
    steps: [
      'Mở Meta for Developers, chọn đúng ứng dụng của Postiva và mở Graph API Explorer.',
      'Lấy danh sách Page qua /me/accounts (trường id, name, access_token), rồi chọn Page của bạn.',
      'Dán Page ID và Page Access Token của đúng Page đó vào form. Postiva tự lấy tên Page. Đừng dán token người dùng.',
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
    tokenLabel: 'Access Token',
    steps: [
      'Dùng tài khoản Instagram Business hoặc Creator đã liên kết trong ứng dụng Meta của bạn.',
      'Tạo access token có quyền đăng nội dung cho tài khoản đó.',
      'Dán Account ID và token vào form. Postiva tự lấy tên tài khoản.',
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
    tokenLabel: 'Access Token',
    steps: [
      'Mở ứng dụng Meta có sản phẩm Threads và tạo token cho tài khoản Threads của bạn.',
      'Chọn quyền đăng nội dung cho token.',
      'Dán Account ID và token vào form. Postiva tự lấy tên tài khoản.',
    ],
    note: 'Threads đăng được chữ, ảnh và video. Hiện đang thử nghiệm giới hạn.',
  },
] as const

type ConnectedAccount = { pageId: string; pageName: string }
const emptyManualConnection = (): ManualConnectionForm => ({
  accountId: '',
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
  // What deleting the channel being confirmed will remove; null while loading or if it could not be counted.
  const [deletionImpact, setDeletionImpact] = useState<{ channelId: string; impact: DeletionImpact | null } | null>(null)

  async function askToDelete(channel: AppChannel) {
    setPendingConfirm({ kind: 'delete', channelId: channel.id })
    setDeletionImpact(null)
    try {
      const response = await api.get<DeletionImpact>(`/social/meta/accounts/${channel.id}/deletion-impact`)
      setDeletionImpact({ channelId: channel.id, impact: response.data })
    } catch {
      setDeletionImpact({ channelId: channel.id, impact: null })
    }
  }
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
    setPendingConfirm(null)
    setPostingPageId(channel.id)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang kiểm tra kết nối với')} ${channel.name}...`)

    try {
      await api.post(`/social/meta/accounts/${channel.id}/check`)
      setConnectStatusTone('success')
      setConnectStatus(`${t('Kết nối hoạt động tốt trên')} ${channel.name}.`)
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
    const accessToken = current.accessToken.trim()
    if (!accountId || !accessToken) {
      return
    }
    setConnectingType(platform)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang kiểm tra kết nối với')} ${title}...`)

    try {
      const body = platform === 'facebook'
        ? { pageId: accountId, pageAccessToken: accessToken }
        : { accountId, accessToken }
      const response = await api.post<ConnectedAccount>(path, body)
      setManualConnections((connections) => ({
        ...connections,
        [platform]: emptyManualConnection(),
      }))
      setConnectStatusTone('success')
      setConnectStatus(`${t('Đã kết nối')} ${response.data.pageName}. Postiva đang lấy các bài cũ của kênh, vài phút nữa sẽ thấy ở Báo cáo.`)
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
    setSyncingPageId(channel.id)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang cập nhật bài đăng và lượt tương tác từ')} ${channel.name}...`)

    try {
      // Instagram and Threads also refresh their profile (name, token) first; Facebook Pages do that while syncing.
      if (channel.badge !== 'Facebook') await api.post(`/social/meta/accounts/${channel.id}/refresh`)
      const response = await api.post<SyncResult>(`/social/meta/accounts/${channel.id}/sync`, null, { timeout: FACEBOOK_SYNC_TIMEOUT_MS })
      const { imported, updated, removed, warning } = response.data
      const parts = [`${t('Đã cập nhật')} ${imported + updated} ${t('bài từ')} ${channel.name}${imported > 0 ? ` (${imported} ${t('bài mới')})` : ''}.`]
      if (removed > 0) parts.push(`${t('Đã gỡ')} ${removed} ${t('bài không còn trên')} ${channel.badge}.`)
      if (warning) parts.push(warning)
      setConnectStatusTone(warning ? 'info' : 'success')
      setConnectStatus(parts.join(' '))
      reload()
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
                        onClick={() => void handleTestPost(channel)}
                        disabled={postingPageId === channel.id}
                        className="h-11 rounded-lg bg-blue-600 px-3 text-xs font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
                      >
                        {postingPageId === channel.id ? t('Đang kiểm tra') : t('Kiểm tra')}
                      </button>
                    </div>
                    <button
                      type="button"
                      onClick={() => void askToDelete(channel)}
                      disabled={deletingPageId === channel.id}
                      className="flex h-11 w-11 items-center justify-center rounded-lg text-slate-600 hover:bg-rose-50 hover:text-rose-600 disabled:cursor-not-allowed disabled:opacity-50"
                      aria-label={`${t('Xóa kết nối')} ${channel.name}`}
                    >
                      <Trash2 size={16} aria-hidden="true" />
                    </button>
                  </div>

                  {confirming && (
                    <div role="alertdialog" aria-label="Xác nhận" className="mt-3 flex flex-col gap-3 rounded-lg border border-[var(--app-border)] bg-[var(--soft-bg)] p-4 text-sm sm:flex-row sm:items-start sm:justify-between">
                      <DeleteChannelSummary
                        channel={channel}
                        impact={deletionImpact?.channelId === channel.id ? deletionImpact.impact : undefined}
                      />
                      <div className="flex shrink-0 gap-2">
                        <button type="button" onClick={() => setPendingConfirm(null)} className="min-h-11 rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 text-sm font-semibold hover:bg-[var(--soft-bg)]">
                          Hủy
                        </button>
                        <button
                          type="button"
                          onClick={() => void handleDelete(channel)}
                          className="min-h-11 rounded-lg bg-rose-600 px-4 text-sm font-bold text-white hover:bg-rose-700"
                        >
                          Xóa kênh
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

/** What removing a channel deletes, so nobody deletes a channel just to fix a token. */
function DeleteChannelSummary({ channel, impact }: { channel: AppChannel; impact: DeletionImpact | null | undefined }) {
  const counts = impact ? [
    [impact.publishedPosts, 'bài đã đăng'],
    [impact.scheduledPosts, 'bài đang hẹn giờ'],
    [impact.failedPosts, 'bài lỗi hoặc đã hủy'],
    [impact.weeklyPlans, 'kế hoạch tuần'],
  ].filter(([count]) => (count as number) > 0) : []
  return (
    <div className="space-y-2">
      <p className="font-semibold">Xóa kênh "{channel.name}" và toàn bộ lịch sử của kênh trong Postiva?</p>
      {impact === undefined && <p className="text-[var(--muted-text)]">Đang đếm dữ liệu của kênh...</p>}
      {impact && (counts.length > 0
        ? <p>Sẽ xóa vĩnh viễn: {counts.map(([count, label]) => `${count} ${label}`).join(', ')}, cùng số liệu tương tác của các bài này.</p>
        : <p>Kênh này chưa có bài nào trong Postiva.</p>)}
      <p className="text-[var(--muted-text)]">
        Bài đã đăng trên {channel.badge} vẫn còn ở đó; kết nối lại kênh sẽ lấy lại các bài đã đăng. Bài đang hẹn giờ, bản nháp và lịch sử lỗi thì mất hẳn.
        Nếu chỉ lỗi token, hãy bấm <span className="font-semibold text-[var(--app-text)]">Cập nhật</span> thay vì xóa.
      </p>
    </div>
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
    return 'Token đã hết hạn. Hãy lấy token mới theo hướng dẫn rồi kết nối lại.'
  }
  if (/permission|permissions|thiếu quyền|không có quyền/i.test(message)) {
    return 'Token này chưa đủ quyền đăng bài. Hãy tạo token mới theo hướng dẫn rồi kết nối lại.'
  }
  return message
}
