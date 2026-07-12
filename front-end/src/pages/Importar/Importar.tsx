import { useMemo, useRef, useState } from 'react'
import { FileSpreadsheet, UploadCloud, CheckCircle2, X } from 'lucide-react'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAccounts } from '../../hooks/useAccounts'
import { api } from '../../services/api'
import '../app-pages.css'

interface Row { date: string; rawDescription: string; amount: number }

function splitCsvLine(line: string, separator: string) {
  const cells: string[] = []; let value = ''; let quoted = false
  for (let i = 0; i < line.length; i++) {
    const char = line[i]
    if (char === '"' && line[i + 1] === '"' && quoted) { value += '"'; i++ }
    else if (char === '"') quoted = !quoted
    else if (char === separator && !quoted) { cells.push(value.trim()); value = '' }
    else value += char
  }
  cells.push(value.trim()); return cells
}

function parseCsv(text: string): Row[] {
  const lines = text.replace(/^\uFEFF/, '').split(/\r?\n/).filter(line => line.trim())
  if (lines.length < 2) throw new Error('O CSV precisa de cabeçalho e pelo menos uma transação.')
  const separator = (lines[0].match(/;/g)?.length ?? 0) > (lines[0].match(/,/g)?.length ?? 0) ? ';' : ','
  const normalize = (value: string) => value.toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '').replace(/[^a-z]/g, '')
  const headers = splitCsvLine(lines[0], separator).map(normalize)
  const indexOf = (...names: string[]) => headers.findIndex(header => names.includes(header))
  const dateIndex = indexOf('date', 'data', 'transactiondate')
  const descriptionIndex = indexOf('rawdescription', 'descricao', 'description')
  const amountIndex = indexOf('amount', 'valor', 'value')
  if ([dateIndex, descriptionIndex, amountIndex].includes(-1)) throw new Error('Use as colunas: data, descricao e valor.')
  return lines.slice(1).map((line, index) => {
    const cells = splitCsvLine(line, separator)
    const rawAmount = cells[amountIndex]?.replace(/R\$\s?/i, '').trim() ?? ''
    const normalizedAmount = rawAmount.includes(',') ? rawAmount.replace(/\./g, '').replace(',', '.') : rawAmount
    const amount = Math.abs(Number(normalizedAmount))
    const rawDate = cells[dateIndex] ?? ''
    const date = /^\d{2}\/\d{2}\/\d{4}$/.test(rawDate) ? rawDate.split('/').reverse().join('-') : rawDate
    const rawDescription = cells[descriptionIndex]?.trim()
    if (!/^\d{4}-\d{2}-\d{2}$/.test(date) || !rawDescription || !Number.isFinite(amount)) throw new Error(`Linha ${index + 2} contém data, descrição ou valor inválido.`)
    return { date, rawDescription, amount }
  })
}

export default function Importar() {
  const inputRef = useRef<HTMLInputElement>(null)
  const { accounts, isLoading: loadingAccounts, error: accountError } = useAccounts()
  const [accountId, setAccountId] = useState('')
  const [rows, setRows] = useState<Row[]>([])
  const [fileName, setFileName] = useState('')
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const [sending, setSending] = useState(false)
  const selectedAccount = accountId || accounts[0]?.id || ''
  const total = useMemo(() => rows.reduce((sum, row) => sum + row.amount, 0), [rows])

  const chooseFile = async (file?: File) => {
    setError(''); setSuccess('')
    if (!file) return
    if (file.size > 2 * 1024 * 1024) { setError('O arquivo deve ter no máximo 2 MB.'); return }
    if (!file.name.toLowerCase().endsWith('.csv')) { setError('Selecione um arquivo no formato CSV.'); return }
    try {
      const parsed = parseCsv(await file.text())
      if (parsed.length > 10_000) throw new Error('O arquivo excede o limite de 10.000 linhas.')
      setRows(parsed); setFileName(file.name)
    } catch (e) { setRows([]); setError(e instanceof Error ? e.message : 'CSV inválido') }
  }
  const upload = async () => {
    if (!selectedAccount || !rows.length) return
    setSending(true); setError('')
    try {
      const result = await api<{ inserted: number }>(`/transactions/upload?accountId=${selectedAccount}`, { method: 'POST', body: JSON.stringify(rows) })
      setSuccess(`${result.inserted} transações importadas e categorizadas.`); setRows([]); setFileName('')
    } catch (e) { setError(e instanceof Error ? e.message : 'Não foi possível importar o arquivo') }
    finally { setSending(false) }
  }

  return <MobileLayout eyebrow="Entrada de dados" title="Importar extrato" subtitle="Envie um CSV e deixe o Nexus limpar e categorizar suas transações.">
    {(error || accountError) && <div className="state-box error" role="alert">{error || accountError}</div>}
    {success && <div className="success-banner" role="status"><CheckCircle2 size={20} />{success}</div>}
    <section className="surface import-card">
      <label className="field-label" htmlFor="account">Conta de destino</label>
      <select id="account" className="field-select" value={selectedAccount} onChange={e => setAccountId(e.target.value)} disabled={loadingAccounts || !accounts.length}>
        {accounts.map(account => <option key={account.id} value={account.id}>{account.bankName}</option>)}
      </select>
      <input ref={inputRef} className="sr-only" type="file" accept=".csv,text/csv" onChange={e => void chooseFile(e.target.files?.[0])} />
      <button className="drop-zone" type="button" onClick={() => inputRef.current?.click()}>
        <UploadCloud size={34} /><strong>{fileName || 'Escolher arquivo CSV'}</strong><span>Até 2 MB · máximo de 10.000 linhas</span>
      </button>
      <div className="csv-help"><FileSpreadsheet size={18} /><div><strong>Formato esperado</strong><code>data;descricao;valor</code><small>Ex.: 2026-07-12;UBER TRIP;24,90</small></div></div>
    </section>
    {!!rows.length && <section className="surface preview-card">
      <div className="section-title"><div><span>Pré-visualização</span><strong>{rows.length} registros · {total.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</strong></div><button aria-label="Remover arquivo" onClick={() => { setRows([]); setFileName('') }}><X size={18} /></button></div>
      <div className="preview-list">{rows.slice(0, 4).map((row, index) => <div key={`${row.date}-${index}`}><span>{row.rawDescription}</span><strong>{row.amount.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })}</strong><small>{new Date(`${row.date}T00:00:00`).toLocaleDateString('pt-BR')}</small></div>)}</div>
      <button className="primary-action" disabled={sending || !selectedAccount} onClick={() => void upload()}>{sending ? 'Processando...' : `Importar ${rows.length} transações`}</button>
    </section>}
  </MobileLayout>
}
