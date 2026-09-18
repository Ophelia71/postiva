import { Camera, CheckCircle2, Music, RefreshCw, Smartphone, Trash2 } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { FacebookBadge } from '../components/FacebookBadge'
import { api, FACEBOOK_SYNC_TIMEOUT_MS } from '../lib/api'
import { type AppChannel, useAppDataResource } from '../lib/appDataApi'
import { getErrorMessage } from '../lib/errors'
import { usePreferences } from '../lib/preferences'
import { PageHeader, PageState } from '../components/PageUi'

const channelTypes = [
  { title: 'Trang Facebook', type: 'Facebook Page', icon: Smartphone, available: true },
  { title: 'Instagram Business', type: 'Instagram Business', icon: Camera, available: false },
  { title: 'Thread Business', type: 'Thread', icon: Music, available: false },
]

type StatusTone = 'info' | 'success' | 'error'

export function ConnectionsPage() {
  const { t } = usePreferences()
  const { data: channels, loading, error, reload, updateData } = useAppDataResource<AppChannel[]>('/app-data/channels')
  const [pageId, setPageId] = useState('')
  const [pageName, setPageName] = useState('')
  const [pageAccessToken, setPageAccessToken] = useState('')
  const [connectStatus, setConnectStatus] = useState('')
  const [connectStatusTone, setConnectStatusTone] = useState<StatusTone>('info')
  const [postingPageId, setPostingPageId] = useState<string | null>(null)
  const [deletingPageId, setDeletingPageId] = useState<string | null>(null)
  const [syncingPageId, setSyncingPageId] = useState<string | null>(null)

  if (loading) {
    return <PageState title={t('Đang tải kênh kết nối...', )} />
  }

  if (error || !channels) {
    return <PageState title="Không thể tải danh sách kết nối" description={error ?? undefined} />
  }

  const connectedFacebookChannels = channels.filter((channel) => channel.badge === 'Facebook')

  async function handleConnect(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    setConnectStatusTone('info')
    setConnectStatus(t('Đang kết nối trang Facebook...'))

    try {
      await api.post('/social/meta/facebook/manual', {
        pageId,
        pageName,
        pageAccessToken,
      })
      setConnectStatusTone('success')
      setConnectStatus(t('Đã kết nối trang Facebook.'))
      setPageAccessToken('')
      reload()
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, 'Không thể kết nối Trang Facebook.'))
    }
  }

  async function handleTestPost(channel: AppChannel) {
    setPostingPageId(channel.accountId)
    setConnectStatusTone('info')
      setConnectStatus(`${t('Đang kiểm tra kết nối với')} ${channel.name}...`)

    try {
      await api.post('/social/meta/facebook/test-post', {
        pageId: channel.accountId,
        message: `Postiva demo: kết nối Facebook Business thành công lúc ${new Date().toLocaleString('vi-VN')}.`,
      })
      setConnectStatusTone('success')
      setConnectStatus(`${t('Kết nối hoạt động tốt trên')} ${channel.name}.`)
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, 'Không thể kiểm tra Trang Facebook này.'))
    } finally {
      setPostingPageId(null)
    }
  }

  async function handleDelete(channel: AppChannel) {
    const confirmed = window.confirm(`${t('Xóa kết nối trang Facebook')} "${channel.name}"?`)
    if (!confirmed) {
      return
    }

    setDeletingPageId(channel.accountId)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang xóa kết nối')} ${channel.name}...`)

    try {
      await api.delete(`/social/meta/accounts/${channel.id}`)
      updateData((currentChannels) => currentChannels.filter((currentChannel) => currentChannel.id !== channel.id))
      resetConnectionState()
      reload()
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, 'Không thể ngắt kết nối Trang Facebook.'))
    } finally {
      setDeletingPageId(null)
    }
  }

  async function handleSync(channel: AppChannel) {
    setSyncingPageId(channel.accountId)
    setConnectStatusTone('info')
    setConnectStatus(`${t('Đang cập nhật bài đăng từ')} ${channel.name}...`)

    try {
      const response = await api.post<{ syncedPosts: number; removedPosts?: number }>('/social/meta/facebook/sync-posts', null, {
        params: { pageId: channel.accountId },
        timeout: FACEBOOK_SYNC_TIMEOUT_MS,
      })
      const removedPosts = response.data.removedPosts ?? 0
      setConnectStatusTone('success')
      setConnectStatus(`${t('Đã cập nhật')} ${response.data.syncedPosts} ${t('bài đăng từ')} ${channel.name}.${removedPosts > 0 ? ` ${t(`Đã gỡ ${removedPosts} bài không còn trên Facebook.`)}` : ''}`)
    } catch (error) {
      setConnectStatusTone('error')
      setConnectStatus(getErrorMessage(error, 'Không thể cập nhật bài viết Facebook.'))
    } finally {
      setSyncingPageId(null)
    }
  }

  function resetConnectionState() {
    setPageId('')
    setPageName('')
    setPageAccessToken('')
    setConnectStatus('')
    setConnectStatusTone('info')
    setPostingPageId(null)
    setSyncingPageId(null)
  }

  return (
    <section className="space-y-6">
      <PageHeader title={t('Kết nối tài khoản')} />

      <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
        <section className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] shadow-sm">
          <div className="border-b border-[var(--app-border)] p-5">
            <h2 className="text-base font-bold">{t('Trang Facebook đã thêm', )} ({connectedFacebookChannels.length})</h2>
          </div>
          <div className="divide-y divide-[var(--app-border)]">
            {connectedFacebookChannels.length === 0 ? (
              <div className="px-5 py-6 text-sm text-[var(--muted-text)]">{t('Chưa có trang Facebook nào được kết nối.')}</div>
            ) : (
              connectedFacebookChannels.map((channel) => (
                <div key={channel.id} className="grid gap-4 px-5 py-4 md:grid-cols-[40px_1fr_112px_190px_40px] md:items-center">
                  <FacebookPageAvatar name={channel.name} />
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
                      disabled={syncingPageId === channel.accountId}
                      className="inline-flex h-8 items-center gap-1 rounded-lg border border-[var(--app-border)] px-3 text-xs font-bold text-[var(--muted-text)] hover:bg-[var(--soft-bg)] disabled:bg-slate-100"
                    >
                      <RefreshCw size={13} className={syncingPageId === channel.accountId ? 'animate-spin' : ''} />
                      {syncingPageId === channel.accountId ? t('Đang cập nhật') : t('Cập nhật')}
                    </button>
                    <button
                      type="button"
                      onClick={() => void handleTestPost(channel)}
                      disabled={postingPageId === channel.accountId}
                      className="h-8 rounded-lg bg-blue-600 px-3 text-xs font-bold text-white hover:bg-blue-700 disabled:bg-slate-300"
                    >
                        {postingPageId === channel.accountId ? t('Đang kiểm tra') : t('Kiểm tra')}
                    </button>
                  </div>
                  <button
                    type="button"
                    onClick={() => void handleDelete(channel)}
                    disabled={deletingPageId === channel.accountId}
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

        <form
          onSubmit={handleConnect}
          className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm"
          autoComplete="off"
        >
          <div className="mb-5">
            <h2 className="text-base font-bold">{t('Kết nối trang Facebook')}</h2>
          </div>
          <div className="space-y-4">
            <label className="block text-sm font-semibold">
              {t('ID trang Facebook')}
              <input
                name="facebook-page-id"
                value={pageId}
                onChange={(event) => setPageId(event.target.value)}
                autoComplete="off"
                inputMode="numeric"
                className="mt-2 h-11 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm outline-none focus:border-blue-500"

                required
              />
            </label>
            <label className="block text-sm font-semibold">
              {t('Tên trang')}
              <input
                name="facebook-page-name"
                value={pageName}
                onChange={(event) => setPageName(event.target.value)}
                autoComplete="off"
                className="mt-2 h-11 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm outline-none focus:border-blue-500"
                required
              />
            </label>
            <label className="block text-sm font-semibold">
              {t('Mã kết nối')}
              <input
                type="password"
                name="facebook-page-access-token"
                value={pageAccessToken}
                onChange={(event) => setPageAccessToken(event.target.value)}
                autoComplete="new-password"
                data-lpignore="true"
                data-1p-ignore="true"
                className="mt-2 h-11 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm outline-none focus:border-blue-500"

                required
              />
            </label>
          </div>
          <button className="mt-5 h-10 w-full rounded-lg bg-blue-600 px-5 text-sm font-bold text-white hover:bg-blue-700">

          </button>
          {connectStatus && (
            <p className={`mt-4 rounded-lg px-3 py-2 text-sm font-semibold ${getStatusClassName(connectStatusTone)}`}>
              {connectStatus}
            </p>
          )}
        </form>
      </div>

      <div className="grid gap-4 md:grid-cols-3">
        {channelTypes.map((type) => {
          const Icon = type.icon
          const count = type.available
            ? connectedFacebookChannels.filter((channel) => channel.type === type.type).length
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
            </div>
          )
        })}
      </div>
    </section>
  )
}

function FacebookPageAvatar({ name }: { name: string }) {
  const initial = name.trim().charAt(0).toUpperCase() || 'F'

  return (
    <span className="relative flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-amber-200 bg-amber-50 text-sm font-bold leading-none text-amber-600">
      {initial}
      <FacebookBadge className="absolute -bottom-1 -right-1 h-[18px] w-[18px] rounded-full ring-2 ring-white" />
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
