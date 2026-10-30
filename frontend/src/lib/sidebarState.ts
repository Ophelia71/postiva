// Remembered per browser: a convenience only, so a blocked storage just means the menu starts open.
const SIDEBAR_COLLAPSED_KEY = 'postiva.sidebar.collapsed'

export function readSidebarCollapsed() {
  try {
    return window.localStorage.getItem(SIDEBAR_COLLAPSED_KEY) === '1'
  } catch {
    return false
  }
}

export function writeSidebarCollapsed(collapsed: boolean) {
  try {
    window.localStorage.setItem(SIDEBAR_COLLAPSED_KEY, collapsed ? '1' : '0')
  } catch {
    // Ignored, see above.
  }
}
