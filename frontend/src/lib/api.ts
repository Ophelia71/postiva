import axios from 'axios'
import { AUTH_EXPIRED_EVENT, clearAuthSessionIfTokenMatches, getAuthToken } from './auth'

export type ApiResponse<T> = {
  success: boolean
  data: T
  message: string | null
}

export class ApiResponseError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ApiResponseError'
  }
}

const configuredApiBaseUrl = import.meta.env.VITE_API_BASE_URL?.trim()
const normalizedApiBaseUrl = configuredApiBaseUrl
  ? configuredApiBaseUrl.replace(/\/+$/, '').endsWith('/api')
    ? configuredApiBaseUrl.replace(/\/+$/, '')
    : `${configuredApiBaseUrl.replace(/\/+$/, '')}/api`
  : '/api'

export const api = axios.create({
  baseURL: normalizedApiBaseUrl,
  timeout: 120000,
})

export const FACEBOOK_SYNC_TIMEOUT_MS = 600000

api.interceptors.request.use((config) => {
  const token = getAuthToken()
  if (token && !config.headers.has('Authorization')) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

api.interceptors.response.use(
  (response) => {
    const authorization = String(response.config.headers?.Authorization ?? '')
    if (authorization.startsWith('Bearer ') && authorization.slice(7) !== getAuthToken()) {
      return Promise.reject(new ApiResponseError('Phiên đăng nhập đã thay đổi. Vui lòng thử lại.'))
    }
    if (!isApiResponse(response.data)) {
      return response
    }

    if (!response.data.success) {
      return Promise.reject(new ApiResponseError(response.data.message ?? 'Yêu cầu không thành công'))
    }

    response.data = response.data.data
    return response
  },
  (error) => {
    const status = axios.isAxiosError(error) ? error.response?.status : undefined
    const requestUrl = axios.isAxiosError(error) ? String(error.config?.url ?? '') : ''
    const isAuthRequest = requestUrl.includes('/auth/login') || requestUrl.includes('/auth/register')

    const authorization = axios.isAxiosError(error)
      ? String(error.config?.headers?.Authorization ?? '') : ''
    if (status === 401 && !isAuthRequest && authorization.startsWith('Bearer ')
      && clearAuthSessionIfTokenMatches(authorization.slice(7))) {
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT))
    }

    return Promise.reject(error)
  },
)

function isApiResponse(value: unknown): value is ApiResponse<unknown> {
  return typeof value === 'object'
    && value !== null
    && typeof (value as { success?: unknown }).success === 'boolean'
    && Object.prototype.hasOwnProperty.call(value, 'data')
}
