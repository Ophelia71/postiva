import { useEffect, useState } from 'react'
import { useLocation } from 'react-router-dom'
import { api } from './api'
import { getAuthSession } from './auth'

type EventType = 'PAGE_VIEW' | 'SCROLL' | 'ENGAGED' | 'HEARTBEAT' | 'IDENTIFY' | 'EXCLUDE'
type Source = 'DIRECT' | 'ORGANIC' | 'SOCIAL' | 'REFERRAL' | 'PAID' | 'EMAIL' | 'OTHER'
type Event = { type: EventType; visitorId: string; sessionId: string; viewId?: string; path?: string; source?: Source; scrollDepth?: number }
type BrowserSession = { id: string; lastAt: number }

const visitorKey = 'postiva.analytics.visitor'
const sessionKey = 'postiva.analytics.session'
const endpoint = `${String(api.defaults.baseURL ?? '/api').replace(/\/+$/, '')}/analytics/events`
let delivery: Promise<unknown> = Promise.resolve()

function trackingAllowed() {
  return navigator.doNotTrack !== '1'
    && !(navigator as Navigator & { globalPrivacyControl?: boolean }).globalPrivacyControl
    && getAuthSession()?.role !== 'ADMIN'
}

function ids() {
  if (!trackingAllowed()) return null
  try {
    let visitorId = localStorage.getItem(visitorKey)
    if (!visitorId) {
      visitorId = crypto.randomUUID()
      localStorage.setItem(visitorKey, visitorId)
    }
    const now = Date.now()
    let stored: BrowserSession | null = null
    try { stored = JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null') as BrowserSession | null } catch { /* reset invalid data */ }
    if (!stored || typeof stored.id !== 'string' || typeof stored.lastAt !== 'number'
      || now - stored.lastAt > 30 * 60_000 || now < stored.lastAt) {
      stored = { id: crypto.randomUUID(), lastAt: now }
    }
    stored.lastAt = now
    sessionStorage.setItem(sessionKey, JSON.stringify(stored))
    return { visitorId, sessionId: stored.id }
  } catch { return null } // Storage can be disabled in private browsing.
}

function source(): Source {
  const query = new URLSearchParams(location.search)
  const medium = query.get('utm_medium')?.toLowerCase()
  const campaignSource = query.get('utm_source')?.toLowerCase()
  if (medium === 'cpc' || medium === 'paid' || medium === 'ppc') return 'PAID'
  if (medium === 'organic') return 'ORGANIC'
  if (medium === 'email') return 'EMAIL'
  if (medium === 'social') return 'SOCIAL'
  if (campaignSource) {
    if (/^(facebook|instagram|threads|linkedin|tiktok|youtube|x|twitter)$/.test(campaignSource)) return 'SOCIAL'
    return 'OTHER'
  }
  try {
    if (!document.referrer) return 'DIRECT'
    const referrer = new URL(document.referrer)
    if (referrer.hostname.replace(/^www\./, '') === location.hostname.replace(/^www\./, '')) return 'DIRECT'
    if (/(^|\.)google\.|(^|\.)bing\.|(^|\.)yahoo\.|(^|\.)duckduckgo\./i.test(referrer.hostname)) return 'ORGANIC'
    if (/(^|\.)(facebook|instagram|threads|linkedin|tiktok|youtube|x|twitter)\./i.test(referrer.hostname)) return 'SOCIAL'
    return 'REFERRAL'
  } catch { return 'OTHER' }
}

function send(event: Event) {
  if (!trackingAllowed() && event.type !== 'EXCLUDE') return
  try {
    const stored = JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null') as BrowserSession | null
    if (stored?.id === event.sessionId) sessionStorage.setItem(sessionKey, JSON.stringify({ ...stored, lastAt: Date.now() }))
  } catch { /* Tracking is optional when browser storage is unavailable. */ }
  const token = getAuthSession()?.token
  // Deliver the page view before scroll/engagement events for the same new session.
  delivery = delivery.catch(() => {}).then(() => fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(event),
    keepalive: true,
    cache: 'no-store',
    signal: AbortSignal.timeout(8000),
  })).catch(() => { /* Analytics must never block the user journey. */ })
}

export function identifyWebAnalyticsUser() {
  if (getAuthSession()?.role === 'ADMIN') {
    try {
      const visitorId = localStorage.getItem(visitorKey)
      const stored = JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null') as BrowserSession | null
      if (visitorId && stored?.id) send({ type: 'EXCLUDE', visitorId, sessionId: stored.id })
      sessionStorage.removeItem(sessionKey)
    } catch { /* No session to exclude. */ }
    return
  }
  if (getAuthSession()?.role !== 'USER') return
  const current = ids()
  if (current) send({ type: 'IDENTIFY', ...current })
}

export function WebAnalyticsTracker() {
  const { pathname } = useLocation()
  const [sessionRevision, setSessionRevision] = useState(0)

  useEffect(() => {
    const current = ids()
    if (!current) return
    // Never persist URL query strings: reset links and tokens may be present there.
    const path = pathname.slice(0, 200)
    const viewId = crypto.randomUUID()
    send({ type: 'PAGE_VIEW', ...current, viewId, path, source: source() })

    let engaged = false
    let scrollDepth = 0
    let lastActivityAt = Date.now()
    const noteActivity = () => {
      try {
        const stored = JSON.parse(sessionStorage.getItem(sessionKey) ?? 'null') as BrowserSession | null
        if (stored?.id === current.sessionId && Date.now() - stored.lastAt > 30 * 60_000) {
          setSessionRevision((value) => value + 1)
          return false
        }
      } catch { return false }
      lastActivityAt = Date.now()
      return true
    }
    const engage = () => {
      if (engaged) return
      engaged = true
      send({ type: 'ENGAGED', ...current })
    }
    const onClick = (event: MouseEvent) => {
      if (!noteActivity()) return
      if ((event.target as Element | null)?.closest('a, button, input, select, textarea')) engage()
    }
    const onScroll = () => {
      if (!noteActivity()) return
      const available = document.documentElement.scrollHeight - window.innerHeight
      if (available <= 0) return
      const percent = Math.min(100, Math.round(window.scrollY * 100 / available))
      for (const threshold of [25, 50, 75, 100]) {
        if (percent >= threshold && scrollDepth < threshold) {
          scrollDepth = threshold
          send({ type: 'SCROLL', ...current, viewId, scrollDepth: threshold })
        }
      }
    }
    const onKeyDown = () => { noteActivity() }
    const onVisibility = () => { if (!document.hidden) noteActivity() }
    const engagementTimer = window.setTimeout(() => { if (!document.hidden) engage() }, 10_000)
    const heartbeat = window.setInterval(() => {
      if (!document.hidden && Date.now() - lastActivityAt < 30_000) send({ type: 'HEARTBEAT', ...current })
    }, 15_000)
    document.addEventListener('click', onClick, true)
    document.addEventListener('keydown', onKeyDown, true)
    document.addEventListener('visibilitychange', onVisibility)
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => {
      window.clearTimeout(engagementTimer)
      window.clearInterval(heartbeat)
      document.removeEventListener('click', onClick, true)
      document.removeEventListener('keydown', onKeyDown, true)
      document.removeEventListener('visibilitychange', onVisibility)
      window.removeEventListener('scroll', onScroll)
    }
  }, [pathname, sessionRevision])

  return null
}
