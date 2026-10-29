import { useEffect, useState } from 'react'
import { api } from '../lib/api'
import { getErrorMessage } from '../lib/errors'
import {
  type IndustryOption,
  OTHER_INDUSTRY,
  SHOP_PROFILE_SAVED_EVENT,
  SHOP_TONES,
  type ShopProfile,
  suggestSignature,
} from '../lib/shopProfile'

type Form = {
  businessName: string
  industryCode: string
  industryName: string
  mainProducts: string
  phone: string
  address: string
  targetCustomer: string
  defaultTone: string
  description: string
  highlights: string
  priceRange: string
  openingHours: string
  website: string
  policies: string
  signature: string
  brandHashtags: string
}

const fromProfile = (profile: ShopProfile | null): Form => ({
  businessName: profile?.businessName ?? '',
  industryCode: profile?.industryCode ?? '',
  industryName: profile?.industryName ?? '',
  mainProducts: profile?.mainProducts ?? '',
  phone: profile?.phone ?? '',
  address: profile?.address ?? '',
  targetCustomer: profile?.targetCustomer ?? '',
  defaultTone: profile?.defaultTone ?? SHOP_TONES[0],
  description: profile?.description ?? '',
  highlights: profile?.highlights ?? '',
  priceRange: profile?.priceRange ?? '',
  openingHours: profile?.openingHours ?? '',
  website: profile?.website ?? '',
  policies: profile?.policies ?? '',
  signature: profile?.signature ?? '',
  brandHashtags: profile?.brandHashtags ?? '',
})

const INPUT = 'mt-1 h-11 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 text-sm font-normal'
const TEXTAREA = 'mt-1 w-full rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] px-3 py-2 text-sm font-normal'

/**
 * The shop profile form. "onboarding" asks for the three required fields plus contact details and hides the rest
 * behind a toggle; "full" shows everything for later edits.
 */
export function ShopProfileForm({ initial, mode, onSaved, onSkip }: {
  initial: ShopProfile | null
  mode: 'onboarding' | 'full'
  onSaved: (profile: ShopProfile) => void
  onSkip?: () => void
}) {
  const [form, setForm] = useState<Form>(() => fromProfile(initial))
  const [industries, setIndustries] = useState<IndustryOption[]>([])
  const [showMore, setShowMore] = useState(mode === 'full')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const set = <K extends keyof Form>(key: K, value: Form[K]) => setForm((current) => ({ ...current, [key]: value }))

  useEffect(() => {
    let ignore = false
    api.get<IndustryOption[]>('/templates')
      .then((response) => { if (!ignore) setIndustries(response.data) })
      .catch(() => { if (!ignore) setIndustries([]) })
    return () => { ignore = true }
  }, [])

  function problem() {
    if (!form.businessName.trim()) return 'Nhập tên shop.'
    if (!form.industryCode) return 'Chọn ngành hàng.'
    if (form.industryCode === OTHER_INDUSTRY && !form.industryName.trim()) return 'Nhập tên ngành hàng của shop.'
    if (!form.mainProducts.trim()) return 'Nhập sản phẩm hoặc dịch vụ chính.'
    return null
  }

  async function save() {
    const blocked = problem()
    if (blocked) {
      setError(blocked)
      return
    }
    setSaving(true)
    setError('')
    try {
      const response = await api.put<ShopProfile>('/shop-profile', {
        ...form,
        industryName: form.industryCode === OTHER_INDUSTRY ? form.industryName : '',
      })
      window.dispatchEvent(new Event(SHOP_PROFILE_SAVED_EVENT))
      onSaved(response.data)
    } catch (saveError) {
      setError(getErrorMessage(saveError, 'Không lưu được hồ sơ shop. Vui lòng thử lại.'))
    } finally {
      setSaving(false)
    }
  }

  const suggestion = suggestSignature(form)

  return (
    <form onSubmit={(event) => { event.preventDefault(); void save() }} className="space-y-5" noValidate>
      <fieldset className="space-y-4">
        <legend className="text-base font-bold">Thông tin bắt buộc</legend>
        <label className="block text-sm font-bold">Tên shop *
          <input value={form.businessName} maxLength={255} onChange={(event) => set('businessName', event.target.value)} className={INPUT} placeholder="Ví dụ: Tiệm Cà Phê Muối" />
        </label>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-bold">Ngành hàng *
            <select value={form.industryCode} onChange={(event) => set('industryCode', event.target.value)} className={INPUT}>
              <option value="">Chọn ngành hàng</option>
              {industries.map((industry) => <option key={industry.industryCode} value={industry.industryCode}>{industry.industryName}</option>)}
              <option value={OTHER_INDUSTRY}>Ngành khác</option>
            </select>
          </label>
          {form.industryCode === OTHER_INDUSTRY && (
            <label className="block text-sm font-bold">Tên ngành *
              <input value={form.industryName} maxLength={100} onChange={(event) => set('industryName', event.target.value)} className={INPUT} placeholder="Ví dụ: Tiệm bánh, Đồ handmade" />
            </label>
          )}
        </div>
        <label className="block text-sm font-bold">Sản phẩm / dịch vụ chính *
          <textarea value={form.mainProducts} maxLength={1000} rows={2} onChange={(event) => set('mainProducts', event.target.value)} className={TEXTAREA} placeholder="Ví dụ: Cà phê muối pha phin, bánh quy bơ làm tại tiệm" />
        </label>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="text-base font-bold">Nên điền <span className="text-sm font-normal text-[var(--muted-text)]">(có thể để sau)</span></legend>
        <p className="text-sm text-[var(--muted-text)]">Số điện thoại và địa chỉ sẽ xuất hiện công khai trên bài đăng khi bạn dùng chữ ký cuối bài.</p>
        <div className="grid gap-4 sm:grid-cols-2">
          <label className="block text-sm font-bold">Số điện thoại / Zalo
            <input value={form.phone} maxLength={50} inputMode="tel" onChange={(event) => set('phone', event.target.value)} className={INPUT} placeholder="0901 234 567" />
          </label>
          <label className="block text-sm font-bold">Địa chỉ
            <input value={form.address} maxLength={500} onChange={(event) => set('address', event.target.value)} className={INPUT} placeholder="Bỏ trống nếu chỉ bán online" />
          </label>
          <label className="block text-sm font-bold">Khách hàng mục tiêu
            <input value={form.targetCustomer} maxLength={1000} onChange={(event) => set('targetCustomer', event.target.value)} className={INPUT} placeholder="Ví dụ: Dân văn phòng 22–35 tuổi" />
          </label>
          <label className="block text-sm font-bold">Giọng văn
            <select value={form.defaultTone} onChange={(event) => set('defaultTone', event.target.value)} className={INPUT}>
              {SHOP_TONES.map((tone) => <option key={tone}>{tone}</option>)}
            </select>
          </label>
        </div>
      </fieldset>

      {mode === 'onboarding' && (
        <button type="button" aria-expanded={showMore} onClick={() => setShowMore((value) => !value)} className="min-h-11 text-sm font-semibold underline underline-offset-4">
          {showMore ? 'Ẩn thông tin thêm' : 'Thêm mô tả, chữ ký cuối bài, hashtag... (tùy chọn)'}
        </button>
      )}

      {showMore && (
        <fieldset className="space-y-4">
          <legend className="text-base font-bold">Thông tin thêm <span className="text-sm font-normal text-[var(--muted-text)]">(tùy chọn)</span></legend>
          <label className="block text-sm font-bold">Giới thiệu shop
            <textarea value={form.description} maxLength={4000} rows={3} onChange={(event) => set('description', event.target.value)} className={TEXTAREA} />
          </label>
          <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-bold">Điểm nổi bật
              <input value={form.highlights} maxLength={2000} onChange={(event) => set('highlights', event.target.value)} className={INPUT} placeholder="Ví dụ: Giao trong 30 phút" />
            </label>
            <label className="block text-sm font-bold">Khoảng giá
              <input value={form.priceRange} maxLength={200} onChange={(event) => set('priceRange', event.target.value)} className={INPUT} placeholder="Ví dụ: 25.000đ – 45.000đ" />
            </label>
            <label className="block text-sm font-bold">Giờ mở cửa
              <input value={form.openingHours} maxLength={200} onChange={(event) => set('openingHours', event.target.value)} className={INPUT} placeholder="7h – 22h hằng ngày" />
            </label>
            <label className="block text-sm font-bold">Website / Fanpage
              <input value={form.website} maxLength={500} inputMode="url" onChange={(event) => set('website', event.target.value)} className={INPUT} />
            </label>
          </div>
          <label className="block text-sm font-bold">Chính sách giao hàng, đổi trả
            <textarea value={form.policies} maxLength={2000} rows={2} onChange={(event) => set('policies', event.target.value)} className={TEXTAREA} />
          </label>
          <label className="block text-sm font-bold">Chữ ký cuối bài
            <textarea value={form.signature} maxLength={500} rows={2} onChange={(event) => set('signature', event.target.value)} className={TEXTAREA} placeholder="📍 Địa chỉ · ☎ Số điện thoại" />
            <span className="mt-1 block text-xs font-normal text-[var(--muted-text)]">Tự thêm vào cuối mỗi bài AI soạn; bạn vẫn sửa hoặc xóa được trước khi đăng. Threads chỉ thêm khi bài còn dưới 500 ký tự.</span>
          </label>
          {!form.signature.trim() && suggestion && (
            <button type="button" onClick={() => set('signature', suggestion)} className="min-h-11 rounded-full border border-[var(--app-border)] px-4 text-sm font-bold hover:bg-[var(--soft-bg)]">
              Dùng gợi ý: {suggestion}
            </button>
          )}
          <label className="block text-sm font-bold">Hashtag thương hiệu
            <input value={form.brandHashtags} maxLength={300} onChange={(event) => set('brandHashtags', event.target.value)} className={INPUT} placeholder="#TiemCaPheMuoi #CaPheMuoi" />
          </label>
        </fieldset>
      )}

      {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm font-semibold text-rose-800">{error}</p>}

      <div className="flex flex-wrap items-center justify-end gap-3">
        {onSkip && <button type="button" onClick={onSkip} className="mr-auto min-h-11 text-sm font-semibold underline underline-offset-4">Để sau</button>}
        <button type="submit" disabled={saving} className="min-h-11 rounded-full bg-[#505db8] px-5 text-sm font-bold text-white hover:bg-[#434fa0] disabled:bg-slate-400">
          {saving ? 'Đang lưu...' : mode === 'onboarding' ? 'Lưu hồ sơ shop' : 'Lưu thay đổi'}
        </button>
      </div>
    </form>
  )
}
