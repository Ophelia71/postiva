import { Link } from 'react-router-dom'
import { type AppOverview, normalizeAppOverview, useAppDataResource } from '../lib/appDataApi'
import { getOnboardingState, isOnboardingDismissed } from '../lib/onboarding'

/**
 * One-line nudge for people who skipped the first-run checklist but have a channel and no post yet.
 * It stays quiet for everyone else and never blocks the page it sits on.
 */
export function SetupReminder() {
  const { data } = useAppDataResource<AppOverview>('/app-data/overview')
  if (!data || !isOnboardingDismissed()) return null
  const state = getOnboardingState(normalizeAppOverview(data))
  if (!state.hasChannel || state.hasPublishedOrScheduled) return null
  return (
    <div role="status" className="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 sm:flex-row sm:items-center sm:justify-between">
      <p>Bạn đã kết nối kênh nhưng chưa có bài nào được đăng hoặc lên lịch.</p>
      <Link to="/app/create" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-full bg-amber-700 px-4 font-bold text-white hover:bg-amber-800">
        Tạo bài đầu tiên
      </Link>
    </div>
  )
}
