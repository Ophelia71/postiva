import type { AuthSession } from './auth'

/**
 * "Try without an account": the real app shell runs on sample data until the visitor signs up. The flag lives in
 * sessionStorage, so closing the tab ends the demo, and it never applies while a real session exists.
 */
const DEMO_FLAG_KEY = 'postiva.demo'
export const DEMO_SIGNUP_EVENT = 'postiva:demo-signup'

export const DEMO_SESSION: AuthSession = {
  token: 'demo',
  userId: 'demo-user',
  // The owner of the demo's shop, Mây Coffee, so the account matches everything else the visitor sees.
  email: 'ngocmay@maycoffee.vn',
  // Greetings use the last word of the name ("Chào Mây").
  fullName: 'Trần Ngọc Mây',
  role: 'USER',
}

export function isDemoActive() {
  try {
    return window.sessionStorage.getItem(DEMO_FLAG_KEY) === '1'
  } catch {
    return false
  }
}

export function startDemo() {
  try {
    window.sessionStorage.setItem(DEMO_FLAG_KEY, '1')
  } catch {
    /* Storage blocked: the demo cannot start, the link still leads to the sign-in page. */
  }
}

export function exitDemo() {
  try {
    window.sessionStorage.removeItem(DEMO_FLAG_KEY)
  } catch {
    /* Nothing stored, nothing to clear. */
  }
}

/** Asks the demo shell to show the sign-up prompt; {@code action} completes "Tạo tài khoản miễn phí để ...". */
export function requestDemoSignup(action: string) {
  window.dispatchEvent(new CustomEvent<string>(DEMO_SIGNUP_EVENT, { detail: action }))
}
