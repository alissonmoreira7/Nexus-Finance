import { useEffect, useState } from 'react'
import { ArrowLeft } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { api } from '../../services/api'
import { decimalValue, type Forecast, inputMoney, monthLabel, validMonth } from './forecast'
import './style.css'

export default function RendaMes() {
  const [searchParams] = useSearchParams()
  const month = validMonth(searchParams.get('month'))
  const [income, setIncome] = useState('')
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [status, setStatus] = useState('')

  useEffect(() => {
    let active = true
    const load = async () => {
      try {
        const forecast = await api<Forecast>(`/forecasts/${month}`)
        if (active) setIncome(inputMoney(forecast.expectedIncome))
      } catch (exception) {
        if (active) setError(exception instanceof Error ? exception.message : 'Não foi possível carregar a renda prevista')
      } finally { if (active) setLoading(false) }
    }
    void load()
    return () => { active = false }
  }, [month])

  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setError(''); setStatus('')
    const value = decimalValue(income)
    if (!Number.isFinite(value) || value < 0) { setError('Informe uma renda prevista válida.'); return }
    setSaving(true)
    try {
      const forecast = await api<Forecast>(`/forecasts/${month}`, { method: 'PUT', body: JSON.stringify({ expectedIncome: value.toFixed(2) }) })
      setIncome(inputMoney(forecast.expectedIncome)); setStatus('Renda do mês atualizada.')
    } catch (exception) {
      setError(exception instanceof Error ? exception.message : 'Não foi possível salvar a renda prevista')
    } finally { setSaving(false) }
  }

  return <MobileLayout>
    <div className="forecast-heading"><Link to={`/gastos-previstos?month=${month}`} className="forecast-back" aria-label="Voltar para gastos previstos"><ArrowLeft size={23} aria-hidden="true" /></Link><h1>Renda do Mês</h1></div>
    {error && <div className="forecast-message forecast-error" role="alert">{error}</div>}
    {status && <div className="forecast-message forecast-success" role="status">{status}</div>}
    <form className="forecast-panel standalone-forecast-form" onSubmit={submit}>
      <span className="forecast-month-label">{monthLabel(month)}</span>
      <label htmlFor="forecast-income">Renda prevista</label>
      <div className="forecast-money-input"><span>R$</span><input id="forecast-income" inputMode="decimal" value={income} onChange={event => setIncome(event.target.value)} placeholder="0,00" disabled={loading} required /></div>
      <button className="primary-action" disabled={loading || saving}>{saving ? 'Salvando...' : 'Salvar renda'}</button>
    </form>
  </MobileLayout>
}
