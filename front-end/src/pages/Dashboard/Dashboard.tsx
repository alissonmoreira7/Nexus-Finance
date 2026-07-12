import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { TrendingUp, TrendingDown, Wallet, Target } from 'lucide-react'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAuth } from '../../contexts/AuthContext'
import { api } from '../../services/api'
import './style.css'

interface Transaction { id: string; cleanDescription: string; amount: number; type: 'INCOME' | 'EXPENSE'; category: string; transactionDate: string }
interface Account { id: string; bankName: string; userId: string; balance: number }
interface Summary { totalIncome: number; totalExpense: number; balance: number; expensesByCategory: Record<string, number> }
interface Page<T> { content: T[] }

const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

function Dashboard() {
  const { user } = useAuth()
  const [transactions, setTransactions] = useState<Transaction[]>([])
  const [accounts, setAccounts] = useState<Account[]>([])
  const [summary, setSummary] = useState<Summary>({ totalIncome: 0, totalExpense: 0, balance: 0, expensesByCategory: {} })
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) return
    const load = async () => {
      setIsLoading(true); setError('')
      try {
        const baseAccounts = await api<Omit<Account, 'balance'>[]>(`/accounts/user/${user.id}`)
        if (!baseAccounts.length) { setAccounts([]); setTransactions([]); return }
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
        const statements = await Promise.all(baseAccounts.map(account => api<Page<Transaction>>(`/transactions/account/${account.id}?page=0&size=10`)))
        setTransactions(statements.flatMap(page => page.content).sort((a, b) => b.transactionDate.localeCompare(a.transactionDate)).slice(0, 10))
      } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível carregar o dashboard') }
      finally { setIsLoading(false) }
    }
    void load()
  }, [user])

  const categories = Object.entries(summary.expensesByCategory)
  const largestCategory = Math.max(1, ...categories.map(([, value]) => value))

  return <MobileLayout eyebrow="Visão geral" title={`Olá, ${user?.name.split(' ')[0] ?? ''}`} subtitle="Seu resumo financeiro do mês atual.">
      {error && <div className="dashboard-error" role="alert">{error}</div>}
      {isLoading ? <div className="dashboard-state">Carregando seus dados...</div> : <>
        <div className="cards-container">
          <SummaryCard className="card-balance" icon={<Wallet size={32} />} label="Saldo Total" value={summary.balance} />
          <SummaryCard className="card-income" icon={<TrendingUp size={32} />} label="Receita" value={summary.totalIncome} />
          <SummaryCard className="card-expense" icon={<TrendingDown size={32} />} label="Despesas" value={summary.totalExpense} />
          <SummaryCard className="card-goal" icon={<Target size={32} />} label="Economia do Mês" value={summary.balance} />
        </div>
        <div className="dashboard-content">
          <section className="section"><h3>Suas Contas</h3><div className="accounts-list">
            {accounts.length ? accounts.map(account => <div key={account.id} className="account-item"><div className="account-info"><p className="account-name">{account.bankName}</p><span className="account-type">Conta</span></div><p className="account-balance">{money(account.balance)}</p></div>) : <p>Nenhuma conta cadastrada.</p>}
          </div></section>
          <section className="section"><h3>Despesas por Categoria</h3><div className="categories-list">
            {categories.length ? categories.map(([name, amount]) => <div key={name} className="category-item"><div className="category-header"><p className="category-name">{name}</p><span className="category-amount">{money(amount)}</span></div><div className="category-bar"><div className="category-fill" style={{ width: `${amount / largestCategory * 100}%` }} /></div></div>) : <p>Nenhuma despesa neste mês.</p>}
          </div></section>
        </div>
        <section className="section"><h3>Transações Recentes</h3><div className="transactions-table">
          <div className="table-header"><div className="col-description">Descrição</div><div className="col-category">Categoria</div><div className="col-date">Data</div><div className="col-amount">Valor</div></div>
          {transactions.length ? transactions.map(transaction => <div key={transaction.id} className="table-row"><div className="col-description"><p>{transaction.cleanDescription}</p></div><div className="col-category"><span className="badge">{transaction.category}</span></div><div className="col-date"><p>{new Date(`${transaction.transactionDate}T00:00:00`).toLocaleDateString('pt-BR')}</p></div><div className={`col-amount ${transaction.type === 'INCOME' ? 'income' : 'expense'}`}><p>{transaction.type === 'INCOME' ? '+' : '-'} {money(transaction.amount)}</p></div></div>) : <p className="dashboard-state">Nenhuma transação importada.</p>}
        </div></section>
      </>}
  </MobileLayout>
}

function SummaryCard({ className, icon, label, value }: { className: string; icon: ReactNode; label: string; value: number }) {
  return <div className={`card ${className}`}><div className="card-icon">{icon}</div><div className="card-content"><p className="card-label">{label}</p><h2 className="card-value">{money(value)}</h2></div></div>
}
export default Dashboard
