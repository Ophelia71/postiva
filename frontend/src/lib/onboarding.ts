import type { AppOverview } from './appDataApi'

/** Same key as AUTH_STORAGE_KEY in auth.ts; read directly so this module stays dependency-free for its tests. */
const AUTH_KEY = 'postiva.auth'

function currentUserId() {
  try {
    const session = JSON.parse(window.localStorage.getItem(AUTH_KEY) ?? 'null') as { userId?: unknown } | null
    return typeof session?.userId === 'string' ? session.userId : 'anonymous'
  } catch {
    return 'anonymous'
  }
}

/** Per account: skipping the checklist on one account must not hide it from the next one on the same browser. */
function dismissedKey() {
  return `postiva-onboarding-dismissed:${currentUserId()}`
}

export function isOnboardingDismissed() {
  try {
    return window.localStorage.getItem(dismissedKey()) === '1'
  } catch {
    return false
  }
}

export function dismissOnboarding() {
  try {
    window.localStorage.setItem(dismissedKey(), '1')
  } catch {
    // Storage can be blocked; the checklist is derived from account data anyway.
  }
}

export type OnboardingState = {
  hasChannel: boolean
  hasPost: boolean
  hasPublishedOrScheduled: boolean
  doneCount: number
  /** One definition shared by the checklist and the /app redirect: a channel and a post that was published or scheduled. */
  isSetUp: boolean
}

/** Derives checklist progress from normalized overview data (pure, so it can be unit tested). */
export function getOnboardingState(overview: AppOverview): OnboardingState {
  const hasChannel = overview.channels.some((channel) => channel.active)
  const hasPost = overview.totalPosts > 0
  const hasPublishedOrScheduled = overview.publishedPosts + overview.scheduledPosts > 0
  return {
    hasChannel,
    hasPost,
    hasPublishedOrScheduled,
    doneCount: [hasChannel, hasPost, hasPublishedOrScheduled].filter(Boolean).length,
    isSetUp: hasChannel && hasPublishedOrScheduled,
  }
}
