import { useEffect, useState } from 'react'
import { ArrowLeft, CalendarDays, ChevronLeft, ChevronRight, Pencil, PlusCircle, Trash2, WalletCards } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { api } from '../../services/api'
import { currentMonth, type Forecast, money, monthLabel, shiftMonth, validMonth, type PlannedExpense } from './forecast'
import './style.css'

export default function GastosPrevistos() {
  const [searchParams, setSearchParams] = useSearchParams()
  const month = validMonth(searchParams.get('month'))
  const [forecast, setForecast] = useState<Forecast | null>(null)
  const [loading, setLoading] = useState(true)
  const [deletingId, setDeletingId] = useState<string | null>(null)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')

  useEffect(() => {
    let active = true
    const loadSelectedMonth = async () => {
      setLoading(true); setError('')
      try {
        const result = await api<Forecast>(`/forecasts/${month}`)
        if (active) setForecast(result)
      } catch (exception) {
        if (!active) return
        setForecast(null)
        setError(exception instanceof Error ? exception.message : 'Não foi possível carregar o planejamento')
      } finally { if (active) setLoading(false) }
    }
    void loadSelectedMonth()
    return () => { active = false }
  }, [month])

  const changeMonth = (value: string) => {
    if (value) { setStatus(''); setSearchParams({ month: value }) }
  }

  const deleteExpense = async (expense: PlannedExpense) => {
    
    if (!window.confirm(`Excluir ${expense.description} de ${monthLabel(month)}?`)) return
    
    setDeletingId(expense.id); setError(''); setStatus('')
    try {
      await api(`/forecasts/${month}/expenses/${expense.id}`, { method: 'DELETE' })
      const result = await api<Forecast>(`/forecasts/${month}`)
      setForecast(result)
      setStatus('Gasto previsto excluído.')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível excluir o gasto previsto')
    } finally { setDeletingId(null) }
  }

  return <MobileLayout wide>
    <div className="forecast-heading">
      <Link to="/dashboard" className="forecast-back" aria-label="Voltar para o início" title="Voltar para o início"><ArrowLeft size={23} aria-hidden="true" /></Link>
      <h1>Gastos previstos</h1>
    </div>

    <section className="month-navigation" aria-label="Mês do planejamento">
      <button type="button" onClick={() => changeMonth(shiftMonth(month, -1))} aria-label="Mês anterior"><ChevronLeft size={22} aria-hidden="true" /></button>
      <label>
        <span className="sr-only">Mês do planejamento</span><input type="month" value={month} onChange={event => changeMonth(event.target.value)} />
      </label>
      <button type="button" onClick={() => changeMonth(shiftMonth(month, 1))} aria-label="Próximo mês">
        <ChevronRight size={22} aria-hidden="true" />
      </button>
    </section>

    {month !== currentMonth() && <button type="button" className="current-month-action" onClick={() => changeMonth(currentMonth())}>Voltar ao mês atual</button>}
    {error && <div className="forecast-message forecast-error" role="alert">{error}</div>}
    {status && <div className="forecast-message forecast-success" role="status">{status}</div>}

    {loading ? <div className="forecast-message">Carregando planejamento...</div> : forecast && <>
      <div className="forecast-summary" aria-live="polite">
        <article className={`forecast-summary-card projected${forecast.projectedBalance < 0 ? ' negative' : ''}`}>
          <span>Saldo após previstos</span> 
          <strong>{money(forecast.projectedBalance)}</strong> 
          <small>Renda menos gastos previstos</small>
        </article>
        <article className="forecast-summary-card">
          <span>Renda prevista</span>
          <strong>{forecast.expectedIncome ? money(forecast.expectedIncome) : 'Não informada'}</strong>
        </article>
        <article className="forecast-summary-card">
          <span>Total de gastos previstos</span>
          <strong>{money(forecast.totalPlanned)}</strong>
        </article>
      </div>

      <nav className="forecast-shortcuts" aria-label={`Ações do planejamento de ${monthLabel(month)}`}>
        <Link to={`/gastos-previstos/renda?month=${month}`} className="forecast-shortcut" aria-label={`Informar renda prevista de ${monthLabel(month)}`}>
          <span className="forecast-shortcut-icon"><WalletCards size={24} aria-hidden="true" /></span><strong>Renda do Mês</strong>
        </Link>
        <Link to={`/gastos-previstos/novo?month=${month}`} className="forecast-shortcut" aria-label={`Adicionar gasto previsto de ${monthLabel(month)}`}>
          <span className="forecast-shortcut-icon"><PlusCircle size={24} aria-hidden="true" /></span><strong className="stacked-label"><span>Adicionar</span><span>Gasto</span></strong>
        </Link>
      </nav>

      <section className="forecast-panel expenses-panel">
        <div className="expenses-heading">
          <div>
            <h2>Gastos do mês</h2>
            <span>
              {forecast.expenses.length} {forecast.expenses.length === 1 ? 'item' : 'itens'}
            </span>
          </div>
          <CalendarDays size={24} aria-hidden="true" />
        </div>
        {forecast.expenses.length ? <div className="planned-expenses-list">
          {forecast.expenses.map(expense => <article key={expense.id} className="planned-expense-item">
          <div className="planned-expense-copy">
            <strong>{expense.description}</strong>
            <span>{expense.dueDate ? `Vence em ${new Date(`${expense.dueDate}T00:00:00`).toLocaleDateString('pt-BR')}` : 'Sem vencimento definido'}</span>
          </div>
            <strong className="planned-expense-amount">{money(expense.amount)}</strong>
            <div className="planned-expense-actions">
              <Link to={`/gastos-previstos/${expense.id}/editar?month=${month}`} aria-label={`Editar ${expense.description}`} title={`Editar ${expense.description}`}><Pencil size={20} aria-hidden="true" /></Link>
             
              <button type="button" className="delete" onClick={() => void deleteExpense(expense)} disabled={deletingId !== null} aria-label={`Excluir ${expense.description}`} title={`Excluir ${expense.description}`}>
                <Trash2 size={20} aria-hidden="true" />
              </button>
            </div>
            </article>)}</div> : 
          <div className="forecast-empty"><PlusCircle size={28} aria-hidden="true" />
            <strong>Nenhum gasto previsto neste mês</strong>
            <span>  Adicione aluguel, cartão ou mensalidades pelo atalho acima.  </span>
          </div>}
      </section>

      <div className='confirm-delete'>
            

      </div>
    </>}
  </MobileLayout>
}
