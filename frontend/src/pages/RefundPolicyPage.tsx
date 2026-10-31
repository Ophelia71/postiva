import { useEffect, type ReactNode } from 'react'
import { Link } from 'react-router-dom'

// Same contact as the privacy policy; replace here when a dedicated support address is available.
const SUPPORT_EMAIL = 'postiva84@gmail.com'
const contactLink = <a href={`mailto:${SUPPORT_EMAIL}`}>{SUPPORT_EMAIL}</a>

const sections: { id: string; title: string; content: ReactNode }[] = [
  {
    id: 'thanh-toan', title: 'Cách thanh toán gói Pro', content: <>
      <p>Gói Pro được trả một lần cho mỗi kỳ sử dụng. Postiva không tự động gia hạn và không lưu thông tin thẻ.</p>
      <p>Bạn thanh toán bằng chuyển khoản ngân hàng qua mã VietQR do payOS cung cấp. Gói Pro được bật ngay khi Postiva nhận được tiền. Nếu sau 30 phút gói Pro vẫn chưa được bật, hãy liên hệ {contactLink} kèm mã đơn.</p>
      <p>Gia hạn khi gói Pro còn hạn sẽ nối thêm một kỳ mới ngay sau kỳ hiện tại.</p>
    </>,
  },
  {
    id: 'duoc-hoan', title: 'Khi nào được hoàn tiền', content: <>
      <ul>
        <li>Bạn bị trừ tiền nhưng gói Pro không được bật.</li>
        <li>Bạn thanh toán hai lần cho cùng một kỳ.</li>
        <li>Postiva gặp lỗi khiến bạn không dùng được các tính năng của gói Pro trong thời gian dài.</li>
      </ul>
      <p>Postiva không hoàn tiền khi bạn đổi ý sau khi đã dùng các tính năng của gói Pro trong kỳ.</p>
    </>,
  },
  {
    id: 'cach-yeu-cau', title: 'Cách gửi yêu cầu', content: <>
      <p>Gửi yêu cầu trong vòng 7 ngày kể từ ngày thanh toán, theo một trong hai cách:</p>
      <ul>
        <li>Trong Postiva: vào Gói Pro, chọn đơn cần hoàn, ghi lý do rồi bấm Gửi yêu cầu.</li>
        <li>Gửi email tới {contactLink}, ghi rõ email tài khoản và mã đơn.</li>
      </ul>
    </>,
  },
  {
    id: 'xu-ly', title: 'Thời gian và cách hoàn', content: <>
      <p>Postiva trả lời yêu cầu trong vòng 3 ngày làm việc. Khi yêu cầu được chấp nhận, tiền được chuyển lại vào tài khoản ngân hàng đã thanh toán trong 5–10 ngày làm việc.</p>
      <p>Sau khi hoàn tiền, kỳ Pro của đơn đó dừng lại và tài khoản trở về gói Free.</p>
    </>,
  },
  {
    id: 'lien-he', title: 'Liên hệ', content: <>
      <address className="not-italic">Postiva<br />Email: {contactLink}<br />Website: <a href="https://postiva.store">https://postiva.store</a></address>
    </>,
  },
]

export function RefundPolicyPage() {
  useEffect(() => {
    const previousTitle = document.title
    document.title = 'Chính sách hoàn tiền | Postiva'
    return () => { document.title = previousTitle }
  }, [])

  return (
    <div className="min-h-svh bg-[var(--app-bg)] text-[var(--app-text)]">
      <header className="border-b border-[var(--app-border)] bg-[var(--panel-bg)]">
        <div className="mx-auto flex max-w-[960px] flex-wrap items-center justify-between gap-4 px-5 py-6 sm:px-8">
          <Link to="/" className="text-xl font-extrabold tracking-tight">Postiva</Link>
          <Link to="/app/billing" className="text-sm font-semibold text-[var(--accent-strong)] underline underline-offset-4">Về trang Gói Pro</Link>
        </div>
      </header>
      <main id="main-content" className="mx-auto max-w-[960px] px-5 py-10 sm:px-8 sm:py-16">
        <h1 className="text-3xl font-bold tracking-tight sm:text-4xl">Chính sách thanh toán và hoàn tiền</h1>
        <p className="mt-4 text-sm text-[var(--muted-text)]">Cập nhật: <time dateTime="2026-10-30">30/10/2026</time></p>
        <article className="mt-10 space-y-10 break-words rounded-2xl border border-[var(--app-border)] bg-[var(--panel-bg)] p-6 leading-relaxed sm:p-10 [&_a]:text-[var(--accent-strong)] [&_a]:underline [&_a]:underline-offset-4 [&_li]:pl-1 [&_ul]:list-disc [&_ul]:space-y-2 [&_ul]:pl-6">
          {sections.map((section, index) => (
            <section key={section.id} id={section.id} aria-labelledby={`${section.id}-heading`} className="scroll-mt-6">
              <h2 id={`${section.id}-heading`} className="mb-4 text-xl font-bold tracking-tight">{index + 1}. {section.title}</h2>
              <div className="space-y-4">{section.content}</div>
            </section>
          ))}
        </article>
      </main>
      <footer className="mx-auto flex max-w-[960px] flex-wrap justify-between gap-4 px-5 pb-8 text-sm text-[var(--muted-text)] sm:px-8">
        <span>© 2026 Postiva</span>
        <Link to="/" className="font-semibold underline underline-offset-4">Về trang chủ</Link>
      </footer>
    </div>
  )
}
