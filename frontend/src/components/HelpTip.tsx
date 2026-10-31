import { CircleHelp } from 'lucide-react'
import { useEffect, useId, useRef, useState, type ReactNode } from 'react'

/**
 * Help popover that opens on hover, keyboard focus or tap. The trigger is a real button,
 * so touch and keyboard users can open it; Esc and outside taps close it.
 */
export function HelpTip({ label, children }: { label: string; children: ReactNode }) {
  const [pinned, setPinned] = useState(false)
  const [hovered, setHovered] = useState(false)
  const [focused, setFocused] = useState(false)
  const rootRef = useRef<HTMLSpanElement>(null)
  const tipId = useId()
  const open = pinned || hovered || focused

  useEffect(() => {
    if (!open) return
    const onKey = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setPinned(false)
        setHovered(false)
        setFocused(false)
      }
    }
    const onPointer = (event: PointerEvent) => {
      if (!rootRef.current?.contains(event.target as Node)) setPinned(false)
    }
    document.addEventListener('keydown', onKey)
    document.addEventListener('pointerdown', onPointer)
    return () => {
      document.removeEventListener('keydown', onKey)
      document.removeEventListener('pointerdown', onPointer)
    }
  }, [open])

  return (
    <span
      ref={rootRef}
      className="relative inline-flex"
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
    >
      <button
        type="button"
        aria-label={label}
        aria-describedby={open ? tipId : undefined}
        aria-expanded={open}
        onClick={() => setPinned((value) => !value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        className="flex h-11 w-11 items-center justify-center rounded-full text-[#505db8] hover:bg-[var(--soft-bg)] focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-[#505db8]"
      >
        <CircleHelp size={18} aria-hidden="true" />
      </button>
      {open && (
        <span
          id={tipId}
          role="tooltip"
          className="absolute right-0 top-11 z-30 w-[min(20rem,calc(100vw-2rem))] rounded-lg border border-[var(--app-border)] bg-[var(--panel-bg)] p-4 text-left text-sm font-normal text-[var(--app-text)] shadow-xl shadow-slate-300/40"
        >
          {children}
        </span>
      )}
    </span>
  )
}
