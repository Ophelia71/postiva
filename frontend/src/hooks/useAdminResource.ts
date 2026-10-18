import { useCallback, useEffect, useState } from 'react'
import { api } from '../lib/api'
import { getErrorMessage } from '../lib/errors'

type Resource<T> = { url: string; data: T | null; error: string | null; loading: boolean }

export function useAdminResource<T>(url: string) {
  const [revision, setRevision] = useState(0)
  const [state, setState] = useState<Resource<T>>({ url, data: null, error: null, loading: true })
  const reload = useCallback(() => setRevision((value) => value + 1), [])

  useEffect(() => {
    const controller = new AbortController()
    setState({ url, data: null, error: null, loading: true })
    api.get<T>(url, { signal: controller.signal })
      .then((response) => {
        if (!controller.signal.aborted) setState({ url, data: response.data, error: null, loading: false })
      })
      .catch((error: unknown) => {
        if (!controller.signal.aborted) setState({
          url, data: null, error: getErrorMessage(error, 'Không thể tải dữ liệu. Vui lòng thử lại.'), loading: false,
        })
      })
    return () => controller.abort()
  }, [url, revision])

  return { ...(state.url === url ? state : { data: null, error: null, loading: true }), reload }
}
