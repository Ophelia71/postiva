import { createContext, useContext, useEffect, useMemo, type ReactNode } from 'react'

type PreferencesContextValue = {
  t: (vi: string, en?: string) => string
}

const PreferencesContext = createContext<PreferencesContextValue | null>(null)

export function PreferencesProvider({ children }: { children: ReactNode }) {
  useEffect(() => {
    document.documentElement.dataset.theme = 'light'
    document.documentElement.classList.remove('theme-dark')
    document.documentElement.classList.add('theme-light')
    document.documentElement.lang = 'vi'
    localStorage.removeItem('postiva-theme')
    localStorage.removeItem('postiva-language')
  }, [])

  const value = useMemo<PreferencesContextValue>(() => ({
    t: (vi) => vi,
  }), [])

  return (
    <PreferencesContext.Provider value={value}>
      {children}
    </PreferencesContext.Provider>
  )
}

export function usePreferences() {
  const value = useContext(PreferencesContext)
  if (!value) {
    throw new Error('usePreferences must be used inside PreferencesProvider')
  }
  return value
}
