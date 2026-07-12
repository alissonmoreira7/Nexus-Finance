import { useEffect, useState } from 'react'
import { ArrowDownLeft, ArrowUpRight, ChevronLeft, ChevronRight } from 'lucide-react'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAccounts } from '../../hooks/useAccounts'
import { api } from '../../services/api'
import '../app-pages.css'

type SourceFilter = 'ALL' | 'MANUAL' | 'CSV'
interface Transaction { id: string; cleanDescription: string; amount: number; type: 'INCOME' | 'EXPENSE'; source: 'MANUAL' | 'CSV'; category: string; transactionDate: string }
interface Page { content: Transaction[]; page: number; totalPages: number; totalElements: number }
const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

export default function Historico() {
  const { accounts, isLoading: loadingAccounts, error: accountError } = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [page, setPage] = useState(0)
  const [result, setResult] = useState<Page>({ content: [], page: 0, totalPages: 0, totalElements: 0 })
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [source, setSource] = useState<SourceFilter>('ALL')
  const selected = accountId || accounts[0]?.id || ''

  useEffect(() => {
    if (!selected) return
    setLoading(true); setError('')
    const sourceQuery = source === 'ALL' ? '' : `&source=${source}`
    api<Page>(`/transactions/account/${selected}?page=${page}&size=15${sourceQuery}`).then(setResult)
      .catch(e => setError(e instanceof Error ? e.message : 'Não foi possível carregar o histórico'))
      .finally(() => setLoading(false))
  }, [selected, page, source])

  return <MobileLayout eyebrow="Movimentações" title="Histórico" subtitle={`${result.totalElements} transações registradas`}>
    <section className="surface filter-card">
      <label className="field-label" htmlFor="history-account">Conta</label>
      <select id="history-account" className="field-select" value={selected} disabled={loadingAccounts || !accounts.length}
        onChange={e => { setAccountId(e.target.value); setPage(0) }}>
        {accounts.map(account => <option key={account.id} value={account.id}>{account.bankName}</option>)}
      </select>
    </section>
    <div className="history-tabs" role="group" aria-label="Origem das transações">
      {([['ALL', 'Todas'], ['MANUAL', 'Manuais'], ['CSV', 'Importadas']] as const).map(([value, label]) =>
        <button key={value} className={source === value ? 'active' : ''} onClick={() => { setSource(value); setPage(0) }}>{label}</button>)}
    </div>
    {(error || accountError) && <div className="state-box error" role="alert">{error || accountError}</div>}
    {loading ? <div className="state-box">Carregando transações...</div> : result.content.length ? <>
      <div className="transaction-feed">{result.content.map(item => <article key={item.id} className="transaction-item">
        <div className={`transaction-icon ${item.type === 'INCOME' ? 'income' : 'expense'}`}>{item.type === 'INCOME' ? <ArrowDownLeft size={20} /> : <ArrowUpRight size={20} />}</div>
        <div className="transaction-copy"><strong>{item.cleanDescription}</strong><span>{item.category} · {new Date(`${item.transactionDate}T00:00:00`).toLocaleDateString('pt-BR')}</span><small className={`source-badge ${item.source.toLowerCase()}`}>{item.source === 'MANUAL' ? 'Manual' : 'CSV'}</small></div>
        <strong className={item.type === 'INCOME' ? 'amount-income' : 'amount-expense'}>{item.type === 'INCOME' ? '+' : '-'} {money(item.amount)}</strong>
      </article>)}</div>
      <div className="pagination"><button className="secondary-action" disabled={page === 0} onClick={() => setPage(value => value - 1)}><ChevronLeft size={18} /> Anterior</button><span>{page + 1} de {result.totalPages}</span><button className="secondary-action" disabled={page + 1 >= result.totalPages} onClick={() => setPage(value => value + 1)}>Próxima <ChevronRight size={18} /></button></div>
    </> : <div className="state-box">Nenhuma transação encontrada neste filtro.</div>}
  </MobileLayout>
}
