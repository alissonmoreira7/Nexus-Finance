import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { api } from '../../services/api'
import { decimalValue, type Forecast, inputMoney, monthLabel, validMonth } from './forecast'
import './style.css'

export default function GastoPrevistoForm() {
  const { expenseId } = useParams()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const month = validMonth(searchParams.get('month'))
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [dueDate, setDueDate] = useState('')
  const [loading, setLoading] = useState(Boolean(expenseId))
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')

  useEffect(() => {
    if (!expenseId) return
    let active = true
    const load = async () => {
      try {
        const forecast = await api<Forecast>(`/forecasts/${month}`)
        const expense = forecast.expenses.find(item => item.id === expenseId)
        if (!expense) throw new Error('Gasto previsto não encontrado')
        if (!active) return
        setDescription(expense.description); setAmount(inputMoney(expense.amount)); setDueDate(expense.dueDate ?? '')
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : 'Não foi possível carregar o gasto previsto')
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false }
  }, [expenseId, month])

  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setError('')
    const value = decimalValue(amount)
    if (!description.trim()) { setError('Informe o nome do gasto.'); return }
    if (!Number.isFinite(value) || value <= 0) { setError('Informe um valor previsto maior que zero.'); return }
    setSaving(true)
    try {
      await api<Forecast>(expenseId ? `/forecasts/${month}/expenses/${expenseId}` : `/forecasts/${month}/expenses`, {
        method: expenseId ? 'PUT' : 'POST',
        body: JSON.stringify({ description: description.trim(), amount: value.toFixed(2), dueDate: dueDate || null }),
      })
      navigate(`/gastos-previstos?month=${month}`, { replace: true })
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível salvar o gasto previsto')
    } finally { setSaving(false) }
  }

  const [year, monthNumber] = month.split('-').map(Number)
  const minDate = `${month}-01`
  const maxDate = `${month}-${new Date(year, monthNumber, 0).getDate()}`

  return <MobileLayout>
    <div className="forecast-heading"><Link to={`/gastos-previstos?month=${month}`} className="forecast-back" aria-label="Voltar para gastos previstos"><ArrowLeft size={23} aria-hidden="true" /></Link><h1>{expenseId ? 'Editar gasto' : 'Adicionar gasto'}</h1></div>
    {error && <div className="forecast-message forecast-error" role="alert">{error}</div>}
    <form className="forecast-panel standalone-forecast-form" onSubmit={submit}>
      <span className="forecast-month-label">{monthLabel(month)}</span>
      <label htmlFor="planned-expense-description">Nome do gasto</label>
      <input id="planned-expense-description" value={description} maxLength={120} onChange={event => setDescription(event.target.value)} placeholder="Ex.: Aluguel" disabled={loading} required autoFocus={!expenseId} />
      <label htmlFor="planned-expense-amount">Valor previsto</label>
      <div className="forecast-money-input"><span>R$</span><input id="planned-expense-amount" inputMode="decimal" value={amount} onChange={event => setAmount(event.target.value)} placeholder="0,00" disabled={loading} required /></div>
      <label htmlFor="planned-expense-due-date">Vencimento opcional</label>
      <input id="planned-expense-due-date" type="date" min={minDate} max={maxDate} value={dueDate} onChange={event => setDueDate(event.target.value)} disabled={loading} />
      <button className="primary-action" disabled={loading || saving}>{saving ? 'Salvando...' : expenseId ? 'Salvar alterações' : 'Adicionar gasto'}</button>
    </form>
  </MobileLayout>
}
