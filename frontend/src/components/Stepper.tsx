import { Check } from 'lucide-react'

export type StepDef = { label: string; optional?: boolean }

/** Horizontal step bar. Steps are marked required or optional and show a tick once complete. */
export function Stepper({
  label,
  steps,
  current,
  isDone,
  onGo,
}: {
  label: string
  steps: readonly StepDef[]
  current: number
  isDone: (index: number) => boolean
  onGo: (index: number) => void
}) {
  return (
    <nav aria-label={label}>
      <ol
        className="grid gap-1 rounded-[8px] border border-slate-200 bg-white p-1.5 shadow-sm"
        style={{ gridTemplateColumns: `repeat(${steps.length}, minmax(0, 1fr))` }}
      >
        {steps.map((step, index) => {
          const active = index === current
          const done = !active && isDone(index)
          return (
            <li key={step.label}>
              <button
                type="button"
                onClick={() => onGo(index)}
                aria-current={active ? 'step' : undefined}
                className={`flex min-h-14 w-full flex-col items-center justify-center gap-0.5 rounded-[6px] px-1 py-1.5 text-center transition focus-visible:outline-2 focus-visible:outline-violet-500 ${active ? 'bg-violet-50 text-violet-800' : 'text-slate-700 hover:bg-slate-50'}`}
              >
                <span
                  className={`flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold ${active ? 'bg-violet-600 text-white' : done ? 'bg-emerald-100 text-emerald-800' : 'bg-slate-100 text-slate-700'}`}
                >
                  {done ? <Check size={14} aria-label="Đã xong" /> : index + 1}
                </span>
                <span className="text-xs font-bold leading-tight">{step.label}</span>
                <span className="hidden text-[11px] font-medium text-slate-600 sm:block">{step.optional ? 'Tùy chọn' : 'Bắt buộc'}</span>
              </button>
            </li>
          )
        })}
      </ol>
    </nav>
  )
}
