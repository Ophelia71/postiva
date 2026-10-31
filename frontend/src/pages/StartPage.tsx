import { ArrowLeft, ArrowRight, Check, Link2, LogOut, PenLine, PlusCircle, Send, Store } from 'lucide-react'
import { type ReactNode, useEffect, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { PageState } from '../components/PageUi'
import { ShopProfileForm } from '../components/ShopProfileForm'
import { type AppOverview, normalizeAppOverview, useAppDataResource } from '../lib/appDataApi'
import type { AuthSession } from '../lib/auth'
import { dismissOnboarding, getOnboardingState, isOnboardingDismissed } from '../lib/onboarding'
import { isShopProfileComplete, type ShopProfile } from '../lib/shopProfile'
import { trackOnboardingStep } from '../lib/webAnalytics'

type StepKey = 'profile' | 'connect' | 'create' | 'publish'

type Step = {
  key: StepKey
  label: string
  title: string
  description: string
  done: boolean
  icon: typeof Link2
}

/** Where /app goes: new accounts and shops without a profile land on onboarding, everyone else on reports. */
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

/**
 * First-run onboarding: a full-screen wizard without the app menu, one step at a time. Steps that need a full
 * page (connecting a channel, writing a post) open that page; /app brings the shop back here until it is set up.
 */
export function StartPage({ session, onLogout, banner }: { session: AuthSession; onLogout: () => void; banner?: ReactNode }) {
  const navigate = useNavigate()
  const { data, loading, error, reload } = useAppDataResource<AppOverview>('/app-data/overview')
  const profileState = useAppDataResource<ShopProfile | null>('/shop-profile')
  const [active, setActive] = useState<number | null>(null)
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

  const firstName = session.fullName.split(' ').slice(-1)[0] || 'bạn'

  if (loading || profileState.loading) {
    return <OnboardingFrame onLogout={onLogout} banner={banner}><PageState title="Đang tải..." /></OnboardingFrame>
  }
  if (error || !data) {
    return <OnboardingFrame onLogout={onLogout} banner={banner}><OverviewError onRetry={reload} /></OnboardingFrame>
  }

  const { hasChannel, hasPost, hasPublishedOrScheduled, doneCount: checklistDone } = getOnboardingState(normalizeAppOverview(data))
  const steps: Step[] = [
    {
      key: 'profile',
      label: 'Hồ sơ shop',
      title: `Chào ${firstName}! Shop của bạn bán gì?`,
      description: 'Chỉ 3 ô bắt buộc. AI dùng thông tin này cho mọi bài, nên bạn không phải nhập lại mỗi lần.',
      done: profileDone,
      icon: Store,
    },
    {
      key: 'connect',
      label: 'Kết nối kênh',
      title: 'Kết nối kênh đầu tiên',
      description: 'Postiva đăng bài thay bạn lên Facebook Page, Instagram hoặc Threads. Facebook Page ổn định nhất để bắt đầu.',
      done: hasChannel,
      icon: Link2,
    },
    {
      key: 'create',
      label: 'Tạo bài',
      title: 'Tạo bài đầu tiên',
      description: 'Nhập sản phẩm, AI gợi ý nội dung riêng cho từng nền tảng, bạn chỉnh lại theo ý mình.',
      done: hasPost,
      icon: PenLine,
    },
    {
      key: 'publish',
      label: 'Đăng bài',
      title: 'Đăng hoặc lên lịch',
      description: 'Đăng ngay hoặc chọn giờ. Postiva tự đăng đúng giờ và báo trạng thái từng nền tảng ở Lịch đăng.',
      done: hasPublishedOrScheduled,
      icon: Send,
    },
  ]
  const doneCount = checklistDone + (profileDone ? 1 : 0)
  const firstOpen = steps.findIndex((step) => !step.done)
  const current = active ?? (firstOpen === -1 ? steps.length - 1 : firstOpen)
  const step = steps[current]
  const allDone = doneCount === steps.length
  const isLast = current === steps.length - 1

  function finish() {
    if (!allDone) trackOnboardingStep('dismissed')
    dismissOnboarding()
    navigate('/app/reports')
  }

  const next = () => (isLast ? finish() : setActive(current + 1))

  return (
    <OnboardingFrame onLogout={onLogout} banner={banner} onSkip={finish} stepLabel={`Bước ${current + 1}/${steps.length}`}>
      <ol aria-label="Các bước thiết lập" className="mx-auto mb-8 grid max-w-2xl grid-cols-4 gap-2">
        {steps.map((item, index) => (
          <li key={item.key}>
            <button
              type="button"
              onClick={() => setActive(index)}
              aria-current={index === current ? 'step' : undefined}
              className="group flex w-full flex-col gap-2 text-left"
            >
              <span className={`h-1.5 rounded-full transition ${item.done ? 'bg-emerald-500' : index === current ? 'bg-[#505db8]' : 'bg-[#d8ddfb]'}`} />
              <span className={`flex items-center gap-1 text-xs font-semibold ${index === current ? 'text-[var(--app-text)]' : 'text-[var(--muted-text)]'}`}>
                {item.done && <Check size={12} aria-hidden="true" className="text-emerald-600" />}
                <span className="hidden sm:inline">{item.label}</span>
                <span className="sm:hidden">{index + 1}</span>
              </span>
            </button>
          </li>
        ))}
      </ol>

      <section aria-labelledby="onboarding-step-title" className="mx-auto max-w-2xl rounded-3xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-6 shadow-xl shadow-slate-200/40 sm:p-10">
        <div className="mb-6 flex items-start gap-4">
          <span className={`flex h-12 w-12 shrink-0 items-center justify-center rounded-2xl ${step.done ? 'bg-emerald-100 text-emerald-700' : 'bg-[#eef0fd] text-[#505db8]'}`} aria-hidden="true">
            {step.done ? <Check size={22} /> : <step.icon size={22} />}
          </span>
          <div className="min-w-0">
            <p className="text-xs font-bold uppercase tracking-wide text-[#6672d8]">Bước {current + 1}</p>
            <h1 id="onboarding-step-title" className="mt-1 text-2xl font-bold tracking-tight sm:text-3xl">{step.title}</h1>
            <p className="mt-2 text-sm text-[var(--muted-text)] sm:text-base">{step.description}</p>
          </div>
        </div>

        {step.key === 'profile' && (
          profileDone && active !== current ? (
            <StepDone text={`Đã lưu hồ sơ của ${profileState.data?.businessName ?? 'shop'}. Bạn có thể sửa bất cứ lúc nào ở menu tài khoản → Hồ sơ shop.`} />
          ) : (
            <ShopProfileForm
              key={profileDone ? 'saved' : 'new'}
              initial={profileState.data ?? null}
              mode="onboarding"
              onSaved={() => { profileState.reload(); setActive(1) }}
              onSkip={() => setActive(1)}
            />
          )
        )}

        {step.key === 'connect' && (
          step.done ? <StepDone text="Đã có kênh được kết nối. Bạn có thể thêm kênh khác ở trang Kết nối." /> : (
            <StepAction
              tips={['Page ID của Facebook Page', 'Page Access Token có quyền đăng bài', 'Bấm dấu ? ở trang Kết nối để xem cách lấy từng thứ']}
              to="/app/connections"
              label="Kết nối kênh"
            />
          )
        )}

        {step.key === 'create' && (
          step.done ? <StepDone text="Bạn đã có bài đầu tiên." /> : (
            <StepAction
              tips={hasChannel
                ? ['Chọn kênh và nhập sản phẩm', 'Bấm "Tự gen bài" để AI soạn nội dung', 'Chỉnh lại câu chữ trước khi đăng']
                : ['Bạn chưa kết nối kênh: vẫn soạn thử được, nhưng cần kết nối trước khi đăng']}
              to="/app/create"
              label="Tạo bài đầu tiên"
            />
          )
        )}

        {step.key === 'publish' && (
          step.done ? <StepDone text="Đã có bài được đăng hoặc lên lịch. Mọi thứ đã sẵn sàng!" /> : (
            <StepAction
              tips={['Ở bước cuối của trang Tạo bài, bấm "Tạo bài" để đăng ngay', 'Hoặc bật Lịch đăng để chọn giờ đăng tự động']}
              to={hasPost ? '/app/schedule' : '/app/create'}
              label={hasPost ? 'Xem lịch đăng' : 'Tạo và đăng bài'}
            />
          )
        )}

        {/* The profile form has its own save and skip buttons. */}
        {!(step.key === 'profile' && (!profileDone || active === current)) && (
          <div className="mt-8 flex items-center justify-between gap-3 border-t border-[var(--app-border)] pt-6">
            {current > 0
              ? <button type="button" onClick={() => setActive(current - 1)} className="inline-flex min-h-11 items-center gap-2 rounded-full px-3 text-sm font-semibold hover:bg-[var(--soft-bg)]"><ArrowLeft size={16} aria-hidden="true" />Quay lại</button>
              : <span />}
            <button type="button" onClick={next} className={`inline-flex min-h-11 items-center gap-2 rounded-full px-5 text-sm font-bold ${step.done || isLast ? 'bg-[#505db8] text-white hover:bg-[#434fa0]' : 'border border-[var(--app-border)] hover:bg-[var(--soft-bg)]'}`}>
              {isLast ? (allDone ? 'Vào trang báo cáo' : 'Hoàn tất, làm tiếp sau') : step.done ? 'Tiếp tục' : 'Làm bước này sau'}
              <ArrowRight size={16} aria-hidden="true" />
            </button>
          </div>
        )}
      </section>

      <p className="mx-auto mt-6 max-w-2xl text-center text-xs text-[var(--muted-text)]" aria-live="polite">
        Đã xong {doneCount}/{steps.length} bước · Mở lại hướng dẫn bất cứ lúc nào ở menu tài khoản
      </p>
    </OnboardingFrame>
  )
}

/** Minimal full-screen frame: no app menu and no "create post" shortcut, only the logo and a way out. */
function OnboardingFrame({ children, onLogout, onSkip, stepLabel, banner }: {
  children: ReactNode
  onLogout: () => void
  onSkip?: () => void
  stepLabel?: string
  banner?: ReactNode
}) {
  return (
    <div className="min-h-svh bg-[radial-gradient(circle_at_top,#eef0fd,var(--app-bg)_55%)] text-[var(--app-text)]">
      <header className="mx-auto flex h-16 max-w-5xl items-center justify-between gap-3 px-4 sm:px-6">
        <span className="flex items-center gap-2 text-base font-bold tracking-tight">
          <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#6672d8] text-white"><PlusCircle size={17} aria-hidden="true" /></span>
          Postiva
        </span>
        <div className="flex items-center gap-1 sm:gap-3">
          {stepLabel && <span className="hidden text-sm font-semibold text-[var(--muted-text)] sm:inline">{stepLabel}</span>}
          {onSkip && <button type="button" onClick={onSkip} className="min-h-11 rounded-full px-3 text-sm font-semibold underline underline-offset-4 hover:bg-[var(--soft-bg)]">Để sau</button>}
          <button type="button" onClick={onLogout} aria-label="Đăng xuất" className="flex h-11 w-11 items-center justify-center rounded-full hover:bg-[var(--soft-bg)]">
            <LogOut size={17} aria-hidden="true" />
          </button>
        </div>
      </header>
      <main className="px-4 pb-16 pt-4 sm:px-6 sm:pt-8">
        {banner && <div className="mx-auto mb-6 max-w-2xl">{banner}</div>}
        {children}
      </main>
    </div>
  )
}

function StepDone({ text }: { text: string }) {
  return (
    <p role="status" className="flex items-start gap-3 rounded-2xl border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
      <Check size={18} aria-hidden="true" className="mt-0.5 shrink-0" />
      {text}
    </p>
  )
}

function StepAction({ tips, to, label }: { tips: string[]; to: string; label: string }) {
  return (
    <div className="space-y-5">
      <ul className="space-y-2 rounded-2xl bg-[var(--soft-bg)] p-4 text-sm">
        {tips.map((tip) => (
          <li key={tip} className="flex items-start gap-2"><Check size={16} aria-hidden="true" className="mt-0.5 shrink-0 text-[#505db8]" />{tip}</li>
        ))}
      </ul>
      <Link to={to} className="inline-flex min-h-12 w-full items-center justify-center gap-2 rounded-full bg-[#505db8] px-6 text-sm font-bold text-white hover:bg-[#434fa0] sm:w-auto">
        {label} <ArrowRight size={16} aria-hidden="true" />
      </Link>
      <p className="text-xs text-[var(--muted-text)]">Làm xong, mở lại hướng dẫn ở menu tài khoản → Hướng dẫn bắt đầu để đi tiếp.</p>
    </div>
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
