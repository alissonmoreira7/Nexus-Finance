import { useEffect, useState } from 'react'
import { useAuth } from '../contexts/AuthContext'
import { api } from '../services/api'

export interface Account { id: string; bankName: string; userId: string }

export function useAccounts() {
  const { user } = useAuth()
  const [accounts, setAccounts] = useState<Account[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  useEffect(() => {
    if (!user) return
    setIsLoading(true)
    api<Account[]>(`/accounts/user/${user.id}`).then(setAccounts)
      .catch(e => setError(e instanceof Error ? e.message : 'Não foi possível carregar as contas'))
      .finally(() => setIsLoading(false))
  }, [user])
  return { accounts, isLoading, error }
}
