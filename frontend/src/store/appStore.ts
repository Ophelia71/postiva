import { create } from 'zustand'

type AppState = {
  backendBaseUrl: string
  setBackendBaseUrl: (backendBaseUrl: string) => void
}

export const useAppStore = create<AppState>((set) => ({
  backendBaseUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
  setBackendBaseUrl: (backendBaseUrl) => set({ backendBaseUrl }),
}))
