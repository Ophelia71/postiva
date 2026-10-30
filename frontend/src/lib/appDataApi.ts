import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

export type AppChannel = {
  id: string
  accountId: string
  name: string
  type: string
  badge: string
  color: string
  active: boolean
}

export type AppPost = {
  id: string
  scheduleId: string
  platform: string
  content: string
  status: string
  time: string
  scheduledTime: string | null
  attemptCount: number
  nextAttemptAt: string | null
  lastError: string
  permalinkUrl: string
  platformPostId: string
  mediaType: string
  mediaUrl: string
  mediaThumbnailUrl: string
  mediaTitle: string
  mediaUrls: string[]
  mediaIds: string[]
  /** Engagement last read from the platform. A missing count means it could not be read, not zero. */
  metrics?: PostMetrics
}

export type PostMetrics = {
  likes?: number
  comments?: number
  shares?: number
  views?: number
  fetchedAt?: string
}

export type AppOverview = {
  totalPosts: number
  scheduledPosts: number
  publishedPosts: number
  failedPosts: number
  channels: AppChannel[]
  recentPosts: AppPost[]
}

export function normalizeAppOverview(overview: Partial<AppOverview>): AppOverview {
  return {
    totalPosts: toNumber(overview.totalPosts),
    scheduledPosts: toNumber(overview.scheduledPosts),
    publishedPosts: toNumber(overview.publishedPosts),
    failedPosts: toNumber(overview.failedPosts),
    channels: overview.channels ?? [],
    recentPosts: overview.recentPosts ?? [],
  }
}

function toNumber(value: unknown) {
  return typeof value === 'number' && Number.isFinite(value) ? value : 0
}

type ResourceState<T> = {
  data: T | null
  loading: boolean
  error: string | null
}

/** Fired once channels finished pulling new posts and numbers from the platforms; open pages re-read their data. */
export const CHANNELS_REFRESHED_EVENT = 'postiva:channels-refreshed'
// Reading a channel takes from a few seconds (Facebook) to about a minute (Threads, one call per post).
const REFRESH_CHECKS_MS = [15_000, 45_000, 90_000]

/**
 * Asks the server to read the shop's channels again (it skips any read in the last half hour), then has open
 * pages pick up the new numbers. Called whenever the app opens, so nobody has to press "Cập nhật".
 */
export function refreshChannelsOnOpen() {
  // React may mount the shell twice in a row; one request per opening is enough.
  if (Date.now() - lastRefreshRequest < 5_000) return
  lastRefreshRequest = Date.now()
  api.post<{ started: number }>('/social/meta/accounts/refresh-on-open')
    .then((response) => {
      if (!response.data.started) return
      REFRESH_CHECKS_MS.forEach((delay) => window.setTimeout(
        () => window.dispatchEvent(new Event(CHANNELS_REFRESHED_EVENT)), delay))
    })
    .catch(() => {
      // Best effort: the numbers from the last read stay on screen.
    })
}
let lastRefreshRequest = 0

export function useAppDataResource<T>(path: string) {
  const [state, setState] = useState<ResourceState<T>>({
    data: null,
    loading: true,
    error: null,
  })
  const [reloadIndex, setReloadIndex] = useState(0)

  const reload = useCallback(() => {
    setReloadIndex((value) => value + 1)
  }, [])

  useEffect(() => {
    window.addEventListener(CHANNELS_REFRESHED_EVENT, reload)
    return () => window.removeEventListener(CHANNELS_REFRESHED_EVENT, reload)
  }, [reload])

  const updateData = useCallback((updater: (data: T) => T) => {
    setState((current) => current.data === null
      ? current
      : { ...current, data: updater(current.data) })
  }, [])

  useEffect(() => {
    let ignore = false

    async function loadData() {
      try {
        const response = await api.get<T>(path)
        if (!ignore) {
          setState({ data: response.data, loading: false, error: null })
        }
      } catch (error) {
        if (!ignore) {
          setState({
            data: null,
            loading: false,
            error: error instanceof Error ? error.message : 'Không thể tải dữ liệu',
          })
        }
      }
    }

    void loadData()

    return () => {
      ignore = true
    }
  }, [path, reloadIndex])

  return { ...state, reload, updateData }
}
