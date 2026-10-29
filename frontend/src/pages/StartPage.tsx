import { Check, Link2, PenLine, Send, Store } from 'lucide-react'
import { useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { PageHeader, PageState } from '../components/PageUi'
import { type AppOverview, normalizeAppOverview, useAppDataResource } from '../lib/appDataApi'
import { dismissOnboarding, getOnboardingState, isOnboardingDismissed } from '../lib/onboarding'
import { trackOnboardingStep } from '../lib/webAnalytics'
import { isShopProfileComplete, type ShopProfile } from '../lib/shopProfile'
import { ShopProfileForm } from '../components/ShopProfileForm'
import type { AuthSession } from '../lib/auth'

type Step = {
  key: string
  title: string
  description: string
  done: boolean
  to: string
  action: string
  icon: typeof Link2
}

/** Where /app goes: new accounts and shops without a profile land on the checklist, everyone else on reports. */
export function AppIndexRedirect() {
  const { data, loading, error, reload } = useAppDataResource<AppOverview>('/app-data/overview')
  const profileState = useAppDataResource<ShopProfile | null>('/shop-profile')
  if (loading || profileState.loading) return <PageState title="Đang tải không gian làm việc..." />
  // A failed request must not silently send a brand-new user to an empty report page; let them retry.
  if (error || !data) return <OverviewError onRetry={reload} />
  // The profile is what the AI writes from, so it is asked for even after the rest of the checklist was skipped.
  if (!profileState.error && !isShopProfileComplete(profileState.data)) return <Navigate to="/app/start" replace />
  const { isSetUp } = getOnboardingState(normalizeAppOverview(data))
  return <Navigate to={isSetUp || isOnboardingDismissed() ? '/app/reports' : '/app/start'} replace />
}

export function StartPage({ session }: { session: AuthSession }) {
  const navigate = useNavigate()
  const { data, loading, error, reload } = useAppDataResource<AppOverview>('/app-data/overview')
  const profileState = useAppDataResource<ShopProfile | null>('/shop-profile')
  const [profileSkipped, setProfileSkipped] = useState(false)
  const profileDone = isShopProfileComplete(profileState.data)

  // Milestones are derived from real data, so they are recorded the first time each one is true.
  useEffect(() => {
    if (!data) return
    const progress = getOnboardingState(normalizeAppOverview(data))
    if (progress.hasChannel) trackOnboardingStep('step/connect')
    if (progress.hasPost) trackOnboardingStep('step/create')
    if (progress.hasPublishedOrScheduled) trackOnboardingStep('step/publish')
  }, [data])
  useEffect(() => {
    if (profileDone) trackOnboardingStep('step/profile')
  }, [profileDone])

  if (loading || profileState.loading) return <PageState title="Đang tải không gian làm việc..." />
  if (error || !data) return <OverviewError onRetry={reload} />

  const progress = getOnboardingState(normalizeAppOverview(data))
  const { hasChannel, hasPost, hasPublishedOrScheduled } = progress
  const doneCount = progress.doneCount + (profileDone ? 1 : 0)
  const showProfileForm = !profileDone && !profileSkipped && !profileState.error
  const steps: Step[] = [
    {
      key: 'profile',
      title: 'Thiết lập hồ sơ shop',
      description: 'Tên shop, ngành hàng và sản phẩm chính. AI dùng thông tin này cho mọi bài; số điện thoại, địa chỉ có thể thêm sau.',
      done: profileDone,
      to: '/app/shop-profile',
      action: 'Điền hồ sơ shop',
      icon: Store,
    },
    {
      key: 'connect',
      title: 'Kết nối kênh đầu tiên',
      description: 'Bắt đầu với Facebook Page. Cần Page ID và token; bấm dấu ? ở trang kết nối để xem cách lấy.',
      done: hasChannel,
      to: '/app/connections',
      action: 'Kết nối kênh',
      icon: Link2,
    },
    {
      key: 'create',
      title: 'Tạo bài đầu tiên',
      description: 'Nhập sản phẩm, để AI gợi ý nội dung cho từng nền tảng rồi chỉnh lại theo ý bạn.',
      done: hasPost,
      to: hasChannel ? '/app/create' : '/app/connections',
      action: hasChannel ? 'Tạo bài' : 'Kết nối kênh trước',
      icon: PenLine,
    },
    {
      key: 'publish',
      title: 'Đăng hoặc lên lịch',
      description: 'Đăng ngay hoặc chọn giờ. Trạng thái từng nền tảng hiện ở Lịch đăng.',
      done: hasPublishedOrScheduled,
      to: '/app/schedule',
      action: 'Xem lịch đăng',
      icon: Send,
    },
  ]
  const nextIndex = steps.findIndex((step) => !step.done)
  const allDone = doneCount === steps.length

  function finish() {
    if (!allDone) trackOnboardingStep('dismissed')
    dismissOnboarding()
    navigate('/app/reports')
  }

  return (
    <section aria-labelledby="start-heading">
      <PageHeader
        title={`Chào ${session.fullName.split(' ').slice(-1)[0] || 'bạn'}, bắt đầu thôi`}
        subtitle="Bốn việc để đăng được bài đầu tiên. Làm lần lượt; bước kết nối kênh cần chuẩn bị token từ Meta."
      />
      <p id="start-heading" className="sr-only">Danh sách thiết lập</p>
      <p className="mb-4 text-sm font-semibold text-[var(--muted-text)]" aria-live="polite">
        Đã xong {doneCount}/{steps.length}
      </p>

      {showProfileForm && (
        <div className="mb-6 rounded-2xl border border-[#8594ea] bg-[var(--panel-bg)] p-5">
          <h2 className="flex items-center gap-2 text-lg font-bold"><Store size={18} aria-hidden="true" />Bước 1: Hồ sơ shop</h2>
          <p className="mb-4 mt-1 text-sm text-[var(--muted-text)]">Chỉ 3 ô bắt buộc. Mọi thông tin khác có thể bổ sung sau ở menu tài khoản → Hồ sơ shop.</p>
          <ShopProfileForm initial={profileState.data ?? null} mode="onboarding" onSaved={() => profileState.reload()} onSkip={() => setProfileSkipped(true)} />
        </div>
      )}

      <ol className="space-y-3">
        {steps.map((step, index) => {
          const Icon = step.icon
          const isNext = index === nextIndex
          return (
            <li
              key={step.key}
              className={`flex flex-col gap-4 rounded-2xl border p-5 sm:flex-row sm:items-center ${isNext ? 'border-[#8594ea] bg-white' : 'border-[var(--app-border)] bg-[var(--panel-bg)]'}`}
            >
              <span
                className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-full ${step.done ? 'bg-emerald-100 text-emerald-700' : 'bg-[var(--soft-bg)] text-[#505db8]'}`}
                aria-hidden="true"
              >
                {step.done ? <Check size={18} /> : <Icon size={18} />}
              </span>
              <div className="min-w-0 flex-1">
                <h2 className="text-base font-bold">
                  {step.title}
                  {step.done && <span className="ml-2 text-xs font-semibold text-emerald-700">Đã xong</span>}
                </h2>
                <p className="mt-1 text-sm text-[var(--muted-text)]">{step.description}</p>
              </div>
              {!step.done && !(step.key === 'profile' && showProfileForm) && (
                <Link
                  to={step.to}
                  className={`inline-flex min-h-11 items-center justify-center rounded-full px-5 text-sm font-bold ${isNext ? 'bg-[#505db8] text-white hover:bg-[#434fa0]' : 'border border-[var(--app-border)] text-[var(--app-text)] hover:bg-[var(--soft-bg)]'}`}
                >
                  {step.action}
                </Link>
              )}
            </li>
          )
        })}
      </ol>

      <div className="mt-6 flex flex-wrap items-center gap-4">
        {allDone && (
          <button type="button" onClick={finish} className="min-h-11 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0]">
            Xem báo cáo
          </button>
        )}
        {!allDone && (
          <button type="button" onClick={finish} className="min-h-11 text-sm font-semibold text-[var(--muted-text)] underline underline-offset-4 hover:text-[var(--app-text)]">
            Để sau (mở lại ở menu tài khoản)
          </button>
        )}
      </div>
    </section>
  )
}

function OverviewError({ onRetry }: { onRetry: () => void }) {
  return (
    <div role="alert">
      <PageState title="Chưa tải được tiến độ thiết lập" description="Kiểm tra kết nối rồi thử lại." />
      <button type="button" onClick={onRetry} className="mx-auto mt-4 block min-h-11 rounded-full border border-[var(--app-border)] px-5 text-sm font-bold">
        Thử lại
      </button>
    </div>
  )
}
