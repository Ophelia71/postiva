import axios from 'axios'

export function getErrorMessage(error: unknown, fallback: string) {
  if (axios.isAxiosError(error)) {
    const data = error.response?.data
    const parsed = typeof data === 'string' ? parseJson(data) : data

    if (parsed && typeof parsed === 'object') {
      if ('error' in parsed) {
        const metaError = parsed.error
        if (metaError && typeof metaError === 'object' && 'message' in metaError && typeof metaError.message === 'string') {
          return metaError.message
        }
      }

      if ('message' in parsed && typeof parsed.message === 'string') {
        return parsed.message
      }
    }

    if (typeof data === 'string' && data.trim()) {
      return data
    }

    return error.message
  }

  return error instanceof Error ? error.message : fallback
}

function parseJson(value: string) {
  try {
    return JSON.parse(value)
  } catch {
    return null
  }
}
