import { useEffect, useState } from 'react'
import { ArrowDownLeft, ArrowLeft, ArrowUpRight, CalendarDays, Landmark, Shapes } from 'lucide-react'
import { useLocation, useNavigate, useParams } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { api } from '../../services/api'
import '../app-pages.css'

interface TransactionDetail {
  id: string
  cleanDescription: string
  amount: number
  type: 'INCOME' | 'EXPENSE'
  category: string
  transactionDate: string
  accountName: string
}

interface DetailLocationState {
  transaction?: TransactionDetail
}

const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })
const date = (value: string) => new Date(`${value}T00:00:00`).toLocaleDateString('pt-BR')

export default function DetalheTransacao() {
  const navigate = useNavigate()
  const location = useLocation()
  const { transactionId } = useParams()
  const routedTransaction = (location.state as DetailLocationState | null)?.transaction ?? null
  const [transaction, setTransaction] = useState<TransactionDetail | null>(routedTransaction)
  const [loading, setLoading] = useState(!routedTransaction)
  const [error, setError] = useState('')

  useEffect(() => {
    if (transaction) return
    let cancelled = false

    const loadTransaction = async () => {
      await Promise.resolve()
      if (!transactionId || cancelled) {
        if (!cancelled) {
          setError('Transação não encontrada.')
          setLoading(false)
        }
        return
      }

      try {
        const response = await api<TransactionDetail>(`/transactions/${transactionId}`)
        if (!cancelled) setTransaction(response)
      } catch (requestError) {
        if (!cancelled) setError(requestError instanceof Error ? requestError.message : 'Não foi possível carregar a transação.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    void loadTransaction()
    return () => { cancelled = true }
  }, [transactionId, transaction])

  return <MobileLayout>
    <div className="page-back-heading">
      <button
        type="button"
        className="page-back-button"
        onClick={() => navigate('/historico')}
        aria-label="Voltar para o histórico"
        title="Voltar para o histórico"
      >
        <ArrowLeft size={23} aria-hidden="true" />
      </button>
      <div><h1>Detalhes da transação</h1></div>
    </div>

    {loading && <div className="state-box">Carregando transação...</div>}
    {error && <div className="state-box error" role="alert">{error}</div>}

    {transaction && <article className="transaction-detail-page">
      <div className={`transaction-detail-hero ${transaction.type === 'INCOME' ? 'income' : 'expense'}`}>
        <span className="transaction-detail-icon">
          {transaction.type === 'INCOME' ? <ArrowDownLeft size={25} /> : <ArrowUpRight size={25} />}
        </span>
        <span>{transaction.type === 'INCOME' ? 'Entrada' : 'Saída'}</span>
        <h2>{transaction.cleanDescription}</h2>
        <strong>{transaction.type === 'INCOME' ? '+' : '-'} {money(transaction.amount)}</strong>
      </div>

      <dl className="transaction-detail-list">
        <div>
          <dt><Shapes size={19} aria-hidden="true" /> Categoria</dt>
          <dd>{transaction.category}</dd>
        </div>
        <div>
          <dt><CalendarDays size={19} aria-hidden="true" /> Data</dt>
          <dd>{date(transaction.transactionDate)}</dd>
        </div>
        <div>
          <dt><Landmark size={19} aria-hidden="true" /> Conta</dt>
          <dd>{transaction.accountName}</dd>
        </div>
        <div>
          <dt>Identificador</dt>
          <dd>{transaction.id}</dd>
        </div>
      </dl>
    </article>}
  </MobileLayout>
}
