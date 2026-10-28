import {
  ArrowRight,
  BarChart3,
  CalendarClock,
  Check,
  ChevronDown,
  Radio,
  MousePointer2,
  Send,
  Sparkles,
  WandSparkles,
} from 'lucide-react'
import { useEffect, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { startDemo } from '../lib/demo'

const features = [
  {
    icon: WandSparkles,
    number: '01',
    title: 'Tạo nội dung nhanh',
    text: 'Biến một ý tưởng ngắn thành caption rõ ràng, đúng mục tiêu và sẵn sàng xuất bản.',
  },
  {
    icon: CalendarClock,
    number: '02',
    title: 'Giữ đúng nhịp đăng',
    text: 'Lên lịch, theo dõi trạng thái và quản lý toàn bộ Facebook Page trong một luồng làm việc.',
  },
  {
    icon: BarChart3,
    number: '03',
    title: 'Theo dõi trạng thái đăng',
    text: 'Xem lịch và kết quả đăng do Postiva ghi nhận. Số liệu lượt xem, tương tác và bình luận hiện chưa được đồng bộ.',
  },
]

const faqs = [
  ['Postiva hỗ trợ những công việc nào?', 'Postiva hỗ trợ tạo nội dung, kết nối tài khoản Meta bằng access token nhập thủ công, lên lịch đăng và theo dõi trạng thái đăng.'],
  ['Postiva đã đồng bộ lượt xem, tương tác và bình luận chưa?', 'Chưa. Báo cáo hiện hiển thị lịch sử và trạng thái đăng do Postiva ghi nhận. Hãy mở bài trên nền tảng để xem lượt xem, tương tác hoặc bình luận.'],
  ['Postiva cập nhật trạng thái bài đăng thế nào?', 'Lịch đăng và báo cáo tự làm mới định kỳ khi trang đang mở. Nếu kết quả từ Meta chưa rõ, bài sẽ ở trạng thái cần kiểm tra để tránh thử lại mù.'],
  ['Tôi có thể quản lý nhiều Facebook Page không?', 'Có. Bạn có thể kết nối và vận hành nhiều Facebook Business Page trong cùng workspace.'],
  ['Có thể dùng trên điện thoại không?', 'Có. Giao diện được tối ưu responsive và các hiệu ứng nặng sẽ tự giảm trên thiết bị cảm ứng hoặc khi bật Reduce Motion.'],
]

export function HomePage() {
  const [activeFaq, setActiveFaq] = useState<number | null>(0)

  useEffect(() => {
    const elements = Array.from(document.querySelectorAll<HTMLElement>('[data-reveal]'))
    const observer = new IntersectionObserver(
      (entries) => entries.forEach((entry) => entry.isIntersecting && entry.target.classList.add('is-visible')),
      { threshold: 0.14 },
    )
    elements.forEach((element) => observer.observe(element))

    let scheduled = false
    const updateScroll = () => {
      if (scheduled) return
      scheduled = true
      window.requestAnimationFrame(() => {
        document.documentElement.style.setProperty('--page-scroll', `${Math.min(window.scrollY, 1400)}`)
        scheduled = false
      })
    }
    window.addEventListener('scroll', updateScroll, { passive: true })
    updateScroll()

    return () => {
      observer.disconnect()
      window.removeEventListener('scroll', updateScroll)
      document.documentElement.style.removeProperty('--page-scroll')
    }
  }, [])

  return (
    <div className="min-h-svh overflow-hidden bg-[#f4f6fc] text-[#344260]">
      <header className="fixed inset-x-0 top-0 z-50 px-4 pt-4">
        <nav className="mx-auto flex h-16 max-w-7xl items-center justify-between rounded-full border border-[#d4d7fd]/80 bg-[#f9fbfc]/90 px-4 shadow-[0_14px_50px_rgba(71,84,117,.10)] backdrop-blur-xl sm:px-6">
          <Link to="/" className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-full bg-[#6672d8] text-[#70d2f5]">
              <Sparkles size={17} />
            </span>
            <span className="text-base font-extrabold tracking-[-.03em]">Postiva</span>
          </Link>
          <div className="hidden items-center gap-7 text-sm font-semibold text-black/60 md:flex">
            <a href="#platform" className="hover:text-black">Nền tảng</a>
            <a href="#workflow" className="hover:text-black">Quy trình</a>
            <a href="#contact" className="hover:text-black">Liên hệ</a>
          </div>
          <div className="flex items-center gap-2">
            <Link to="/app" className="hidden min-h-11 items-center px-3 text-sm font-semibold text-black/70 hover:text-black sm:inline-flex">
              Đăng nhập
            </Link>
            <Link
              to="/app?mode=register"
              className="inline-flex min-h-11 items-center gap-2 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:-translate-y-0.5 hover:bg-[#434fa0]"
            >
              Tạo tài khoản <ArrowRight size={15} aria-hidden="true" />
            </Link>
          </div>
        </nav>
      </header>

      <main>
        <section className="rescale-glow relative isolate px-5 pb-20 pt-36 sm:pt-44">
          <div className="rescale-animated-background" aria-hidden="true">
            <span className="rescale-orb rescale-orb-cyan" />
            <span className="rescale-orb rescale-orb-lavender" />
            <span className="rescale-orb rescale-orb-pink" />
            <span className="rescale-orb rescale-orb-peach" />
          </div>
          <div className="mx-auto max-w-7xl text-center">
            <h1 data-reveal className="reveal-up rescale-text-reveal mx-auto max-w-5xl text-balance text-[clamp(2.5rem,6vw,5.5rem)] font-medium leading-[1] tracking-[-.03em]">
              Đăng bài đều đặn,
              <span className="block text-[#505db8]">không cần ngồi canh giờ.</span>
            </h1>
            <p data-reveal className="reveal-up mx-auto mt-8 max-w-2xl text-base leading-7 text-[#4a56a0] sm:text-lg">
              Soạn nội dung với gợi ý từ AI, lên lịch đăng lên Facebook Page và xem bài nào đã đăng, bài nào cần xử lý.
              Instagram và Threads đang thử nghiệm.
            </p>
            <div data-reveal className="reveal-up mt-9 flex flex-wrap justify-center gap-3">
              <Link to="/app/reports" onClick={startDemo} className="inline-flex min-h-12 items-center gap-2 rounded-full bg-[#505db8] px-7 text-sm font-bold text-white hover:-translate-y-1 hover:bg-[#434fa0]">
                Dùng thử ngay <ArrowRight size={16} aria-hidden="true" />
              </Link>
              <Link to="/app?mode=register" className="inline-flex min-h-12 items-center rounded-full border border-[#b9bfee] bg-white/60 px-7 text-sm font-bold hover:bg-white">
                Tạo tài khoản
              </Link>
            </div>
            <p data-reveal className="reveal-up mt-3 text-sm text-black/60">Không cần đăng ký, xem ngay với dữ liệu mẫu.</p>

            <ReportsPreview />
          </div>
        </section>

        <section className="border-y border-[#e4e3fd] bg-[#f9fbfc] py-8">
          <div className="mx-auto grid max-w-7xl grid-cols-2 gap-6 px-5 text-center md:grid-cols-4">
            {['Tạo nội dung', 'Lịch đăng', 'Trạng thái bài', 'Facebook Page'].map((label) => (
              <p key={label} className="text-sm font-bold text-[#4a56a0]">{label}</p>
            ))}
          </div>
        </section>

        <section id="platform" className="bg-[#47599f] px-5 py-24 text-white sm:py-32">
          <div className="mx-auto max-w-7xl">
            <div className="grid gap-8 lg:grid-cols-[1fr_420px] lg:items-end">
              <h2 data-reveal className="reveal-up max-w-4xl text-balance text-4xl font-medium leading-[1] tracking-[-.03em] sm:text-6xl">
                Từ ý tưởng đến bài đăng được quản lý.
              </h2>
              <p data-reveal className="reveal-up leading-7 text-white">
                Postiva quản lý nội dung, lịch đăng và kết quả đăng trong cùng một hệ thống. Lượt xem, tương tác và bình luận chưa được đồng bộ.
              </p>
            </div>

            <div className="mt-16 grid gap-4 lg:grid-cols-3">
              {features.map((feature) => {
                const Icon = feature.icon
                return (
                  <article data-reveal key={feature.title} className="reveal-up rescale-bento-card group rounded-[30px] border border-white/20 bg-white/[.10] p-7 hover:-translate-y-1 hover:border-[#d4d7fd] hover:bg-white/[.15]">
                    <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#67caef] text-[#344260]"><Icon size={21} aria-hidden="true" /></span>
                    <h3 className="mt-20 text-2xl font-semibold tracking-[-.02em]">{feature.title}</h3>
                    <p className="mt-3 leading-7 text-white">{feature.text}</p>
                  </article>
                )
              })}
            </div>
          </div>
        </section>

        <section id="workflow" className="bg-[#f8f9fc] px-5 py-24 sm:py-32">
          <div className="mx-auto grid max-w-7xl gap-14 lg:grid-cols-[.9fr_1.1fr] lg:items-start">
            <div className="lg:sticky lg:top-28">
              <h2 data-reveal className="reveal-up text-balance text-5xl font-medium leading-[1] tracking-[-.03em] sm:text-7xl">
                Ít thao tác.<br />Nhiều kết quả.
              </h2>
              <p data-reveal className="reveal-up mt-7 max-w-lg leading-7 text-[#4a56a0]">
                Giao diện được thiết kế quanh công việc thực tế: kết nối Page, tạo bài, chọn lịch và theo dõi trạng thái đăng.
              </p>
              <div data-reveal className="reveal-up mt-8 space-y-3">
                <CheckLine text="Gợi ý nội dung bằng AI cho từng nền tảng" />
                <CheckLine text="Quản lý nhiều Facebook Page" />
                <CheckLine text="Theo dõi trạng thái và lịch sử đăng" />
                <CheckLine text="Báo cáo lịch đăng, không gồm số liệu tương tác" />
              </div>
            </div>
            <div data-reveal className="reveal-scale rescale-scroll-card relative rounded-[36px] bg-[linear-gradient(135deg,#d4d7fd,#e4e3fd_48%,#c2cbf6)] p-5 sm:p-9">
              <div className="absolute -right-8 -top-8 h-28 w-28 rounded-full bg-[#84d2f4]/70 blur-2xl" />
              <div className="relative rounded-[26px] bg-white p-5 shadow-[0_30px_90px_rgba(32,40,17,.18)]">
                <div className="flex items-center justify-between border-b border-black/8 pb-4">
                  <div><p className="text-xs font-bold text-black/65">Bản minh họa · không phải lịch tài khoản</p><p className="mt-1 font-bold">Bài viết mẫu</p></div>
                  <span className="rounded-full bg-[#e4e3fd] px-3 py-1 text-xs font-bold text-[#475475]">Bản xem trước</span>
                </div>
                <div className="mt-5 rounded-2xl bg-[#f3f0fd] p-5">
                  <div className="h-44 rounded-xl bg-[linear-gradient(135deg,#3f55a6,#4c62b3_55%,#3f86b5)] p-5 text-white">
                    <p className="mt-16 text-3xl font-semibold tracking-[-.02em]">Nhẹ nhàng.<br />Đúng chất bạn.</p>
                  </div>
                  <p className="mt-4 text-sm leading-6 text-black/70">Đây là nội dung minh họa giao diện, không phải bài của người dùng.</p>
                </div>
                <div className="mt-4 flex items-center justify-between">
                  <span className="flex items-center gap-2 text-xs font-bold text-black/65"><Radio size={16} /> Facebook Business</span>
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#4c58b8] text-white"><Send size={16} /></span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="insights" className="bg-[#f3f0fd] px-5 py-24 sm:py-32">
          <div className="mx-auto max-w-7xl">
            <div data-reveal className="reveal-up flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <h2 className="max-w-3xl text-balance text-4xl font-medium leading-[1] tracking-[-.03em] sm:text-6xl">Thông tin rõ ràng trước khi bạn kết nối.</h2>
              </div>
            </div>
            <div data-reveal className="reveal-scale mt-14 grid gap-4 md:grid-cols-3">
              <ScopeCard title="Kết nối Meta" text="Tài khoản Facebook Page hiện được kết nối bằng Page Access Token nhập thủ công. OAuth tự phục vụ chưa khả dụng." />
              <ScopeCard title="Báo cáo" text="Hiển thị lịch sử đăng, trạng thái và lỗi do Postiva ghi nhận. Lượt xem, tương tác và bình luận chưa được đồng bộ." />
              <ScopeCard title="Kết quả chưa rõ" text="Bài đăng có thể chuyển sang trạng thái cần kiểm tra. Hãy xác minh trên nền tảng trước khi yêu cầu thử lại." />
            </div>
          </div>
        </section>

        <section id="faq" className="bg-[#f8f9fc] px-5 py-24 sm:py-32">
          <div className="mx-auto grid max-w-7xl gap-12 lg:grid-cols-[.75fr_1.25fr]">
            <div data-reveal className="reveal-up lg:sticky lg:top-28 lg:self-start">
              <h2 className="text-5xl font-medium tracking-[-.03em]">Câu hỏi thường gặp.</h2>
              <p className="mt-5 max-w-sm leading-7 text-[#4a56a0]">Thông tin ngắn gọn để bạn hiểu cách Postiva vận hành trước khi bắt đầu.</p>
            </div>
            <div data-reveal className="reveal-up divide-y divide-[#d4d7fd] border-y border-[#d4d7fd]">
              {faqs.map(([question, answer], index) => {
                const isOpen = activeFaq === index
                return (
                  <div key={question}>
                    <button type="button" onClick={() => setActiveFaq(isOpen ? null : index)} className="flex w-full items-center justify-between gap-6 py-6 text-left text-lg font-semibold text-[#344260]">
                      {question}
                      <ChevronDown size={20} className={`shrink-0 text-[#4a56a0] transition-transform duration-300 ${isOpen ? 'rotate-180' : ''}`} />
                    </button>
                    <div className={`rescale-accordion-grid ${isOpen ? 'is-open' : ''}`}>
                      <div><p className="max-w-2xl pb-6 leading-7 text-[#4a56a0]">{answer}</p></div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </section>

        <section id="contact" className="px-5 pb-5">
          <div data-reveal className="reveal-scale mx-auto max-w-7xl overflow-hidden rounded-[36px] bg-[linear-gradient(120deg,#3f55a6,#4c62b3_55%,#5a6fc0)] px-6 py-20 text-center sm:px-12 sm:py-28">
            <h2 className="mx-auto max-w-4xl text-balance text-5xl font-medium leading-[1] tracking-[-.03em] text-white sm:text-7xl">
              Đăng bài đầu tiên trong vài phút.
            </h2>
            <p className="mx-auto mt-5 max-w-xl text-lg text-white">Tạo tài khoản, kết nối một Facebook Page và để Postiva lo phần lên lịch.</p>
            <Link to="/app?mode=register" className="mt-9 inline-flex min-h-12 items-center gap-2 rounded-full bg-white px-7 text-sm font-bold text-[#344260] hover:-translate-y-1 hover:bg-[#f3f0fd]">
              Tạo tài khoản <ArrowRight size={16} aria-hidden="true" />
            </Link>
          </div>
          <footer className="relative mt-5 overflow-hidden rounded-t-[36px] bg-[#47599f] px-6 pb-8 pt-16 text-white sm:px-10 lg:px-16 lg:pt-24">
            <div className="relative mx-auto grid max-w-7xl gap-14 lg:grid-cols-[1.15fr_1fr_.75fr_.75fr]">
              <div>
                <p className="text-5xl font-semibold tracking-[-.065em] sm:text-7xl">postiva<sup className="ml-1 align-top text-sm">®</sup></p>
                <p className="mt-5 max-w-xs text-lg leading-8 text-white">Tạo nội dung, lên lịch và theo dõi trạng thái đăng trong một workspace.</p>
              </div>

              <div>
                <h3 className="text-3xl font-medium tracking-[-.02em]">Bắt đầu với Postiva</h3>
                <p className="mt-3 max-w-sm leading-7 text-white">Postiva đang trong giai đoạn thử nghiệm giới hạn. Tạo tài khoản để dùng ngay.</p>
                <Link to="/app?mode=register" className="mt-6 inline-flex min-h-11 items-center gap-2 font-semibold text-white hover:underline">Tạo tài khoản <ArrowRight size={16} aria-hidden="true" /></Link>
              </div>

              <FooterLinks title="Sản phẩm" links={[["Trang chủ", "#"], ["Tính năng", "#platform"], ["Cách hoạt động", "#workflow"], ["Phạm vi hiện tại", "#insights"], ["FAQ", "#faq"]]} />
              <FooterLinks title="Postiva" links={[["Về Postiva", "#platform"], ["Phạm vi hỗ trợ", "#insights"], ["Đăng nhập", "/app"], ["Chính sách riêng tư", "/privacy-policy"]]} />
            </div>
            <div className="relative mx-auto mt-16 flex max-w-7xl flex-col gap-3 border-t border-white/18 pt-6 text-sm text-white sm:flex-row sm:items-center sm:justify-between">
              <span>Copyright © Postiva 2026</span>
              <span>Nội dung · Lịch đăng · Trạng thái đăng</span>
              <span>Dành cho chủ shop và người làm nội dung</span>
            </div>
          </footer>
        </section>
      </main>
    </div>
  )
}

function ReportsPreview() {
  return (
    <div className="rescale-float relative mx-auto mt-16 max-w-5xl rounded-[30px] border border-black/10 bg-white/75 p-3 text-left shadow-[0_35px_120px_rgba(25,30,20,.16)] backdrop-blur sm:p-5">
      <div className="grid overflow-hidden rounded-[22px] border border-[#d4d7fd] bg-[#f9fbfc] md:grid-cols-[190px_1fr]">
        <div className="hidden border-r border-white/20 bg-[#455ab0] p-5 text-white md:block">
            <p className="font-bold">Postiva</p>
            <p className="mt-1 text-xs text-white">Bản xem trước</p>
            <div className="mt-10 space-y-2 text-xs font-semibold text-white">
              <PreviewNav icon={<MousePointer2 size={14} />} text="Tạo bài" />
              <PreviewNav icon={<CalendarClock size={14} />} text="Lịch đăng" />
              <PreviewNav active icon={<BarChart3 size={14} />} text="Báo cáo" />
          </div>
        </div>
        <div className="p-5 sm:p-7">
          <div className="flex items-center justify-between"><div><p className="text-xs font-bold text-[#5d6490]">BẢN XEM TRƯỚC · KHÔNG CÓ DỮ LIỆU TÀI KHOẢN</p><p className="mt-1 text-xl font-bold">Lịch sử bài đăng</p></div><span className="rounded-full bg-[#4c58b8] px-4 py-2 text-xs font-bold text-white">+ Tạo bài</span></div>
          <div className="mt-3 grid gap-3 lg:grid-cols-[1.5fr_1fr]">
            <div className="rounded-2xl border border-[#e4e3fd] bg-white p-4"><p className="text-sm font-bold">Trạng thái trong báo cáo</p><div className="mt-4 space-y-3"><PreviewStatus label="Đã lên lịch" tone="blue" /><PreviewStatus label="Đã đăng" tone="green" /><PreviewStatus label="Cần kiểm tra" tone="amber" /></div></div>
            <div className="rounded-2xl bg-[#455ab0] p-4 text-white"><p className="text-sm font-bold">Thông tin được theo dõi</p><div className="mt-4 space-y-3 text-xs leading-5 text-white"><p>Lịch đăng và trạng thái bài</p><p>Lỗi gần nhất và số lần thử</p><p>Lượt xem, tương tác, bình luận: chưa đồng bộ</p></div></div>
          </div>
        </div>
      </div>
    </div>
  )
}

function PreviewNav({ icon, text, active = false }: { icon: ReactNode; text: string; active?: boolean }) {
  return <div className={`flex items-center gap-2 rounded-lg px-3 py-2 ${active ? 'bg-[#d4d7fd] text-[#475475]' : ''}`}>{icon}{text}</div>
}

function ScopeCard({ title, text }: { title: string; text: string }) {
  return <article className="rounded-[28px] border border-[#d4d7fd] bg-[#f9fbfc] p-7 shadow-[0_24px_80px_rgba(102,115,185,.08)]"><Sparkles className="text-[#6672d8]" size={24} /><h3 className="mt-6 text-xl font-bold">{title}</h3><p className="mt-3 leading-7 text-[#4a56a0]">{text}</p></article>
}

function PreviewStatus({ label, tone }: { label: string; tone: 'blue' | 'green' | 'amber' }) {
  const colors = { blue: 'bg-blue-50 text-blue-700', green: 'bg-emerald-50 text-emerald-700', amber: 'bg-amber-50 text-amber-800' }
  return <div className="flex items-center justify-between rounded-xl border border-[#e4e3fd] px-3 py-2 text-xs"><span className="font-medium text-[#475475]">Bài mẫu</span><span className={`rounded-full px-2.5 py-1 font-bold ${colors[tone]}`}>{label}</span></div>
}

function CheckLine({ text }: { text: string }) {
  return <div className="flex items-center gap-3 font-semibold"><span className="flex h-7 w-7 items-center justify-center rounded-full bg-[#e4e3fd]"><Check size={14} /></span>{text}</div>
}

function FooterLinks({ title, links }: { title: string; links: Array<[string, string]> }) {
  return (
    <div>
      <p className="text-xs font-bold uppercase tracking-[.18em] text-white">{title}</p>
      <nav className="mt-6 flex flex-col gap-4">
        {links.map(([label, href]) => <a key={label} href={href} className="w-fit text-base text-white/82 hover:translate-x-1 hover:text-white">{label}</a>)}
      </nav>
    </div>
  )
}
