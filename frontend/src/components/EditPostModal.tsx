import { Image as ImageIcon, X } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { readDraft, useSessionWork } from '../lib/idleSession'
import { ProtectedMediaImage } from './ProtectedMedia'

export type EditMediaItem = { id: string; url: string }

/** Optional images and videos of the post; without it the modal edits the text only. */
export type EditPostMedia = {
  items: EditMediaItem[]
  uploading: boolean
  changed: boolean
  max: number
  onAdd: (files: File[]) => void
  onRemove: (id: string) => void
}

type EditPostModalProps = {
  title: string
  message: string
  error: string
  saving: boolean
  saveLabel: string
  savingLabel: string
  /** Where unsaved edits are kept on this device; the parent clears it when the edit is saved or dropped. */
  draftKey?: string
  media?: EditPostMedia
  onChange: (message: string) => void
  onClose: () => void
  onSubmit: (event: FormEvent<HTMLFormElement>) => void
}

export function EditPostModal({
  title,
  message,
  error,
  saving,
  saveLabel,
  savingLabel,
  draftKey,
  media,
  onChange,
  onClose,
  onSubmit,
}: EditPostModalProps) {
  const [original] = useState(message)
  const [restored] = useState(() => (draftKey ? readDraft<string>(draftKey) : null))
  const [showRestored, setShowRestored] = useState(false)
  // Edits left unsaved last time (signed out, tab closed) come back once, when the editor opens.
  const appliedRestore = useRef(false)
  useEffect(() => {
    if (appliedRestore.current || restored === null || restored === original) return
    appliedRestore.current = true
    onChange(restored)
    setShowRestored(true)
  }, [restored, original, onChange])
  useSessionWork({ busy: saving || !!media?.uploading, unsaved: message !== original || !!media?.changed, draftKey, draft: () => message })
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 px-4">
      <form onSubmit={onSubmit} className="w-full max-w-2xl rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-lg font-bold">{title}</h2>
          <button type="button" onClick={onClose} aria-label="Đóng" className="flex h-11 w-11 items-center justify-center rounded-lg hover:bg-slate-100">
            <X size={17} />
          </button>
        </div>
        {showRestored && (
          <div role="status" className="mb-3 flex flex-wrap items-center justify-between gap-2 rounded-lg border border-slate-200 bg-slate-50 px-3 py-2 text-sm text-slate-700">
            <span>Đã mở lại nội dung bạn sửa dở.</span>
            <button type="button" onClick={() => { onChange(original); setShowRestored(false) }} className="min-h-11 text-sm font-semibold underline underline-offset-4">Dùng nội dung cũ</button>
          </div>
        )}
        <textarea
          value={message}
          onChange={(event) => onChange(event.target.value)}
          className="h-64 w-full resize-none rounded-xl border border-slate-200 bg-slate-50 px-3 py-2 text-sm leading-6 outline-none focus:border-violet-500"
        />
        {media && (
          <div className="mt-4">
            <div className="flex flex-wrap items-center gap-3">
              <label className={`inline-flex h-10 items-center gap-2 rounded-lg border border-slate-200 bg-white px-4 text-sm font-semibold ${media.uploading || media.items.length >= media.max ? 'cursor-not-allowed opacity-60' : 'cursor-pointer hover:bg-slate-50'}`}>
                <ImageIcon size={16} aria-hidden="true" />
                {media.uploading ? 'Đang tải...' : 'Thêm ảnh/video'}
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp,video/mp4,video/quicktime,video/webm"
                  multiple
                  disabled={media.uploading || media.items.length >= media.max}
                  onChange={(event) => {
                    const files = Array.from(event.target.files ?? [])
                    event.target.value = ''
                    if (files.length > 0) media.onAdd(files)
                  }}
                  className="hidden"
                />
              </label>
              <span className="text-xs text-slate-500">{media.items.length}/{media.max} ảnh hoặc video</span>
            </div>
            {media.items.length > 0 && (
              <ul className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-5">
                {media.items.map((item) => (
                  <li key={item.id} className="relative aspect-square overflow-hidden rounded-lg border border-slate-200 bg-slate-100">
                    <ProtectedMediaImage src={item.url} alt="" className="h-full w-full object-cover" />
                    <button
                      type="button"
                      onClick={() => media.onRemove(item.id)}
                      aria-label="Bỏ ảnh này"
                      className="absolute right-1 top-1 flex h-7 w-7 items-center justify-center rounded-full bg-white/90 text-slate-700 shadow hover:bg-white"
                    >
                      <X size={14} />
                    </button>
                  </li>
                ))}
              </ul>
            )}
          </div>
        )}
        {error && <p className="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>}
        <button
          disabled={saving || !!media?.uploading}
          className="mt-5 h-11 w-full rounded-xl bg-violet-700 text-sm font-bold text-white hover:bg-violet-800 disabled:bg-slate-300"
        >
          {saving ? savingLabel : saveLabel}
        </button>
      </form>
    </div>
  )
}
