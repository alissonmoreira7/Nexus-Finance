import { useEffect, useState } from 'react'
import { ArrowLeft, ArrowUpDown, CalendarDays, CircleDollarSign, Landmark, Text } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import AnimatedSuccess from '../../components/feedback/AnimatedSuccess'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAccounts } from '../../hooks/useAccounts'
import { api } from '../../services/api'
import '../app-pages.css'

interface CreatedTransaction {
  id: string
  cleanDescription: string
  amount: number
  type: 'INCOME' | 'EXPENSE'
  category: string
  transactionDate: string
  accountName: string
}

interface TransactionResponse extends Omit<CreatedTransaction, 'accountName'> {
  source: 'MANUAL' | 'CSV'
}

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
  const [type, setType] = useState<'EXPENSE' | 'INCOME'>('EXPENSE')
  const [date, setDate] = useState(today())
  const [description, setDescription] = useState('')
  const [amount, setAmount] = useState('')
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [createdTransaction, setCreatedTransaction] = useState<CreatedTransaction | null>(null)
  const selectedAccountId = accountId || accounts[0]?.id || ''
  const selectedAccount = accounts.find(account => account.id === selectedAccountId)

  useEffect(() => {
    if (!createdTransaction) return

    const redirectTimer = window.setTimeout(() => {
      navigate(`/historico/${createdTransaction.id}`, {
        replace: true,
        state: { transaction: createdTransaction },
      })
    }, 1700)

    return () => window.clearTimeout(redirectTimer)
  }, [createdTransaction, navigate])

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')

    const rawAmount = amount.replace(/R\$\s?/i, '').trim()
    const normalized = rawAmount.includes(',') ? rawAmount.replace(/\./g, '').replace(',', '.') : rawAmount
    const numericAmount = Number(normalized)

    if (!selectedAccountId) {
      setError('Selecione uma conta para continuar.')
      return
    }
    if (!description.trim()) {
      setError('Informe uma descrição para a transação.')
      return
    }
    if (!Number.isFinite(numericAmount) || numericAmount <= 0) {
      setError('Informe um valor maior que zero.')
      return
    }

    setSaving(true)
    try {
      const transaction = await api<TransactionResponse>(`/transactions/manual?accountId=${selectedAccountId}`, {
        method: 'POST',
        body: JSON.stringify({ date, rawDescription: description.trim(), amount: numericAmount, type }),
      })

      setCreatedTransaction({
        id: transaction.id,
        cleanDescription: transaction.cleanDescription,
        amount: transaction.amount,
        type: transaction.type,
        category: transaction.category,
        transactionDate: transaction.transactionDate,
        accountName: selectedAccount?.bankName || 'Conta selecionada',
      })
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Não foi possível salvar a transação.')
      setSaving(false)
    }
  }

  return <MobileLayout>
    <div className="page-back-heading">
      <button
        type="button"
        className="page-back-button"
        onClick={() => navigate('/dashboard')}
        aria-label="Voltar para o início"
        title="Voltar para o início"
        disabled={saving}
      >
        <ArrowLeft size={23} aria-hidden="true" />
      </button>
      <div><h1>Adicionar transação</h1></div>
    </div>

    {(error || accountError) && <div className="state-box error" role="alert">{error || accountError}</div>}

    <form className="surface manual-form" onSubmit={submit} aria-busy={saving}>
      <div className="manual-field manual-account">
        <label htmlFor="manual-account"><Landmark size={17} />Conta</label>
        <select
          id="manual-account"
          value={selectedAccountId}
          onChange={event => setAccountId(event.target.value)}
          disabled={loadingAccounts || !accounts.length || saving}
          required
        >
          {accounts.map(account => <option key={account.id} value={account.id}>{account.bankName}</option>)}
        </select>
      </div>

      <div className="manual-field manual-type">
        <label htmlFor="manual-type"><ArrowUpDown size={17} />Tipo de movimentação</label>
        <select id="manual-type" value={type} onChange={event => setType(event.target.value as 'EXPENSE' | 'INCOME')} disabled={saving} required>
          <option value="EXPENSE">Saída</option>
          <option value="INCOME">Entrada</option>
        </select>
      </div>

      <div className="manual-field manual-date">
        <label htmlFor="manual-date"><CalendarDays size={17} />Data</label>
        <input id="manual-date" type="date" value={date} max="2099-12-31" onChange={event => setDate(event.target.value)} disabled={saving} required />
      </div>

      <div className="manual-field manual-description">
        <label htmlFor="manual-description"><Text size={17} />Descrição</label>
        <input id="manual-description" value={description} maxLength={255} onChange={event => setDescription(event.target.value)} placeholder="Ex.: UBER TRIP ou SALARIO EMPRESA" disabled={saving} required />
      </div>

      <div className="manual-field manual-amount">
        <label htmlFor="manual-amount"><CircleDollarSign size={17} />Valor</label>
        <div className="money-input">
          <span>R$</span>
          <input id="manual-amount" inputMode="decimal" value={amount} onChange={event => setAmount(event.target.value)} placeholder="0,00" disabled={saving} required />
        </div>
      </div>

      <p className="categorization-note">Entradas somam ao saldo e saídas subtraem. A descrição será usada para escolher a categoria automaticamente.</p>
      <button className="primary-action" disabled={saving || !accounts.length}>{saving ? 'Salvando...' : 'Salvar transação'}</button>
      <button className="secondary-action" type="button" disabled={saving} onClick={() => navigate('/historico')}>Ver histórico</button>
    </form>

    {createdTransaction && <AnimatedSuccess
      fullscreen
      title="Transação adicionada!"
      message="Abrindo os detalhes..."
      ariaLabel="Transação adicionada com sucesso"
    />}
  </MobileLayout>
}
