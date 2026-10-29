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
    <div role="status" className="mb-6 flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-sm text-amber-900 sm:flex-row sm:items-center sm:justify-between">
      <p><span className="font-bold">Hoàn thiện hồ sơ shop</span> để AI viết đúng ngành hàng, sản phẩm và thông tin liên hệ của bạn.</p>
      <Link to="/app/shop-profile" className="inline-flex min-h-11 shrink-0 items-center justify-center rounded-full bg-amber-700 px-4 font-bold text-white hover:bg-amber-800">
        Điền hồ sơ shop
      </Link>
    </div>
  )
}
