import { useEffect, useState } from 'react'
import { getAuthSession, type UserRole } from './auth'

/**
 * Signs the user out after a period without interaction, shared across tabs through localStorage:
 * - while Postiva itself is working (AI writing, uploads, publishing) the clock is held, never signing out;
 * - while a post is being written and not yet saved, the limit grows to two hours;
 * - otherwise 30 minutes for shops, 15 for admins.
 * Unsaved posts are kept on this device at sign-out and offered back after the same user signs in again.
 */
export const IDLE_LIMIT_MS: Record<UserRole, number> = { USER: 30 * 60_000, ADMIN: 15 * 60_000 }
export const UNSAVED_LIMIT_MS = 2 * 60 * 60_000
export const WARNING_MS = 2 * 60_000

const LAST_ACTIVITY_KEY = 'postiva.idle.lastActivity'
const UNSAVED_AT_KEY = 'postiva.idle.unsavedAt'
const LOGOUT_KEY = 'postiva.idle.logout'
const NOTICE_KEY = 'postiva.idle.notice'
const DRAFT_PREFIX = 'postiva.draft.'
// Another tab with unsaved work refreshes its mark this often; a mark older than twice that is gone.
const HEARTBEAT_MS = 20_000
const DRAFT_MAX_AGE_MS = 7 * 24 * 60 * 60_000
const NOTICE_MAX_AGE_MS = 10 * 60_000

export type SessionWork = {
  /** Postiva is doing something for the user right now; never sign out meanwhile. */
  busy?: boolean
  /** The user has written something not saved yet. */
  unsaved?: boolean
  /** Kept on this device if the session ends while unsaved, and handed back by takeDraft(key). */
  draftKey?: string
  draft?: () => unknown
}

const works = new Map<number, SessionWork>()
let nextWorkId = 0

/** Tells the idle sign-out what this screen is doing. */
export function useSessionWork(work: SessionWork) {
  const [id] = useState(() => ++nextWorkId)
  useEffect(() => {
    works.set(id, work)
  })
  useEffect(() => () => {
    works.delete(id)
  }, [id])
}

export function currentWork() {
  let busy = false
  let unsaved = false
  works.forEach((work) => {
    busy ||= Boolean(work.busy)
    unsaved ||= Boolean(work.unsaved)
  })
  return { busy, unsaved }
}

export function recordActivity(now = Date.now()) {
  write(LAST_ACTIVITY_KEY, String(now))
}

export function markUnsavedWork(now = Date.now()) {
  write(UNSAVED_AT_KEY, String(now))
}

/** A fresh sign-in starts a fresh idle clock. */
export function startIdleClock() {
  recordActivity()
  remove(UNSAVED_AT_KEY)
  remove(NOTICE_KEY)
}

export function idleState(role: UserRole, now = Date.now()) {
  const last = readNumber(LAST_ACTIVITY_KEY) ?? now
  const unsavedAt = readNumber(UNSAVED_AT_KEY)
  const unsaved = currentWork().unsaved || (unsavedAt !== null && now - unsavedAt < HEARTBEAT_MS * 2)
  const limit = unsaved ? UNSAVED_LIMIT_MS : IDLE_LIMIT_MS[role]
  return { elapsed: Math.max(0, now - last), limit }
}

/** True when a session stored earlier (e.g. before closing the browser) has already gone idle too long. */
export function storedSessionIsIdle(role: UserRole) {
  if (readNumber(LAST_ACTIVITY_KEY) === null) {
    recordActivity()
    return false
  }
  const { elapsed, limit } = idleState(role)
  return elapsed >= limit
}

// The sign-in page says nothing about a plain idle sign-out; it only speaks up when a half-written post was kept.
const DRAFT_KEPT_NOTICE = 'Bài bạn đang viết dở vẫn còn. Đăng nhập lại để viết tiếp.'

/** Saves unsaved work, tells other tabs, and leaves the notice (if any) for whichever tab shows the sign-in page. */
export function announceIdleLogout() {
  const notice = saveDrafts() > 0 ? DRAFT_KEPT_NOTICE : ''
  if (notice) write(NOTICE_KEY, JSON.stringify({ at: Date.now(), notice }))
  else remove(NOTICE_KEY)
  write(LOGOUT_KEY, String(Date.now()))
  return notice
}

export function readIdleNotice() {
  const raw = read(NOTICE_KEY)
  if (!raw) return ''
  try {
    const value = JSON.parse(raw) as { at?: number; notice?: string }
    return typeof value.at === 'number' && Date.now() - value.at < NOTICE_MAX_AGE_MS && typeof value.notice === 'string'
      ? value.notice
      : ''
  } catch {
    return ''
  }
}

export function isIdleLogoutSignal(event: StorageEvent) {
  return event.key === LOGOUT_KEY
}

/** Stores every unsaved screen's draft for the signed-in user; returns how many were kept. */
export function saveDrafts() {
  const userId = getAuthSession()?.userId
  if (!userId) return 0
  let kept = 0
  works.forEach((work) => {
    if (!work.unsaved || !work.draftKey || !work.draft) return
    write(draftStorageKey(userId, work.draftKey), JSON.stringify({ savedAt: Date.now(), value: work.draft() }))
    kept++
  })
  return kept
}

/** A draft kept for this user when their last session ended; call clearDraft once it is back on screen. */
export function readDraft<T>(key: string): T | null {
  const userId = getAuthSession()?.userId
  if (!userId) return null
  const raw = read(draftStorageKey(userId, key))
  if (!raw) return null
  try {
    const stored = JSON.parse(raw) as { savedAt?: number; value?: T }
    return typeof stored.savedAt === 'number' && Date.now() - stored.savedAt < DRAFT_MAX_AGE_MS && stored.value !== undefined
      ? stored.value
      : null
  } catch {
    return null
  }
}

export function clearDraft(key: string) {
  const userId = getAuthSession()?.userId
  if (userId) remove(draftStorageKey(userId, key))
}

function draftStorageKey(userId: string, key: string) {
  return `${DRAFT_PREFIX}${userId}.${key}`
}

function readNumber(key: string) {
  const value = Number(read(key))
  return read(key) !== null && Number.isFinite(value) ? value : null
}

function read(key: string) {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function write(key: string, value: string) {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // Storage may be full or blocked; the in-tab clock still works.
  }
}

function remove(key: string) {
  try {
    window.localStorage.removeItem(key)
  } catch {
    // Same as above.
  }
}
