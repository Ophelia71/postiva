import { X } from 'lucide-react'
import { useEffect, useRef, useState, type FormEvent } from 'react'
import { readDraft, useSessionWork } from '../lib/idleSession'

type EditPostModalProps = {
  title: string
  message: string
  error: string
  saving: boolean
  saveLabel: string
  savingLabel: string
  /** Where unsaved edits are kept on this device; the parent clears it when the edit is saved or dropped. */
  draftKey?: string
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
  useSessionWork({ busy: saving, unsaved: message !== original, draftKey, draft: () => message })
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
        {error && <p className="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-600">{error}</p>}
        <button
          disabled={saving}
          className="mt-5 h-11 w-full rounded-xl bg-violet-700 text-sm font-bold text-white hover:bg-violet-800 disabled:bg-slate-300"
        >
          {saving ? savingLabel : saveLabel}
        </button>
      </form>
    </div>
  )
}
