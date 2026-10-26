# Plan onboarding lần đầu đăng nhập

> Trạng thái: giai đoạn G1–G4 đã có bản triển khai frontend trong nhánh `worktree-ux-onboarding-fixes`; G5 (đo lường) chưa làm. Backend không đổi.

## Mục tiêu

Đưa người dùng mới từ "vừa đăng nhập, chưa có gì" đến **bài đầu tiên đã đăng hoặc đã lên lịch** trong khoảng 5 phút, mà không buộc họ đọc tài liệu.

Người dùng mục tiêu: chủ shop nhỏ và người làm nội dung mới. Họ quen điện thoại hơn công cụ kỹ thuật, nên cụm "Page Access Token" là điểm dễ bỏ cuộc nhất.

## Hiện trạng trước khi sửa

| Điểm | Vấn đề |
|---|---|
| Đích sau đăng nhập | `/app` chuyển về `/app/reports`, nơi mọi thứ đều trống. |
| Empty state | Chỉ là một câu chữ, không có hành động. |
| Kết nối kênh | 3 form token luôn mở (9 ô), không có hướng dẫn, dấu tick xanh hiện cả khi chưa kết nối. |
| Tạo bài khi chưa có kênh | Báo lỗi "Hãy kết nối X trước" không kèm đường dẫn. |
| Mobile | Mất đăng xuất và Bảo mật tài khoản dưới 640px. |

## Luồng đích

```
Landing (CTA "Tạo tài khoản")
  → /app?mode=register (Đăng ký là chế độ mặc định)
  → xác thực email bằng mã 6 số
  → /app  ──(tài khoản mới)──▶  /app/start  (checklist 3 bước)
        └─(đã có kênh + bài)──▶  /app/reports
```

Checklist ở `/app/start`:

1. **Kết nối kênh đầu tiên** → `/app/connections`
2. **Tạo bài đầu tiên** → `/app/create` (khóa mềm: nếu chưa có kênh thì dẫn sang bước 1)
3. **Đăng hoặc lên lịch** → `/app/schedule`

Nguyên tắc:

- **Trạng thái suy ra từ dữ liệu thật** (`/app-data/overview`: có kênh `active`, `totalPosts`, `publishedPosts + scheduledPosts`). Không lưu cờ "đã xong từng bước" riêng, nên không thể lệch với thực tế.
- **Luôn bỏ qua được**: nút "Để sau, tôi tự khám phá" ghi cờ `postiva-onboarding-dismissed` trong `localStorage` (bọc try/catch). Cờ chỉ ảnh hưởng chuyển hướng ở `/app`, không chặn truy cập trang nào.
- **Xem lại được**: mục "Hướng dẫn bắt đầu" trong menu tài khoản trỏ tới `/app/start`.
- Người dùng cũ (đã có kênh và bài) không bao giờ bị chuyển vào checklist.

## Các giai đoạn

### G1. Sửa nền tảng (xong)

- Tách class dính chữ `rescale-glowflex` / `rescale-glowrelative` (card đăng nhập lệch góc).
- Menu tài khoản dùng chung cho desktop và mobile (Hướng dẫn bắt đầu, Gói & thanh toán, Bảo mật, Đăng xuất); đóng bằng Esc và bấm ra ngoài.
- Mobile: thanh trên có logo và tài khoản; thanh dưới có 4 mục (Lịch đăng, Tạo bài, Báo cáo, Kết nối) với nhãn 12px.
- Sidebar chỉ hiện từ 1024px; bỏ các rule CSS ẩn nhãn bằng `font-size: 0`.
- Mỗi trang chỉ có một `<h1>` (logo sidebar không còn là `h1`).

### G2. Checklist và chuyển hướng (xong)

- `AppIndexRedirect` quyết định `/app` đi đâu; `StartPage` hiển thị 3 bước, bước kế tiếp được nhấn mạnh.
- Auth: Đăng ký là mặc định khi vào bằng `?mode=register`; có logo, nút quay lại, ghi chú sẽ có 3 bước sau khi xác thực.

### G3. Kết nối kênh dễ hơn (xong)

- Giữ bố cục 3 thẻ (Facebook, Instagram, Threads) hiện cùng lúc, mỗi thẻ một form 3 ô. Hướng dẫn 3 bước và ghi chú giới hạn nằm trong tooltip `HelpTip` (nút `?` của từng thẻ): mở bằng hover, focus bàn phím hoặc bấm, đóng bằng Esc hoặc bấm ra ngoài.
- Dấu tick chỉ hiện khi đã có kết nối.
- Xác nhận ngay trong dòng, thay cho `window.confirm`: xóa kết nối, và **nút Kiểm tra Facebook** (tạo bài thử không công khai, `published=false`).
- Kết nối thành công → nút "Tạo bài đầu tiên".
- Trang tạo bài: banner dẫn sang Kết nối khi chưa có kênh; nút Quay lại dùng lịch sử trình duyệt.

### G4. Empty state và polish (xong)

- Reports khi chưa có bài: một empty state duy nhất với hành động; không còn biểu đồ rỗng.
- Schedule: empty state có nút "Tạo bài và lên lịch"; nút biểu tượng có `aria-label`, vùng chạm 44px trên mobile; ô ngày có tên đầy đủ.
- Billing: trạng thái đơn bằng tiếng Việt (thay mã enum), ngày theo `vi-VN`, giải thích Free trước khi mời mua Pro.
- Landing: bỏ kicker và chip tiếng Anh, CTA dẫn tới đăng ký, sửa contrast, bỏ chữ gradient, bỏ số thứ tự section.

### G4b. Sửa lỗi sau rà soát (xong)

Một subagent Plan đọc lại phần đã làm và tìm ra các lỗi nhỏ ở frontend; đã sửa:

- `AppIndexRedirect` trước đây chuyển người dùng mới sang Báo cáo khi tải dữ liệu lỗi; nay hiện nút Thử lại.
- Định nghĩa "đã thiết lập xong" của chuyển hướng (`totalPosts > 0`) và của bước 3 (`published + scheduled > 0`) khác nhau; nay dùng chung `getOnboardingState()` trong `lib/onboarding.ts` (hàm thuần, dễ test): có kênh hoạt động **và** có bài đã đăng hoặc đã lên lịch.
- Bỏ `aria-disabled` giả trên liên kết; nhãn "Kết nối kênh trước" là một hành động thật.
- Câu chữ: bước 1 nói đúng thực tế "Bắt đầu với Facebook Page"; bỏ lời hứa "mỗi bước chỉ vài phút"; nút "Để sau" nói rõ mở lại ở menu tài khoản.

### G5. Giai đoạn kế tiếp (chưa làm)

Thứ tự đề xuất, kèm tiêu chí hoàn thành:

| # | Việc | File | Mức | Hoàn thành khi |
|---|---|---|---|---|
| 1 | **Xác minh** bài thử Facebook (không công khai) có được tính vào `totalPosts` của `/app-data/overview` không (`AppDataController.java:80-88`). Chưa truy vết; nếu có thì bước 2 bị đánh dấu xong khi chưa có bài thật. | backend, chỉ đọc | S | Có câu trả lời bằng văn bản; sửa định nghĩa bước nếu cần |
| 2 | Khung test frontend: Vitest + Testing Library + jsdom (chưa kiểm tra tương thích React 19). Test đầu tiên: `getOnboardingState`, `AppIndexRedirect`, `StartPage`, `onboarding.ts` khi `localStorage` ném lỗi. | `package.json`, `tests/` | M | `npm test` chạy cả test cũ lẫn mới |
| 3 | **Khoảnh khắc thành công đầu tiên.** Khi bài đầu được tạo thành công (`CreatePostPage`, nhánh không có target lỗi) hiện khung "Bài đầu tiên đã sẵn sàng" với "Xem lịch đăng" và "Tạo bài tiếp"; gọi `dismissOnboarding()`; không tự chuyển trang. Bài lên lịch nói rõ chưa đăng và giờ đăng. Bài đầu thất bại hiện "Thử lại" và liên kết tới Kết nối (token hết hạn là nguyên nhân hay gặp). | `CreatePostPage.tsx` | M | Khung chỉ hiện một lần; lỗi dẫn được tới Kết nối |
| 4 | Biểu ngữ nhắc cho người đã bấm "Để sau", có kênh nhưng chưa có bài, trên Báo cáo và Lịch đăng. | `ReportsPage.tsx`, `SchedulePage.tsx` | S | Chỉ hiện khi có kênh và chưa có bài |
| 5 | Đo lường (xem bên dưới). | `webAnalytics.ts`, backend | M | Thấy được phễu theo đường dẫn |
| 6 | Chuyển chuỗi của `StartPage` sang `t()`; "Tạo bài đầu tiên" ở Kết nối đổi thành "Tạo bài" khi đã có bài. | `StartPage.tsx`, `ConnectionsPage.tsx` | S | Người bản ngữ duyệt |
| 7 | Thêm lối vào "Hướng dẫn bắt đầu" ở thanh dưới mobile (hiện chỉ có ở menu tài khoản). | `App.tsx` | S | Kiểm tra bàn phím và trình đọc màn hình |

**Đo lường: không thể gửi tên sự kiện tùy ý.** Backend chỉ chấp nhận enum cố định `PAGE_VIEW, SCROLL, ENGAGED, HEARTBEAT, IDENTIFY, EXCLUDE` (`WebAnalyticsService.java:117`), sự kiện chỉ có `type, visitorId, sessionId, viewId, path, source, scrollDepth`; `PAGE_VIEW` đòi đường dẫn hợp lệ (bắt đầu bằng `/`, không có `?`, `#`, `\`, tối đa 200 ký tự); mỗi phiên tối đa 100 lượt xem trang; sự kiện của ADMIN bị bỏ. Hai lựa chọn:

- **A. Không đổi backend:** gửi `PAGE_VIEW` ảo với đường dẫn `/virtual/onboarding/...` (đã bỏ phần "gửi tên sự kiện" trong bản plan cũ). Đổi lại, số lượt xem và tỉ lệ thoát trên bảng admin bị thổi phồng, nên vẫn cần một bộ lọc nhỏ ở `AdminWebAnalyticsService` (đây vẫn là thay đổi backend). Cần cờ chống gửi lặp theo từng bước.
- **B. Đổi backend:** thêm loại `CUSTOM` với tên theo danh sách cho phép, cùng bảng hoặc bộ đếm riêng. Sạch hơn, mức M; chỉ làm nếu phễu còn quan trọng sau pha này.

Đã đọc code nhưng chưa chạy thử; việc Jackson từ chối giá trị enum lạ là suy ra từ cấu hình Spring mặc định, chưa kiểm chứng.

## Rủi ro và quyết định còn mở

- **Kết nối bằng token thủ công vẫn là điểm nghẽn lớn nhất.** Hướng dẫn chữ giảm được ma sát nhưng không loại bỏ. Giải pháp dài hạn là OAuth, phụ thuộc Meta App Review; ngoài phạm vi.
- **Facebook là nền tảng pilot chính**; Instagram và Threads đang thử nghiệm giới hạn (`docs/META_PAGE_TOKEN_PILOT.md`). Giao diện đã nói rõ, không đổi thông điệp khi chưa kiểm chứng.
- **OTP email khi đăng ký phụ thuộc cấu hình mail.** Local tắt gửi mail nên chưa thử bằng tay được bước xác thực email.
- **Cờ dismissed nằm ở trình duyệt**, không theo tài khoản; checklist vốn suy ra từ dữ liệu thật nên tự biến mất khi xong. Người bấm "Để sau" rồi kết nối kênh sẽ không được nhắc tạo bài, trừ khi làm việc số 4 ở trên.
- **Mất bản nháp khi phiên hết hạn:** biểu mẫu tạo bài và form token không được lưu; chưa kiểm chứng có lưu nháp ở đâu không.

## Kiểm tra đã chạy

- `tsc -b`, `oxlint`, `vite build` và `node tests/auth-session.test.mjs` đều qua.
- Trình duyệt ở 1440px và 375px với tài khoản mới: `/app` chuyển sang `/app/start`, Reports trống hiện empty state, Kết nối và Tạo bài hiển thị đúng; không có lỗi JS trong console.
- Chưa kiểm tra bằng trình đọc màn hình thật; chưa có test tự động cho `StartPage` và `AppIndexRedirect`.
