export function resolveMediaUrl(value?: string | null) {
  const mediaUrl = (value ?? '').trim()
  if (!mediaUrl) {
    return ''
  }
  if (/^https?:\/\//i.test(mediaUrl) || mediaUrl.startsWith('blob:') || mediaUrl.startsWith('data:')) {
    return mediaUrl
  }

  const apiBaseUrl = import.meta.env.VITE_API_BASE_URL ?? ''
  if (apiBaseUrl && /^https?:\/\//i.test(apiBaseUrl)) {
    return `${apiBaseUrl.replace(/\/api\/?$/, '')}${mediaUrl.startsWith('/') ? mediaUrl : `/${mediaUrl}`}`
  }

  return mediaUrl
}
