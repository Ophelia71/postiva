import type { AppOverview } from './appDataApi'

const DISMISSED_KEY = 'postiva-onboarding-dismissed'

export function isOnboardingDismissed() {
  try {
    return window.localStorage.getItem(DISMISSED_KEY) === '1'
  } catch {
    return false
  }
}

export function dismissOnboarding() {
  try {
    window.localStorage.setItem(DISMISSED_KEY, '1')
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
