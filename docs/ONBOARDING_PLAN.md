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

### G5. Đo lường và vòng lặp (chưa làm)

Dự án đã có analytics first-party (`/api/analytics/events`, `lib/webAnalytics.ts`). Đề xuất gửi các sự kiện sau, không cần đổi backend nếu endpoint chấp nhận tên sự kiện tự do (cần kiểm tra trước):

| Sự kiện | Khi nào |
|---|---|
| `onboarding_viewed` | Mở `/app/start` |
| `onboarding_step_completed` | Bước chuyển từ chưa xong sang xong (suy ra khi tải trang) |
| `onboarding_dismissed` | Bấm "Để sau" |
| `connect_failed` | Kết nối kênh lỗi, kèm loại lỗi đã được che token |

Chỉ số: tỉ lệ hoàn thành checklist, thời gian từ đăng ký tới bài đầu tiên, tỉ lệ rơi ở bước kết nối kênh, tỉ lệ bấm "Để sau".

## Rủi ro và quyết định còn mở

- **Kết nối bằng token thủ công vẫn là điểm nghẽn lớn nhất.** Hướng dẫn chữ giảm được ma sát nhưng không loại bỏ. Giải pháp dài hạn là OAuth, phụ thuộc Meta App Review; chưa nằm trong phạm vi này.
- **Facebook là nền tảng pilot chính**; Instagram và Threads đang thử nghiệm giới hạn (theo `docs/META_PAGE_TOKEN_PILOT.md`). Giao diện đã nói rõ điều này, và không nên đổi thông điệp khi chưa kiểm chứng.
- **Email OTP khi đăng ký phụ thuộc cấu hình mail.** Môi trường local tắt gửi mail, nên chưa kiểm thử được bước xác thực email bằng tay.
- **Cờ dismissed nằm ở trình duyệt**, không theo tài khoản. Đổi thiết bị có thể thấy lại checklist; chấp nhận được vì nó vốn suy ra từ dữ liệu thật và tự biến mất khi đã xong.
- Chưa có kiểm thử tự động cho `StartPage` và `AppIndexRedirect`; nên thêm khi frontend có khung test component.

## Kiểm tra đã chạy

- `tsc -b`, `oxlint`, `vite build` và `node tests/auth-session.test.mjs` đều qua.
- Kiểm tra bằng trình duyệt ở 1440px và 375px với tài khoản mới: `/app` chuyển sang `/app/start`, Reports trống hiện empty state, Connections và Tạo bài hiển thị đúng; không có lỗi JS trong console.
- Chưa kiểm tra bằng trình đọc màn hình thật; chưa chạy quét contrast tự động sau khi sửa.
