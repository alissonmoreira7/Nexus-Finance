import { useEffect, useState } from 'react'
import { BarChart3, CalendarClock, ChevronLeft, ChevronRight, Eye, EyeOff, Plus } from 'lucide-react'
import { Link } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAuth } from '../../contexts/AuthContext'
import { api } from '../../services/api'
import './dashboard_style.css'

interface Transaction { id: string; cleanDescription: string; amount: number; type: 'INCOME' | 'EXPENSE'; category: string; transactionDate: string }
interface Account { id: string; bankName: string; userId: string; balance: number }
interface Summary { totalIncome: number; totalExpense: number; balance: number; expensesByCategory: Record<string, number> }
interface Page<T> { content: T[]; totalElements: number; totalPages: number }

const TRANSACTIONS_PER_PAGE = 15

const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

function Dashboard() {
  const { user } = useAuth()
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [summary, setSummary] = useState<Summary>({ totalIncome: 0, totalExpense: 0, balance: 0, expensesByCategory: {} })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [showValues, setShowValues] = useState(true)
  const [transactionPage, setTransactionPage] = useState(0)
  const [transactionTotalElements, setTransactionTotalElements] = useState(0)
  const [transactionsLoading, setTransactionsLoading] = useState(false)
  const [transactionsError, setTransactionsError] = useState('')

  useEffect(() => {
    if (!user) return
    const load = async () => {
      setIsLoading(true); setError('')
      try {
        const baseAccounts = await api<Omit<Account, 'balance'>[]>(`/accounts/user/${user.id}`)
        if (!baseAccounts.length) {
          setAccounts([]); setTransactions([]); setTransactionTotalElements(0); setTransactionPage(0); return
        }
        const summaries = await Promise.all(baseAccounts.map(account => api<Summary>(`/analytics/${account.id}/summary`)))
        setAccounts(baseAccounts.map((account, index) => ({ ...account, balance: summaries[index].balance })))
        setSummary(summaries.reduce((total, current) => ({
          totalIncome: total.totalIncome + current.totalIncome,
          totalExpense: total.totalExpense + current.totalExpense,
          balance: total.balance + current.balance,
          expensesByCategory: Object.entries(current.expensesByCategory).reduce((categories, [name, value]) => {
            categories[name] = (categories[name] ?? 0) + value; return categories
          }, total.expensesByCategory),
        }), { totalIncome: 0, totalExpense: 0, balance: 0, expensesByCategory: {} } as Summary))
        setTransactionPage(0)
      } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível carregar o dashboard') }
      finally { setIsLoading(false) }
    }
    void load()
  }, [user])

  useEffect(() => {
    if (!accounts.length) return
    const loadTransactions = async () => {
      setTransactionsLoading(true); setTransactionsError('')
      try {
        const requiredCount = (transactionPage + 1) * TRANSACTIONS_PER_PAGE
        const statements = await Promise.all(accounts.map(account => loadAccountTransactions(account.id, requiredCount)))
        const totalElements = statements.reduce((total, statement) => total + statement.totalElements, 0)
        const start = transactionPage * TRANSACTIONS_PER_PAGE
        setTransactionTotalElements(totalElements)
        setTransactions(statements.flatMap(statement => statement.content)
          .sort((a, b) => b.transactionDate.localeCompare(a.transactionDate) || b.id.localeCompare(a.id))
          .slice(start, start + TRANSACTIONS_PER_PAGE))
      } catch (e) {
        setTransactions([])
        setTransactionsError(e instanceof Error ? e.message : 'Não foi possível carregar as transações')
      } finally { setTransactionsLoading(false) }
    }
    void loadTransactions()
  }, [accounts, transactionPage])

  const categories = Object.entries(summary.expensesByCategory)
  const largestCategory = Math.max(1, ...categories.map(([, value]) => value))
  const displayMoney = (value: number) => showValues ? money(value) : '••••••'
  const privacyLabel = showValues ? 'Ocultar valores monetários' : 'Mostrar valores monetários'
  const transactionTotalPages = Math.ceil(transactionTotalElements / TRANSACTIONS_PER_PAGE)

return <MobileLayout headerAction={<button type="button" className="privacy-toggle" onClick={() => setShowValues(value => !value)}
      aria-label={privacyLabel} title={privacyLabel} aria-pressed={!showValues}>
      {showValues ? <Eye size={23} aria-hidden="true" /> : <EyeOff size={23} aria-hidden="true" />}
    </button>}>
      <span className="sr-only" aria-live="polite">{showValues ? 'Valores monetários visíveis' : 'Valores monetários ocultos'}</span>
      {error && <div className="dashboard-error" role="alert">{error}</div>}
      {isLoading ? <div className="dashboard-state">Carregando seus dados...</div> : <>
        <section className="section dashboard-accounts">
          <h3>Suas Contas</h3>
          <div className="accounts-list">
            {accounts.length ? accounts.map(account => <div key={account.id} className="account-item">
            <div className="account-info">
              <p className="account-name">
                {account.bankName}
              </p>
              <span className="account-type">Conta</span>
            </div>
            <p className="account-balance">
              {displayMoney(account.balance)}
            </p>
            </div>) : <p>Nenhuma conta cadastrada.</p>}
          </div>
        </section>

        <nav className="dashboard-shortcuts" aria-label="Atalhos do início">
          <Link to="/relatorios" className="dashboard-shortcut">
            <span className="dashboard-shortcut-icon" aria-hidden="true"><BarChart3 size={23} /></span>
            <strong>Relatórios</strong>
          </Link>
          <Link to="/gastos-previstos" className="dashboard-shortcut">
            <span className="dashboard-shortcut-icon" aria-hidden="true"><CalendarClock size={23} /></span>
            <strong>Gastos previstos</strong>
          </Link>
        </nav>

        <div className="dashboard-content">
          <section className="section dashboard-categories">
            <h3>Despesas por Categoria</h3>
            <div className="categories-list">
              {categories.length ? categories.map(([name, amount]) =>
              <div key={name} className="category-item">

                <div className="category-header">
                  <p className="category-name">{name}</p>
                  <span className="category-amount">{displayMoney(amount)}</span>
                </div>
                <div className="category-bar">
                <div className="category-fill" style={{ width: showValues ? `${amount / largestCategory * 100}%` : '0%' }} /></div>

              </div>) : <p>Nenhuma despesa neste mês.</p>}
            </div>
          </section>
        </div>

        <section className="section dashboard-transactions"><h3>Transações Recentes</h3><div className="transactions-table">
          <div className="table-header"><div className="col-description">Descrição</div><div className="col-category">Categoria</div><div className="col-date">Data</div><div className="col-amount">Valor</div></div>
            {transactionsLoading ? <p className="dashboard-state">Carregando transações...</p>
              : transactionsError ? <p className="dashboard-error" role="alert">{transactionsError}</p>
              : transactions.length ? transactions.map(transaction => <div key={transaction.id} className="table-row"><div className="col-description"><p>{transaction.cleanDescription}</p></div><div className="col-category"><span className="badge">{transaction.category}</span></div><div className="col-date"><p>{new Date(`${transaction.transactionDate}T00:00:00`).toLocaleDateString('pt-BR')}</p></div><div className={`col-amount ${transaction.type === 'INCOME' ? 'income' : 'expense'}`}><p>{showValues ? `${transaction.type === 'INCOME' ? '+' : '-'} ${money(transaction.amount)}` : '••••••'}</p></div></div>) : <p className="dashboard-state">Nenhuma transação importada.</p>}
          </div>
          {!transactionsLoading && !transactionsError && transactionTotalPages > 1 && <nav className="dashboard-pagination" aria-label="Paginação das transações recentes">
            <button type="button" className="secondary-action" disabled={transactionPage === 0} onClick={() => setTransactionPage(page => page - 1)}>
              <ChevronLeft size={20} aria-hidden="true" /> Anterior
            </button>
            <span>Página {transactionPage + 1} de {transactionTotalPages}</span>
            <button type="button" className="secondary-action" disabled={transactionPage + 1 >= transactionTotalPages} onClick={() => setTransactionPage(page => page + 1)}>
              Próxima <ChevronRight size={20} aria-hidden="true" />
            </button>
          </nav>}
        </section>
      </>}

      <Link to="/adicionar" className="add-transaction-fab" aria-label="Adicionar transação" title="Adicionar transação">
        <Plus size={30} strokeWidth={2.5} aria-hidden="true" />
      </Link>
  </MobileLayout>
}

async function loadAccountTransactions(accountId: string, requiredCount: number) {
  const requestSize = Math.min(100, requiredCount)
  const firstPage = await api<Page<Transaction>>(`/transactions/account/${accountId}?page=0&size=${requestSize}`)
  if (firstPage.content.length >= requiredCount || firstPage.totalPages <= 1) return firstPage

  const pagesToLoad = Math.min(firstPage.totalPages, Math.ceil(requiredCount / requestSize))
  const remainingPages = await Promise.all(Array.from({ length: pagesToLoad - 1 }, (_, index) =>
    api<Page<Transaction>>(`/transactions/account/${accountId}?page=${index + 1}&size=${requestSize}`)))
  return {
    ...firstPage,
    content: [firstPage, ...remainingPages].flatMap(page => page.content).slice(0, requiredCount),
  }
}

export default Dashboard
