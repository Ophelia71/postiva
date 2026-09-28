import {
  ArrowRight,
  BarChart3,
  CalendarClock,
  Check,
  ChevronDown,
  ChevronLeft,
  ChevronRight,
  Radio,
  MessageCircle,
  MousePointer2,
  Send,
  Sparkles,
  WandSparkles,
} from 'lucide-react'
import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

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
    title: 'Đọc hiệu quả thật',
    text: 'Xem bài đăng, lượt xem, tương tác và bình luận để biết nội dung nào đang hoạt động tốt.',
  },
]

const testimonials = [
  {
    quote: 'Postiva giúp đội ngũ nhìn thấy toàn bộ nội dung, lịch đăng và phản hồi khách hàng trong cùng một nhịp làm việc.',
    name: 'Minh Anh',
    role: 'Founder, Lumen Studio',
  },
  {
    quote: 'Chúng tôi giảm đáng kể thời gian chuẩn bị bài nhưng vẫn giữ được giọng thương hiệu và lịch xuất bản ổn định.',
    name: 'Hoàng Nam',
    role: 'Marketing Lead, Nova Retail',
  },
  {
    quote: 'Báo cáo rõ, bình luận cập nhật nhanh và quy trình đủ đơn giản để cả đội sử dụng ngay từ ngày đầu.',
    name: 'Thảo Vy',
    role: 'Operations, Orbit Commerce',
  },
]

const faqs = [
  ['Postiva hỗ trợ những công việc nào?', 'Postiva tập trung vào tạo nội dung, lên lịch Facebook Business, theo dõi bài đăng, bình luận và báo cáo hiệu quả.'],
  ['Dữ liệu có cập nhật theo thời gian thực không?', 'Bình luận và các sự kiện liên quan được cập nhật qua kết nối realtime khi hệ thống và Facebook Page đang hoạt động.'],
  ['Tôi có thể quản lý nhiều Facebook Page không?', 'Có. Bạn có thể kết nối và vận hành nhiều Facebook Business Page trong cùng workspace.'],
  ['Có thể dùng trên điện thoại không?', 'Có. Giao diện được tối ưu responsive và các hiệu ứng nặng sẽ tự giảm trên thiết bị cảm ứng hoặc khi bật Reduce Motion.'],
]

export function HomePage() {
  const [activeFaq, setActiveFaq] = useState<number | null>(0)
  const [testimonialIndex, setTestimonialIndex] = useState(0)

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
          <Link
            to="/app/reports"
            className="inline-flex h-10 items-center gap-2 rounded-full bg-[#6672d8] px-5 text-sm font-bold text-white hover:-translate-y-0.5 hover:bg-[#505db8]"
          >
            Mở báo cáo <ArrowRight size={15} />
          </Link>
        </nav>
      </header>

      <main>
        <section className="rescale-grid rescale-glow relative isolate px-5 pb-20 pt-36 sm:pt-44">
          <div className="rescale-animated-background" aria-hidden="true">
            <span className="rescale-orb rescale-orb-cyan" />
            <span className="rescale-orb rescale-orb-lavender" />
            <span className="rescale-orb rescale-orb-pink" />
            <span className="rescale-orb rescale-orb-peach" />
          </div>
          <div className="mx-auto max-w-7xl text-center">
            <div data-reveal className="reveal-up mx-auto inline-flex items-center gap-2 rounded-full border border-[#d4d7fd] bg-white/65 px-4 py-2 text-xs font-bold uppercase tracking-[.16em] text-[#6673b9] backdrop-blur">
              <span className="h-2 w-2 rounded-full bg-[#84d2f4]" />
              Social operations, simplified
            </div>
            <h1 data-reveal className="reveal-up rescale-text-reveal mx-auto mt-8 max-w-5xl text-[clamp(3.4rem,8vw,7.6rem)] font-medium leading-[.88] tracking-[-.075em]">
              Nội dung chạy đều.
              <span className="rescale-gradient-text block">Tăng trưởng rõ ràng.</span>
            </h1>
            <p data-reveal className="reveal-up mx-auto mt-8 max-w-2xl text-base leading-7 text-[#6673b9] sm:text-lg">
              Một workspace tinh gọn để tạo nội dung, lên lịch Facebook, phản hồi khách hàng
              và đọc hiệu quả kinh doanh mà không phải chuyển qua nhiều công cụ.
            </p>
            <div data-reveal className="reveal-up mt-9 flex flex-wrap justify-center gap-3">
              <Link to="/app/reports" className="inline-flex h-13 items-center gap-2 rounded-full bg-[#6672d8] px-7 text-sm font-bold text-white hover:-translate-y-1 hover:bg-[#505db8]">
                Bắt đầu vận hành <ArrowRight size={16} />
              </Link>
              <a href="#platform" className="inline-flex h-13 items-center rounded-full border border-[#d4d7fd] bg-white/60 px-7 text-sm font-bold hover:bg-white">
                Xem cách hoạt động
              </a>
            </div>

            <ReportsPreview />
          </div>
        </section>

        <section className="border-y border-[#e4e3fd] bg-[#f9fbfc] py-8">
          <div className="mx-auto grid max-w-7xl grid-cols-2 gap-6 px-5 text-center md:grid-cols-4">
            <AnimatedStat value={12} suffix="K+" label="Doanh nghiệp tăng trưởng" />
            <AnimatedStat value={96} suffix="%" label="Tỷ lệ vận hành ổn định" />
            <AnimatedStat value={4} suffix="x" label="Nhanh hơn khi xuất bản" />
            <AnimatedStat value={24} suffix="/7" label="Theo dõi hoạt động" />
          </div>
        </section>

        <section id="platform" className="bg-[#5369b8] px-5 py-24 text-white sm:py-32">
          <div className="mx-auto max-w-7xl">
            <p data-reveal className="reveal-up text-xs font-bold uppercase tracking-[.2em] text-[#89cefa]">Một nền tảng, trọn quy trình</p>
            <div className="mt-5 grid gap-8 lg:grid-cols-[1fr_420px] lg:items-end">
              <h2 data-reveal className="reveal-up max-w-4xl text-4xl font-medium leading-[.98] tracking-[-.055em] sm:text-6xl">
                Từ ý tưởng đến cuộc trò chuyện với khách hàng.
              </h2>
              <p data-reveal className="reveal-up leading-7 text-white/70">
                Postiva quản lý nội dung, lịch đăng, bình luận và báo cáo trong cùng một hệ thống để bạn và đội ngũ luôn nhìn thấy bước tiếp theo.
              </p>
            </div>

            <div className="mt-16 grid gap-4 lg:grid-cols-3">
              {features.map((feature) => {
                const Icon = feature.icon
                return (
                  <article data-reveal key={feature.title} className="reveal-up rescale-bento-card group rounded-[30px] border border-white/20 bg-white/[.10] p-7 hover:-translate-y-1 hover:border-[#d4d7fd] hover:bg-white/[.15]">
                    <div className="flex items-center justify-between">
                      <span className="flex h-12 w-12 items-center justify-center rounded-full bg-[#67caef] text-[#344260]"><Icon size={21} /></span>
                      <span className="text-sm font-bold text-white/28">{feature.number}</span>
                    </div>
                    <h3 className="mt-20 text-2xl font-semibold tracking-[-.03em]">{feature.title}</h3>
                    <p className="mt-3 leading-7 text-white/70">{feature.text}</p>
                  </article>
                )
              })}
            </div>
          </div>
        </section>

        <section id="workflow" className="bg-[#f8f9fc] px-5 py-24 sm:py-32">
          <div className="mx-auto grid max-w-7xl gap-14 lg:grid-cols-[.9fr_1.1fr] lg:items-start">
            <div className="lg:sticky lg:top-28">
              <p data-reveal className="reveal-up text-xs font-bold uppercase tracking-[.2em] text-[#8a8fbb]">Đơn giản từ ngày đầu</p>
              <h2 data-reveal className="reveal-up mt-5 text-5xl font-medium leading-[.96] tracking-[-.06em] sm:text-7xl">
                Ít thao tác.<br />Nhiều kết quả.
              </h2>
              <p data-reveal className="reveal-up mt-7 max-w-lg leading-7 text-[#6673b9]">
                Giao diện được thiết kế quanh công việc thực tế: kết nối Page, tạo bài, chọn lịch và theo dõi phản hồi.
              </p>
              <div data-reveal className="reveal-up mt-8 space-y-3">
                <CheckLine text="Quản lý nhiều Facebook Page" />
                <CheckLine text="Bình luận và phản hồi theo thời gian thực" />
                <CheckLine text="Báo cáo tập trung, dễ đọc" />
              </div>
            </div>
            <div data-reveal className="reveal-scale rescale-scroll-card relative rounded-[36px] bg-[linear-gradient(135deg,#d4d7fd,#e4e3fd_48%,#c2cbf6)] p-5 sm:p-9">
              <div className="absolute -right-8 -top-8 h-28 w-28 rounded-full bg-[#84d2f4]/70 blur-2xl" />
              <div className="relative rounded-[26px] bg-white p-5 shadow-[0_30px_90px_rgba(32,40,17,.18)]">
                <div className="flex items-center justify-between border-b border-black/8 pb-4">
                  <div><p className="text-xs font-bold text-black/38">Bài đăng tiếp theo</p><p className="mt-1 font-bold">BST mùa hè 2026</p></div>
                  <span className="rounded-full bg-[#e4e3fd] px-3 py-1 text-xs font-bold text-[#475475]">Đã sẵn sàng</span>
                </div>
                <div className="mt-5 rounded-2xl bg-[#f3f0fd] p-5">
                  <div className="h-44 rounded-xl bg-[linear-gradient(135deg,#6b7dc9,#8594ea_55%,#84d2f4)] p-5 text-white">
                    <p className="text-xs uppercase tracking-[.2em] text-white/55">Summer edit</p>
                    <p className="mt-16 text-3xl font-semibold tracking-[-.04em]">Nhẹ nhàng.<br />Đúng chất bạn.</p>
                  </div>
                  <p className="mt-4 text-sm leading-6 text-black/55">Khám phá bộ sưu tập mới với ưu đãi dành riêng cho khách hàng của chúng tôi.</p>
                </div>
                <div className="mt-4 flex items-center justify-between">
                  <span className="flex items-center gap-2 text-xs font-bold text-black/48"><Radio size={16} /> Facebook Business</span>
                  <span className="flex h-10 w-10 items-center justify-center rounded-full bg-[#757bd4] text-white"><Send size={16} /></span>
                </div>
              </div>
            </div>
          </div>
        </section>

        <section id="insights" className="bg-[#f3f0fd] px-5 py-24 sm:py-32">
          <div className="mx-auto max-w-7xl">
            <div data-reveal className="reveal-up flex flex-col gap-6 sm:flex-row sm:items-end sm:justify-between">
              <div>
                <p className="text-xs font-bold uppercase tracking-[.2em] text-[#8a8fbb]">Client insight</p>
                <h2 className="mt-4 max-w-3xl text-4xl font-medium leading-[1] tracking-[-.05em] sm:text-6xl">Được xây dựng quanh cách đội ngũ thực sự làm việc.</h2>
              </div>
              <div className="flex gap-2">
                <button type="button" onClick={() => setTestimonialIndex((value) => (value - 1 + testimonials.length) % testimonials.length)} aria-label="Nhận xét trước" className="flex h-12 w-12 items-center justify-center rounded-full border border-[#c2cbf6] bg-white text-[#6673b9] hover:bg-[#e4e3fd]"><ChevronLeft size={18} /></button>
                <button type="button" onClick={() => setTestimonialIndex((value) => (value + 1) % testimonials.length)} aria-label="Nhận xét tiếp theo" className="flex h-12 w-12 items-center justify-center rounded-full bg-[#6672d8] text-white hover:bg-[#505db8]"><ChevronRight size={18} /></button>
              </div>
            </div>
            <div data-reveal className="reveal-scale mt-14 overflow-hidden rounded-[36px] border border-[#d4d7fd] bg-[#f9fbfc] p-7 shadow-[0_24px_80px_rgba(102,115,185,.12)] sm:p-12">
              <div key={testimonialIndex} className="rescale-slide-enter">
                <Sparkles className="text-[#84d2f4]" size={30} />
                <blockquote className="mt-9 max-w-5xl text-3xl font-medium leading-[1.16] tracking-[-.04em] text-[#344260] sm:text-5xl">“{testimonials[testimonialIndex].quote}”</blockquote>
                <div className="mt-10 border-t border-[#e4e3fd] pt-6"><p className="font-semibold">{testimonials[testimonialIndex].name}</p><p className="mt-1 text-sm text-[#8a8fbb]">{testimonials[testimonialIndex].role}</p></div>
              </div>
            </div>
          </div>
        </section>

        <section id="faq" className="bg-[#f8f9fc] px-5 py-24 sm:py-32">
          <div className="mx-auto grid max-w-7xl gap-12 lg:grid-cols-[.75fr_1.25fr]">
            <div data-reveal className="reveal-up lg:sticky lg:top-28 lg:self-start">
              <p className="text-xs font-bold uppercase tracking-[.2em] text-[#8a8fbb]">FAQ</p>
              <h2 className="mt-4 text-5xl font-medium tracking-[-.055em]">Câu hỏi thường gặp.</h2>
              <p className="mt-5 max-w-sm leading-7 text-[#6673b9]">Thông tin ngắn gọn để bạn hiểu cách Postiva vận hành trước khi bắt đầu.</p>
            </div>
            <div data-reveal className="reveal-up divide-y divide-[#d4d7fd] border-y border-[#d4d7fd]">
              {faqs.map(([question, answer], index) => {
                const isOpen = activeFaq === index
                return (
                  <div key={question}>
                    <button type="button" onClick={() => setActiveFaq(isOpen ? null : index)} className="flex w-full items-center justify-between gap-6 py-6 text-left text-lg font-semibold text-[#344260]">
                      {question}
                      <ChevronDown size={20} className={`shrink-0 text-[#6673b9] transition-transform duration-300 ${isOpen ? 'rotate-180' : ''}`} />
                    </button>
                    <div className={`rescale-accordion-grid ${isOpen ? 'is-open' : ''}`}>
                      <div><p className="max-w-2xl pb-6 leading-7 text-[#6673b9]">{answer}</p></div>
                    </div>
                  </div>
                )
              })}
            </div>
          </div>
        </section>

        <section id="contact" className="px-5 pb-5">
          <div data-reveal className="reveal-scale mx-auto max-w-7xl overflow-hidden rounded-[36px] bg-[linear-gradient(120deg,#63c8ef,#7180df_55%,#aeb9ef)] px-6 py-20 text-center sm:px-12 sm:py-28">
            <p className="text-xs font-bold uppercase tracking-[.2em] text-[#4f5b7c]">Sẵn sàng bắt đầu?</p>
            <h2 className="mx-auto mt-5 max-w-4xl text-5xl font-medium leading-[.95] tracking-[-.065em] text-white sm:text-7xl">
              Biến nội dung thành một hệ thống tăng trưởng.
            </h2>
            <Link to="/app" className="mt-9 inline-flex h-13 items-center gap-2 rounded-full bg-[#5662c0] px-7 text-sm font-bold text-white hover:-translate-y-1 hover:bg-[#414da7]">
              Truy cập Postiva <ArrowRight size={16} />
            </Link>
          </div>
          <footer className="relative mt-5 overflow-hidden rounded-t-[36px] bg-[#5369b8] px-6 pb-8 pt-16 text-white sm:px-10 lg:px-16 lg:pt-24">
            <div className="pointer-events-none absolute -bottom-40 -left-24 h-[430px] w-[430px] rounded-[42%_58%_65%_35%] bg-[linear-gradient(145deg,rgba(132,210,244,.58),rgba(216,221,251,.2))] blur-sm" />
            <div className="relative mx-auto grid max-w-7xl gap-14 lg:grid-cols-[1.15fr_1fr_.75fr_.75fr]">
              <div>
                <p className="text-5xl font-semibold tracking-[-.065em] sm:text-7xl">postiva<sup className="ml-1 align-top text-sm">®</sup></p>
                <p className="mt-5 max-w-xs text-lg leading-8 text-white/78">Vận hành nội dung thông minh với dữ liệu, tự động hóa và AI.</p>
              </div>

              <div>
                <h3 className="text-3xl font-medium tracking-[-.04em]">Luôn nhận tin mới</h3>
                <p className="mt-1 text-white/70">Cập nhật tính năng và hướng dẫn vận hành.</p>
                <form onSubmit={(event) => event.preventDefault()} className="mt-8 flex max-w-sm items-center border-b border-white/70 pb-3">
                  <input type="email" aria-label="Email nhận bản tin" placeholder="Email của bạn" className="min-w-0 flex-1 bg-transparent text-lg text-white outline-none placeholder:text-white/65" />
                  <button type="submit" aria-label="Đăng ký nhận bản tin" className="flex h-9 w-9 items-center justify-center rounded-full hover:bg-white/12"><ArrowRight size={19} /></button>
                </form>
                <div className="mt-14 flex items-center gap-4">
                  <a href="#contact" aria-label="X" className="flex h-9 w-9 items-center justify-center rounded-full border border-white/25 hover:bg-white/12">X</a>
                  <a href="#contact" aria-label="LinkedIn" className="flex h-9 w-9 items-center justify-center rounded-full border border-white/25 text-sm font-bold hover:bg-white/12">in</a>
                  <a href="#contact" aria-label="Instagram" className="flex h-9 w-9 items-center justify-center rounded-full border border-white/25 text-lg hover:bg-white/12">◎</a>
                  <a href="#contact" aria-label="YouTube" className="flex h-9 w-9 items-center justify-center rounded-full border border-white/25 text-sm hover:bg-white/12">▶</a>
                </div>
              </div>

              <FooterLinks title="Sản phẩm" links={[["Trang chủ", "#"], ["Tính năng", "#platform"], ["Cách hoạt động", "#workflow"], ["Hiệu quả", "#insights"], ["FAQ", "#faq"]]} />
              <FooterLinks title="Công ty" links={[["Về Postiva", "#platform"], ["Khách hàng", "#insights"], ["Mở báo cáo", "/app/reports"], ["Liên hệ", "#contact"]]} />
            </div>
            <div className="relative mx-auto mt-16 flex max-w-7xl flex-col gap-3 border-t border-white/18 pt-6 text-sm text-white/68 sm:flex-row sm:items-center sm:justify-between">
              <span>Copyright © Postiva 2026</span>
              <span>Nội dung · Lịch đăng · Tương tác · Phân tích</span>
              <span>Made for social teams</span>
            </div>
          </footer>
        </section>
      </main>
    </div>
  )
}

function AnimatedStat({ value, suffix, label }: { value: number; suffix: string; label: string }) {
  const ref = useRef<HTMLDivElement>(null)
  const [displayValue, setDisplayValue] = useState(0)

  useEffect(() => {
    const element = ref.current
    if (!element) return
    let animationFrame = 0
    const observer = new IntersectionObserver(([entry]) => {
      if (!entry.isIntersecting) return
      const startedAt = performance.now()
      const animate = (now: number) => {
        const progress = Math.min((now - startedAt) / 1100, 1)
        const eased = 1 - Math.pow(1 - progress, 3)
        setDisplayValue(Math.round(value * eased))
        if (progress < 1) animationFrame = window.requestAnimationFrame(animate)
      }
      animationFrame = window.requestAnimationFrame(animate)
      observer.disconnect()
    }, { threshold: 0.45 })
    observer.observe(element)
    return () => {
      observer.disconnect()
      window.cancelAnimationFrame(animationFrame)
    }
  }, [value])

  return (
    <div ref={ref} data-reveal className="reveal-up">
      <p className="text-3xl font-medium tracking-[-.04em] text-[#475475] sm:text-4xl">{displayValue}{suffix}</p>
      <p className="mt-2 text-xs font-semibold uppercase tracking-[.12em] text-[#8a8fbb]">{label}</p>
    </div>
  )
}

function ReportsPreview() {
  return (
    <div className="rescale-float relative mx-auto mt-16 max-w-5xl rounded-[30px] border border-black/10 bg-white/75 p-3 text-left shadow-[0_35px_120px_rgba(25,30,20,.16)] backdrop-blur sm:p-5">
      <div className="grid overflow-hidden rounded-[22px] border border-[#d4d7fd] bg-[#f9fbfc] md:grid-cols-[190px_1fr]">
        <div className="hidden border-r border-white/20 bg-[#6b7dc9] p-5 text-white md:block">
          <p className="font-bold">Postiva</p>
          <div className="mt-10 space-y-2 text-xs font-semibold text-white/46">
            <PreviewNav icon={<MousePointer2 size={14} />} text="Tạo bài" />
            <PreviewNav icon={<CalendarClock size={14} />} text="Lịch đăng" />
            <PreviewNav icon={<MessageCircle size={14} />} text="Bình luận" />
            <PreviewNav active icon={<BarChart3 size={14} />} text="Báo cáo" />
          </div>
        </div>
        <div className="p-5 sm:p-7">
          <div className="flex items-center justify-between"><div><p className="text-xs font-bold text-[#8a8fbb]">BÁO CÁO</p><p className="mt-1 text-xl font-bold">Lịch sử bài đăng</p></div><span className="rounded-full bg-[#757bd4] px-4 py-2 text-xs font-bold text-white">+ Tạo bài</span></div>
          <div className="mt-6 grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Metric value="128" label="Bài đã đăng" />
            <Metric value="24.8K" label="Lượt xem" accent />
            <Metric value="1.2K" label="Tương tác" />
            <Metric value="96%" label="Thành công" />
            <Metric value="96%" label="Thành công" />
          </div>
          <div className="mt-3 grid gap-3 lg:grid-cols-[1.5fr_1fr]">
            <div className="rounded-2xl border border-[#e4e3fd] bg-white p-4"><p className="text-sm font-bold">Hiệu quả nội dung</p><div className="mt-6 flex h-28 items-end gap-2">{[38, 58, 46, 76, 64, 92, 72, 86].map((height, index) => <span key={index} className="rescale-bar flex-1 rounded-t-md bg-[#8594ea]" style={{ height: `${height}%`, animationDelay: `${index * 70}ms` }} />)}</div></div>
            <div className="rounded-2xl bg-[#6b7dc9] p-4 text-white"><p className="text-sm font-bold">Hoạt động gần đây</p><div className="mt-5 space-y-4"><Activity text="Bài mới đã xuất bản" /><Activity text="Có 8 bình luận mới" /><Activity text="Lịch tuần đã sẵn sàng" /></div></div>
          </div>
        </div>
      </div>
    </div>
  )
}

function PreviewNav({ icon, text, active = false }: { icon: ReactNode; text: string; active?: boolean }) {
  return <div className={`flex items-center gap-2 rounded-lg px-3 py-2 ${active ? 'bg-[#d4d7fd] text-[#475475]' : ''}`}>{icon}{text}</div>
}

function Metric({ value, label, accent = false }: { value: string; label: string; accent?: boolean }) {
  return <div className={`rounded-2xl p-4 ${accent ? 'bg-[#d4d7fd]' : 'border border-[#e4e3fd] bg-white'}`}><p className="text-xl font-bold">{value}</p><p className="mt-1 text-[11px] font-semibold text-[#8a8fbb]">{label}</p></div>
}

function Activity({ text }: { text: string }) {
  return <div className="flex items-center gap-2 text-xs text-white/70"><span className="h-2 w-2 rounded-full bg-[#84d2f4]" />{text}</div>
}

function CheckLine({ text }: { text: string }) {
  return <div className="flex items-center gap-3 font-semibold"><span className="flex h-7 w-7 items-center justify-center rounded-full bg-[#e4e3fd]"><Check size={14} /></span>{text}</div>
}

function FooterLinks({ title, links }: { title: string; links: Array<[string, string]> }) {
  return (
    <div>
      <p className="text-xs font-bold uppercase tracking-[.18em] text-white/48">{title}</p>
      <nav className="mt-6 flex flex-col gap-4">
        {links.map(([label, href]) => <a key={label} href={href} className="w-fit text-base text-white/82 hover:translate-x-1 hover:text-white">{label}</a>)}
      </nav>
    </div>
  )
}
