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
