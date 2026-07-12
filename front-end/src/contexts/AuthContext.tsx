import { createContext, useContext, useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { api } from '../services/api'

export interface User { id: string; email: string; name: string }
interface LoginResponse { token: string; user: User }
interface AuthContextType {
  user: User | null; isAuthenticated: boolean; isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (name: string, cpf: string, email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [isLoading, setIsLoading] = useState(true)
  useEffect(() => {
    const token = localStorage.getItem('authToken'), stored = localStorage.getItem('user')
    if (token && stored) try { setUser(JSON.parse(stored) as User) } catch { localStorage.clear() }
    setIsLoading(false)
  }, [])

  const persistLogin = (result: LoginResponse) => {
    localStorage.setItem('authToken', result.token); localStorage.setItem('user', JSON.stringify(result.user)); setUser(result.user)
  }
  const login = async (email: string, password: string) => {
    setIsLoading(true)
    try { persistLogin(await api<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })) }
    finally { setIsLoading(false) }
  }
  const register = async (name: string, cpf: string, email: string, password: string) => {
    setIsLoading(true)
    try {
      await api('/users', { method: 'POST', body: JSON.stringify({ name, cpf, email, password }) })
      const result = await api<LoginResponse>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) })
      persistLogin(result)
      await api('/accounts', { method: 'POST', body: JSON.stringify({ bankName: 'Conta principal' }) })
    } finally { setIsLoading(false) }
  }
  const logout = () => { localStorage.removeItem('authToken'); localStorage.removeItem('user'); setUser(null) }
  return <AuthContext.Provider value={{ user, isAuthenticated: !!user, isLoading, login, register, logout }}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth deve ser usado dentro de AuthProvider')
  return context
}
