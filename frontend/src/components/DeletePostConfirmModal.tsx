import { AlertTriangle, X } from 'lucide-react'
import type { AppPost } from '../lib/appDataApi'

type DeletePostConfirmModalProps = {
  post: AppPost
  deleting: boolean
  onCancel: () => void
  onConfirm: () => void
}

export function DeletePostConfirmModal({
  post,
  deleting,
  onCancel,
  onConfirm,
}: DeletePostConfirmModalProps) {
  const description = post.status === 'Published' && post.platform.toLowerCase() === 'facebook'
    ? 'Bài sẽ bị xóa khỏi Facebook và lịch sử Postiva.'
    : post.status === 'Scheduled'
      ? 'Bài và tất cả lần đăng liên quan sẽ bị xóa khỏi lịch đăng Postiva.'
      : 'Bài và tất cả lần đăng liên quan sẽ bị xóa khỏi lịch sử Postiva.'

  return (
    <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-950/40 px-4" role="dialog" aria-modal="true" aria-labelledby="delete-post-title">
      <div className="w-full max-w-md rounded-2xl bg-white p-5 shadow-2xl">
        <div className="flex items-start gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-rose-100 text-rose-600">
            <AlertTriangle size={20} />
          </span>
          <div className="min-w-0 flex-1">
            <div className="flex items-start justify-between gap-3">
              <h2 id="delete-post-title" className="text-lg font-bold text-slate-800">Xóa bài viết?</h2>
              <button
                type="button"
                onClick={onCancel}
                disabled={deleting}
                className="flex h-8 w-8 items-center justify-center rounded-lg text-slate-400 hover:bg-slate-100 disabled:cursor-not-allowed disabled:opacity-50"
                aria-label="Đóng"
              >
                <X size={17} />
              </button>
            </div>
            <p className="mt-2 text-sm leading-6 text-slate-600">
              {description}
            </p>
            <p className="mt-3 line-clamp-2 rounded-lg bg-slate-50 px-3 py-2 text-sm text-slate-500">
              {post.content || 'Không có nội dung chữ.'}
            </p>
          </div>
        </div>
        <div className="mt-5 flex justify-end gap-3">
          <button
            type="button"
            onClick={onCancel}
            disabled={deleting}
            className="h-10 rounded-lg border border-slate-200 px-4 text-sm font-semibold text-slate-600 hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50"
          >
            Hủy
          </button>
          <button
            type="button"
            onClick={onConfirm}
            disabled={deleting}
            className="h-10 rounded-lg bg-rose-600 px-4 text-sm font-bold text-white hover:bg-rose-700 disabled:cursor-not-allowed disabled:bg-slate-300"
          >
            {deleting ? 'Đang xóa...' : 'Xóa bài viết'}
          </button>
        </div>
      </div>
    </div>
  )
}
