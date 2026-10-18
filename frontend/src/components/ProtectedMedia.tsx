import { useEffect, useState, type ImgHTMLAttributes, type VideoHTMLAttributes } from 'react'
import { api } from '../lib/api'
import { resolveMediaUrl } from '../lib/media'

export function ProtectedMediaImage({
  src,
  alt,
  className,
  referrerPolicy,
}: Pick<ImgHTMLAttributes<HTMLImageElement>, 'alt' | 'className' | 'referrerPolicy'> & { src: string }) {
  const mediaSrc = useProtectedMediaUrl(src)
  if (!mediaSrc) {
    return <div className={className} aria-label="Đang tải media" />
  }
  return <img src={mediaSrc} alt={alt} className={className} referrerPolicy={referrerPolicy} />
}

export function ProtectedMediaVideo({
  src,
  poster,
  className,
  controls = true,
  preload,
}: Pick<VideoHTMLAttributes<HTMLVideoElement>, 'poster' | 'className' | 'controls' | 'preload'> & { src: string }) {
  const mediaSrc = useProtectedMediaUrl(src)
  const posterSrc = useProtectedMediaUrl(poster)
  if (!mediaSrc) {
    return <div className={className} aria-label="Đang tải video" />
  }
  return <video src={mediaSrc} poster={posterSrc || undefined} controls={controls} preload={preload} className={className} />
}

function useProtectedMediaUrl(value?: string | null) {
  const [source, setSource] = useState('')

  useEffect(() => {
    const resolved = resolveMediaUrl(value)
    if (!resolved) {
      setSource('')
      return
    }

    const fileId = uploadedFileId(resolved)
    if (!fileId) {
      setSource(resolved)
      return
    }

    const controller = new AbortController()
    let objectUrl = ''
    setSource('')
    void api.get<Blob>(`/files/${fileId}`, {
      responseType: 'blob',
      signal: controller.signal,
    }).then((response) => {
      objectUrl = URL.createObjectURL(response.data)
      setSource(objectUrl)
    }).catch(() => {
      setSource('')
    })

    return () => {
      controller.abort()
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [value])

  return source
}

function uploadedFileId(value: string) {
  try {
    const path = new URL(value, window.location.origin).pathname
    const match = path.match(/\/api\/files\/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\/?$/i)
    return match?.[1] ?? ''
  } catch {
    return ''
  }
}
