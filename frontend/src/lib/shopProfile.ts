/** Mirrors the backend ShopProfileResponse; optional fields are absent when empty. */
export type ShopProfile = {
  businessName: string
  industryCode: string | null
  industryName?: string | null
  mainProducts?: string | null
  targetCustomer?: string | null
  defaultTone?: string | null
  description?: string | null
  highlights?: string | null
  priceRange?: string | null
  phone?: string | null
  address?: string | null
  openingHours?: string | null
  website?: string | null
  policies?: string | null
  signature?: string | null
  brandHashtags?: string | null
  complete: boolean
}

export type IndustryOption = { industryCode: string; industryName: string }

/** Industry code for trades that are not one of the seeded templates; keep in sync with ShopProfileService. */
export const OTHER_INDUSTRY = 'other'

export const SHOP_TONES = ['Thân thiện, gần gũi', 'Chuyên nghiệp, tin cậy', 'Trẻ trung, hài hước', 'Sang trọng, tinh tế']

/** Fired after a save so reminders and pages that read the profile refresh without a reload. */
export const SHOP_PROFILE_SAVED_EVENT = 'postiva:shop-profile-saved'

export function isShopProfileComplete(profile: ShopProfile | null | undefined) {
  return Boolean(profile?.complete)
}

/** A ready-made signature from the contact fields, offered when the shop has not written one. */
export function suggestSignature(profile: Pick<ShopProfile, 'phone' | 'address' | 'openingHours' | 'website'>) {
  return [
    profile.address ? `📍 ${profile.address}` : '',
    profile.phone ? `☎ ${profile.phone}` : '',
    profile.openingHours ? `🕒 ${profile.openingHours}` : '',
    profile.website ? `🌐 ${profile.website}` : '',
  ].filter(Boolean).join(' · ')
}
