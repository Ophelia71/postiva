import { useEffect, useRef } from 'react'

const interactiveSelector = 'a, button, input, textarea, select, [role="button"]'

export function CustomCursor() {
  const dotRef = useRef<HTMLDivElement>(null)
  const ringRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const finePointer = window.matchMedia('(pointer: fine)')
    const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)')

    if (!finePointer.matches || reducedMotion.matches) return

    const body = document.body
    const dot = dotRef.current
    const ring = ringRef.current
    if (!dot || !ring) return

    let pointerX = -100
    let pointerY = -100
    let ringX = -100
    let ringY = -100
    let animationFrame = 0

    body.classList.add('custom-cursor-enabled')

    const render = () => {
      ringX += (pointerX - ringX) * 0.18
      ringY += (pointerY - ringY) * 0.18
      dot.style.transform = `translate3d(${pointerX}px, ${pointerY}px, 0)`
      ring.style.transform = `translate3d(${ringX}px, ${ringY}px, 0)`
      animationFrame = window.requestAnimationFrame(render)
    }

    const handlePointerMove = (event: PointerEvent) => {
      pointerX = event.clientX
      pointerY = event.clientY
      const interactive = event.target instanceof Element && Boolean(event.target.closest(interactiveSelector))
      ring.classList.toggle('is-interactive', interactive)
      dot.classList.toggle('is-interactive', interactive)
      dot.classList.add('is-visible')
      ring.classList.add('is-visible')
    }

    const handlePointerLeave = () => {
      dot.classList.remove('is-visible')
      ring.classList.remove('is-visible')
    }

    window.addEventListener('pointermove', handlePointerMove, { passive: true })
    document.documentElement.addEventListener('mouseleave', handlePointerLeave)
    animationFrame = window.requestAnimationFrame(render)

    return () => {
      body.classList.remove('custom-cursor-enabled')
      window.removeEventListener('pointermove', handlePointerMove)
      document.documentElement.removeEventListener('mouseleave', handlePointerLeave)
      window.cancelAnimationFrame(animationFrame)
    }
  }, [])

  return (
    <>
      <div ref={ringRef} className="rescale-cursor-ring" aria-hidden="true" />
      <div ref={dotRef} className="rescale-cursor-dot" aria-hidden="true" />
    </>
  )
}
