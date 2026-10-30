import { useState } from 'react'
import { PageHeader, PageState } from '../components/PageUi'
import { ShopProfileForm } from '../components/ShopProfileForm'
import { useAppDataResource } from '../lib/appDataApi'
import type { ShopProfile } from '../lib/shopProfile'

/** Edit the shop profile any time after onboarding. */
export function ShopProfilePage() {
  const { data, loading, error, reload } = useAppDataResource<ShopProfile | null>('/shop-profile')
  const [saved, setSaved] = useState(false)

  if (loading) return <PageState title="Đang tải hồ sơ shop..." />
  if (error) return <PageState title="Không tải được hồ sơ shop" description={error} onRetry={reload} />

  return (
    <section>
      <PageHeader title="Hồ sơ shop" subtitle="AI dùng thông tin này cho mọi bài: ngành hàng, sản phẩm, giọng văn, chữ ký và hashtag." />
      {saved && <p role="status" className="mb-4 rounded-lg border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm font-semibold text-emerald-800">Đã lưu hồ sơ shop. Bài AI soạn từ giờ sẽ dùng thông tin mới.</p>}
      <div className="rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-5 shadow-sm">
        <ShopProfileForm key={data ? 'loaded' : 'empty'} initial={data ?? null} mode="full" onSaved={() => { setSaved(true); reload() }} />
      </div>
    </section>
  )
}
