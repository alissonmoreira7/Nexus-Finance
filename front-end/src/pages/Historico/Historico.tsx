import { useEffect, useState } from 'react'
import {
  ArrowDownLeft,
  ArrowUpRight,
  ChevronLeft,
  ChevronRight,
  ListFilter,
  Search,
} from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAccounts } from '../../hooks/useAccounts'
import { api } from '../../services/api'
import '../app-pages.css'

type TypeFilter = 'ALL' | 'INCOME' | 'EXPENSE'

interface Transaction {
  id: string
  cleanDescription: string
  amount: number
  type: 'INCOME' | 'EXPENSE'
  category: string
  transactionDate: string
}

interface Page {
  content: Transaction[]
  page: number
  totalPages: number
  totalElements: number
}

const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const date = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString('pt-BR')

const filters = [
  { value: 'ALL', label: 'Todas', icon: ListFilter },
  { value: 'INCOME', label: 'Entradas', icon: ArrowDownLeft },
  { value: 'EXPENSE', label: 'Saídas', icon: ArrowUpRight },
] as const

export default function Historico() {
  const navigate = useNavigate()
  const { accounts, isLoading: loadingAccounts, error: accountError } = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Page>({ content: [], page: 0, totalPages: 0, totalElements: 0 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [type, setType] = useState<TypeFilter>('ALL')
  const [search, setSearch] = useState('')
  const [debouncedSearch, setDebouncedSearch] = useState('')
  const selectedAccountId = accountId || accounts[0]?.id || ''
  const selectedAccount = accounts.find(account => account.id === selectedAccountId)

  useEffect(() => {
    const timeout = window.setTimeout(() => setDebouncedSearch(search.trim()), 300)
    return () => window.clearTimeout(timeout)
  }, [search])

  useEffect(() => {
    if (!selectedAccountId) return
    let cancelled = false

    const loadTransactions = async () => {
      await Promise.resolve()
      if (cancelled) return
      setLoading(true)
      setError('')

      const params = new URLSearchParams({ page: String(page), size: '15' })
      if (type !== 'ALL') params.set('type', type)
      if (debouncedSearch) params.set('search', debouncedSearch)

      try {
        const response = await api<Page>(`/transactions/account/${selectedAccountId}?${params.toString()}`)
        if (!cancelled) setResult(response)
      } catch (requestError) {
        if (!cancelled) setError(requestError instanceof Error ? requestError.message : 'Não foi possível carregar o histórico')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadTransactions()
    return () => { cancelled = true }
  }, [selectedAccountId, page, type, debouncedSearch])

  const changeSearch = (value: string) => {
    setSearch(value)
    setPage(0)
  }

  const changeType = (value: TypeFilter) => {
    setType(value)
    setPage(0)
  }

  return <MobileLayout title="Histórico" subtitle={`${result.totalElements} transações encontradas`}>
    <section className="surface filter-card history-controls">
      <label className="field-label" htmlFor="history-account">Conta</label>
      <select
        id="history-account"
        className="field-select"
        value={selectedAccountId}
        disabled={loadingAccounts || !accounts.length}
        onChange={event => { setAccountId(event.target.value); setPage(0) }}
      >
        {accounts.map(account => <option key={account.id} value={account.id}>{account.bankName}</option>)}
      </select>

      <label className="history-search" htmlFor="history-search">
        <Search size={19} aria-hidden="true" />
        <span className="sr-only">Buscar transações</span>
        <input
          id="history-search"
          type="search"
          value={search}
          maxLength={100}
          placeholder="Buscar por descrição ou categoria"
          onChange={event => changeSearch(event.target.value)}
        />
      </label>
    </section>

    <div className="history-type-filters" role="group" aria-label="Filtrar transações por tipo">
      {filters.map(({ value, label, icon: Icon }) => <div className="history-filter-option" key={value}>
        <button
          type="button"
          className={type === value ? 'active' : ''}
          aria-pressed={type === value}
          aria-label={`Mostrar ${label.toLowerCase()}`}
          onClick={() => changeType(value)}
        >
          <Icon size={21} aria-hidden="true" />
        </button>
        <span>{label}</span>
      </div>)}
    </div>

    {(error || accountError) && <div className="state-box error" role="alert">{error || accountError}</div>}
    {loading ? <div className="state-box">Carregando transações...</div> : result.content.length ? <>
      <div className="transaction-feed">
        {result.content.map(item => <button
          type="button"
          key={item.id}
          className="transaction-item"
          onClick={() => navigate(`/historico/${item.id}`, {
            state: {
              transaction: {
                ...item,
                accountName: selectedAccount?.bankName || 'Conta selecionada',
              },
            },
          })}
          aria-label={`Ver detalhes de ${item.cleanDescription}`}
        >
          <span className={`transaction-icon ${item.type === 'INCOME' ? 'income' : 'expense'}`}>
            {item.type === 'INCOME' ? <ArrowDownLeft size={20} /> : <ArrowUpRight size={20} />}
          </span>
          <span className="transaction-copy">
            <strong>{item.cleanDescription}</strong>
            <span>{item.category} · {date(item.transactionDate)}</span>
          </span>
          <strong className={item.type === 'INCOME' ? 'amount-income' : 'amount-expense'}>
            {item.type === 'INCOME' ? '+' : '-'} {money(item.amount)}
          </strong>
        </button>)}
      </div>
      {result.totalPages > 1 && <nav className="pagination" aria-label="Paginação do histórico">
        <button type="button" className="secondary-action" disabled={page === 0} onClick={() => setPage(value => value - 1)}>
          <ChevronLeft size={18} /> Anterior
        </button>
        <span>{page + 1} de {result.totalPages}</span>
        <button type="button" className="secondary-action" disabled={page + 1 >= result.totalPages} onClick={() => setPage(value => value + 1)}>
          Próxima <ChevronRight size={18} />
        </button>
      </nav>}
    </> : <div className="state-box">Nenhuma transação encontrada. Tente mudar a busca ou o filtro.</div>}

  </MobileLayout>
}
