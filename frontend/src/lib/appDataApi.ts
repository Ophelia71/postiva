import { useCallback, useEffect, useState } from 'react'
import { api } from './api'

export type AppChannel = {
  id: string
  accountId: string
  name: string
  type: string
  badge: string
  followers: string
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
  viewCount: number
  likeCount: number
  commentCount: number
  shareCount: number
  permalinkUrl: string
  reactionLikeCount: number
  reactionLoveCount: number
  reactionCareCount: number
  reactionHahaCount: number
  reactionWowCount: number
  reactionSadCount: number
  reactionAngryCount: number
  platformPostId: string
  mediaType: string
  mediaUrl: string
  mediaThumbnailUrl: string
  mediaTitle: string
  mediaUrls: string[]
  mediaIds: string[]
}

export type AppComment = {
  id: string
  platformCommentId: string
  parentCommentId: string
  commenterName: string
  commenterAvatarUrl: string
  message: string
  attachmentType: string
  attachmentUrl: string
  commentedAt: string | null
  likeCount: number
  replyCount: number
}

export type AppOverview = {
  totalPosts: number
  scheduledPosts: number
  publishedPosts: number
  failedPosts: number
  views: number
  likes: number
  comments: number
  shares: number
  channels: AppChannel[]
  recentPosts: AppPost[]
}

export function normalizeAppOverview(overview: Partial<AppOverview>): AppOverview {
  return {
    totalPosts: toNumber(overview.totalPosts),
    scheduledPosts: toNumber(overview.scheduledPosts),
    publishedPosts: toNumber(overview.publishedPosts),
    failedPosts: toNumber(overview.failedPosts),
    views: toNumber(overview.views),
    likes: toNumber(overview.likes),
    comments: toNumber(overview.comments),
    shares: toNumber(overview.shares),
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
