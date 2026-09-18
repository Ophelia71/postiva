import { X } from 'lucide-react'
import type { FormEvent } from 'react'

type EditPostModalProps = {
  title: string
  message: string
  error: string
  saving: boolean
  saveLabel: string
  savingLabel: string
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
  onChange,
  onClose,
  onSubmit,
}: EditPostModalProps) {
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 px-4">
      <form onSubmit={onSubmit} className="w-full max-w-2xl rounded-2xl bg-white p-5 shadow-xl">
        <div className="mb-5 flex items-center justify-between">
          <h2 className="text-lg font-bold">{title}</h2>
          <button type="button" onClick={onClose} className="flex h-8 w-8 items-center justify-center rounded-lg hover:bg-slate-100">
            <X size={17} />
          </button>
        </div>
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
