import { Camera, CheckCircle2, Music, RefreshCw, Smartphone, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { FacebookBadge } from '../components/FacebookBadge'
import { api, FACEBOOK_SYNC_TIMEOUT_MS } from '../lib/api'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { usePreferences } from '../lib/preferences'
import { PageHeader, PageState } from '../components/PageUi'

type StatusTone = 'info' | 'success' | 'error'
type SocialPlatform = 'facebook' | 'instagram' | 'threads'
type ProtectedManualField = 'accountId' | 'accessToken'
type ManualConnectionForm = {
  accountId: string
  accountName: string
  accessToken: string
}

const channelTypes = [
  { title: 'Trang Facebook', type: 'Facebook Page', icon: Smartphone, platform: 'facebook', manualPath: '/social/meta/facebook/manual' },
  { title: 'Instagram Business', type: 'Instagram Business', icon: Camera, platform: 'instagram', manualPath: '/social/instagram/manual' },
  { title: 'Threads Business', type: 'Threads', icon: Music, platform: 'threads', manualPath: '/social/threads/manual' },
] as const

type ConnectedAccount = { pageId: string; pageName: string }
const emptyManualConnection = (): ManualConnectionForm => ({
  accountId: '',
  accountName: '',
  accessToken: '',
})

export function ConnectionsPage() {
  const { t } = usePreferences()
  const { data: channels, loading, error, reload, updateData } = useAppDataResource<AppChannel[]>('/app-data/channels')
  const [connectStatus, setConnectStatus] = useState('')
  const [connectStatusTone, setConnectStatusTone] = useState<StatusTone>('info')
  const [postingPageId, setPostingPageId] = useState<string | null>(null)
  const [deletingPageId, setDeletingPageId] = useState<string | null>(null)
  const [syncingPageId, setSyncingPageId] = useState<string | null>(null)
  const [connectingType, setConnectingType] = useState<SocialPlatform | null>(null)
  const [activeManualField, setActiveManualField] = useState<string | null>(null)
  const [manualConnections, setManualConnections] = useState<Record<SocialPlatform, ManualConnectionForm>>({
    facebook: emptyManualConnection(),
    instagram: emptyManualConnection(),
    threads: emptyManualConnection(),
  })

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

  async function handleManualConnect(platform: SocialPlatform, title: string, path: string) {
    const form = manualConnections[platform]
    const accountId = form.accountId.trim()
    const accountName = form.accountName.trim()
    const accessToken = form.accessToken.trim()
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
      setActiveManualField(null)
      setConnectStatusTone('success')
      setConnectStatus(`${t('Đã kết nối')} ${response.data.pageName} (ID ${response.data.pageId}).`)
      reload()
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, `Không thể kết nối ${title}.`))
    } finally {
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
    setActiveManualField(null)
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
          const count = connectedChannels.filter((channel) => channel.type === type.type).length
          const accountIdField = manualFieldKey(type.platform, 'accountId')
          const accessTokenField = manualFieldKey(type.platform, 'accessToken')
          return (
            <div key={type.title} className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 shadow-sm">
              <div className="mb-4 flex items-center justify-between">
                <Icon className="text-blue-600" size={22} />
                <CheckCircle2 className="text-emerald-500" size={18} />
              </div>
              <h2 className="text-sm font-bold">{type.title}</h2>
              <p className={`mt-2 text-xs font-semibold ${count > 0 ? 'text-emerald-600' : 'text-[var(--muted-text)]'}`}>
                {count > 0 ? `${count} ${t('kết nối')}` : t('Chưa kết nối')}
              </p>

              <form autoComplete="off" data-form-type="other" className="mt-3 space-y-3" onSubmit={(event) => {
                event.preventDefault()
                void handleManualConnect(type.platform, type.title, type.manualPath)
              }}>
                <label htmlFor={`${type.platform}-account-id`} className="block text-xs font-semibold">
                  {type.platform === 'facebook' ? t('Page ID') : t('Account ID')}
                </label>
                <input
                  id={`${type.platform}-account-id`}
                  name={`${type.platform}-connection-id`}
                  type="text"
                  autoComplete="off"
                  spellCheck={false}
                  required
                  readOnly={!manualConnections[type.platform].accountId && activeManualField !== accountIdField}
                  value={manualConnections[type.platform].accountId}
                  onFocus={() => setActiveManualField(accountIdField)}
                  onBlur={() => setActiveManualField((field) => field === accountIdField ? null : field)}
                  onChange={(event) => setManualConnections((connections) => ({
                    ...connections,
                    [type.platform]: { ...connections[type.platform], accountId: event.target.value },
                  }))}
                  data-1p-ignore="true"
                  data-bwignore="true"
                  data-lpignore="true"
                  className="h-9 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-xs outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                  placeholder={type.platform === 'facebook' ? t('Nhập Page ID') : t('Nhập Account ID')}
                />
                <label htmlFor={`${type.platform}-account-name`} className="block text-xs font-semibold">
                  {type.platform === 'facebook' ? t('Tên Page') : t('Tên tài khoản')}
                </label>
                <input
                  id={`${type.platform}-account-name`}
                  type="text"
                  autoComplete="off"
                  required
                  value={manualConnections[type.platform].accountName}
                  onChange={(event) => setManualConnections((connections) => ({
                    ...connections,
                    [type.platform]: { ...connections[type.platform], accountName: event.target.value },
                  }))}
                  className="h-9 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-xs outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                  placeholder={type.platform === 'facebook' ? t('Nhập tên Page') : t('Nhập tên tài khoản')}
                />
                <label htmlFor={`${type.platform}-access-token`} className="block text-xs font-semibold">
                  {type.platform === 'facebook' ? t('Page Access Token') : t('Access Token')}
                </label>
                <input
                  id={`${type.platform}-access-token`}
                  name={`${type.platform}-connection-token`}
                  type="password"
                  autoComplete="new-password"
                  spellCheck={false}
                  required
                  readOnly={!manualConnections[type.platform].accessToken && activeManualField !== accessTokenField}
                  value={manualConnections[type.platform].accessToken}
                  onFocus={() => setActiveManualField(accessTokenField)}
                  onBlur={() => setActiveManualField((field) => field === accessTokenField ? null : field)}
                  onChange={(event) => setManualConnections((connections) => ({
                    ...connections,
                    [type.platform]: { ...connections[type.platform], accessToken: event.target.value },
                  }))}
                  data-1p-ignore="true"
                  data-bwignore="true"
                  data-lpignore="true"
                  className="h-9 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-xs outline-none focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20"
                  placeholder={t('Dán access token vào đây')}
                />
                <button
                  type="submit"
                  disabled={connectingType !== null}
                  className="h-9 w-full rounded-lg bg-blue-600 px-3 text-xs font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
                >
                  {connectingType === type.platform ? t('Đang kết nối...') : `${t('Kết nối')} ${type.title}`}
                </button>
              </form>
            </div>
          )
        })}
      </div>
    </section>
  )
}

function manualFieldKey(platform: SocialPlatform, field: ProtectedManualField) {
  return `${platform}:${field}`
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
