import { useEffect, useState } from 'react'
import type { ReactNode } from 'react'
import { ArrowLeft, Target, TrendingDown, TrendingUp, Wallet } from 'lucide-react'
import { Link } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAuth } from '../../contexts/AuthContext'
import { api } from '../../services/api'
import './relatorio_style.css'

interface Account { id: string }
interface Summary { totalIncome: number; totalExpense: number; balance: number; expensesByCategory: Record<string, number> }

const emptySummary: Summary = { totalIncome: 0, totalExpense: 0, balance: 0, expensesByCategory: {} }
const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

export default function Relatorios() {
  const { user } = useAuth()
  const [summary, setSummary] = useState<Summary>(emptySummary)
  const [accountCount, setAccountCount] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!user) return
    const load = async () => {
      setIsLoading(true); setError('')
      try {
        const accounts = await api<Account[]>(`/accounts/user/${user.id}`)
        setAccountCount(accounts.length)
        if (!accounts.length) { setSummary(emptySummary); return }
        const summaries = await Promise.all(accounts.map(account => api<Summary>(`/analytics/${account.id}/summary`)))
        setSummary(summaries.reduce((total, current) => ({
          totalIncome: total.totalIncome + current.totalIncome,
          totalExpense: total.totalExpense + current.totalExpense,
          balance: total.balance + current.balance,
          expensesByCategory: total.expensesByCategory,
        }), { ...emptySummary }))
      } catch (exception) {
        setAccountCount(0)
        setSummary(emptySummary)
        setError(exception instanceof Error ? exception.message : 'Não foi possível carregar os relatórios')
      } finally { setIsLoading(false) }
    }
    void load()
  }, [user])

  return <MobileLayout wide>
    <div className="reports-heading">
      <Link to="/dashboard" className="reports-back" aria-label="Voltar para o início" title="Voltar para o início">
        <ArrowLeft size={23} aria-hidden="true" />
      </Link>
      <div><h1>Relatórios</h1></div>
    </div>
    {error ? <div className="reports-state reports-error" role="alert">{error}</div>
      : isLoading ? <div className="reports-state">Carregando indicadores...</div> : accountCount === 0
      ? <div className="reports-state">Cadastre uma conta para visualizar seus indicadores.</div>
      : <div className="reports-cards">
          <SummaryCard className="report-balance" icon={<Wallet />} label="Saldo Total" value={summary.balance} />
          <SummaryCard className="report-income" icon={<TrendingUp />} label="Receita" value={summary.totalIncome} />
          <SummaryCard className="report-expense" icon={<TrendingDown />} label="Despesas" value={summary.totalExpense} />
          <SummaryCard className="report-goal" icon={<Target />} label="Economia do Mês" value={summary.balance} />
        </div>}
  </MobileLayout>
}

function SummaryCard({ className, icon, label, value }: { className: string; icon: ReactNode; label: string; value: number }) {
  return <article className={`report-card ${className}`}>
    <div className="report-card-icon" aria-hidden="true">{icon}</div>
    <div><p>{label}</p><strong>{money(value)}</strong></div>
  </article>
}
