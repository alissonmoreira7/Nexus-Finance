const API_URL = (import.meta.env.VITE_API_URL as string | undefined) ?? 'http://localhost:8081/api/v1'

interface ApiError { message?: string }

export const AUTH_UNAUTHORIZED_EVENT = 'nexus:auth-unauthorized'

export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = localStorage.getItem('authToken')
  const response = await fetch(`${API_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })
  if (!response.ok) {
    const body = await response.json().catch(() => ({})) as ApiError
    if (response.status === 401) {
      localStorage.removeItem('authToken')
      localStorage.removeItem('user')
      window.dispatchEvent(new Event(AUTH_UNAUTHORIZED_EVENT))
    }
    throw new Error(body.message ?? `Falha na API (${response.status})`)
  }
  if (response.status === 204) return undefined as T
  return response.json() as Promise<T>
}
