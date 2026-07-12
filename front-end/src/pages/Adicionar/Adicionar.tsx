import { useState } from 'react'
import { CalendarDays, CheckCircle2, CircleDollarSign, Landmark, Text } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAccounts } from '../../hooks/useAccounts'
import { api } from '../../services/api'
import '../app-pages.css'

function today() {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

export default function Adicionar() {
  const navigate = useNavigate()
  const { accounts, isLoading: loadingAccounts, error: accountError } = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [date, setDate] = useState(today())
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState(false)
  const [saving, setSaving] = useState(false)
  const selectedAccount = accountId || accounts[0]?.id || ''

  const submit = async (event: React.FormEvent) => {
    event.preventDefault(); setError(''); setSuccess(false)
    const rawAmount = amount.replace(/R\$\s?/i, '').trim()
    const normalized = rawAmount.includes(',') ? rawAmount.replace(/\./g, '').replace(',', '.') : rawAmount
    const numericAmount = Number(normalized)
    if (!selectedAccount) { setError('Selecione uma conta para continuar.'); return }
    if (!description.trim()) { setError('Informe uma descrição para a transação.'); return }
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) { setError('Informe um valor maior que zero.'); return }
    setSaving(true)
    try {
      await api(`/transactions/manual?accountId=${selectedAccount}`, {
        method: 'POST',
        body: JSON.stringify({ date, rawDescription: description.trim(), amount: numericAmount }),
      })
      setSuccess(true); setDescription(''); setAmount('')
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível salvar a transação.') }
    finally { setSaving(false) }
  }

  return <MobileLayout eyebrow="Novo lançamento" title="Adicionar transação" subtitle="Registre uma movimentação e o Nexus fará a categorização automaticamente.">
    {(error || accountError) && <div className="state-box error" role="alert">{error || accountError}</div>}
    {success && <div className="success-banner" role="status"><CheckCircle2 size={20} />Transação adicionada com sucesso.</div>}
    <form className="surface manual-form" onSubmit={submit}>
      <div className="manual-field"><label htmlFor="manual-account"><Landmark size={17} />Conta</label><select id="manual-account" value={selectedAccount} onChange={e => setAccountId(e.target.value)} disabled={loadingAccounts || !accounts.length} required>{accounts.map(account => <option key={account.id} value={account.id}>{account.bankName}</option>)}</select></div>
      <div className="manual-field"><label htmlFor="manual-date"><CalendarDays size={17} />Data</label><input id="manual-date" type="date" value={date} max="2099-12-31" onChange={e => setDate(e.target.value)} required /></div>
      <div className="manual-field"><label htmlFor="manual-description"><Text size={17} />Descrição</label><input id="manual-description" value={description} maxLength={255} onChange={e => setDescription(e.target.value)} placeholder="Ex.: UBER TRIP ou SALARIO EMPRESA" required /></div>
      <div className="manual-field"><label htmlFor="manual-amount"><CircleDollarSign size={17} />Valor</label><div className="money-input"><span>R$</span><input id="manual-amount" inputMode="decimal" value={amount} onChange={e => setAmount(e.target.value)} placeholder="0,00" required /></div></div>
      <p className="categorization-note">A descrição será comparada ao dicionário de categorias. Receitas como salário são identificadas automaticamente.</p>
      <button className="primary-action" disabled={saving || !accounts.length}>{saving ? 'Salvando...' : 'Salvar transação'}</button>
      <button className="secondary-action" type="button" onClick={() => navigate('/historico')}>Ver histórico</button>
    </form>
  </MobileLayout>
}
