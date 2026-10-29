import { useEffect } from 'react'
import { Link } from 'react-router-dom'
import { useAppDataResource } from '../lib/appDataApi'
import { isShopProfileComplete, SHOP_PROFILE_SAVED_EVENT, type ShopProfile } from '../lib/shopProfile'

/** One-line nudge for shops without a complete profile (older accounts, or people who chose "Để sau"). */
export function ShopProfileReminder() {
  const { data, loading, error, reload } = useAppDataResource<ShopProfile | null>('/shop-profile')

  useEffect(() => {
    window.addEventListener(SHOP_PROFILE_SAVED_EVENT, reload)
    return () => window.removeEventListener(SHOP_PROFILE_SAVED_EVENT, reload)
  }, [reload])

  if (loading || error || isShopProfileComplete(data)) return null
  return (
    <div role="status" className="mb-6 flex flex-col gap-3 rounded-xl border border-[var(--app-border)] bg-[var(--panel-bg)] px-4 py-3 text-sm sm:flex-row sm:items-center sm:justify-between">
      <p className="text-[var(--muted-text)]"><span className="font-semibold text-[var(--app-text)]">Hồ sơ shop chưa đầy đủ.</span> Điền tên shop, ngành hàng và sản phẩm để bài viết đúng với shop của bạn.</p>
      <Link to="/app/shop-profile" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-full border border-[var(--app-border)] px-4 font-semibold hover:bg-[var(--soft-bg)]">
        Điền hồ sơ
      </Link>
    </div>
  )
}
