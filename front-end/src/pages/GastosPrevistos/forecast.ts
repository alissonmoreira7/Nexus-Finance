export interface PlannedExpense {
  id: string
  description: string
  amount: number
  dueDate: string | null
}

export interface Forecast {
  month: string
  expectedIncome: number
  totalPlanned: number
  projectedBalance: number
  expenses: PlannedExpense[]
}

export const money = (value: number) => value.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' })

export function currentMonth() {
  const now = new Date()
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
}

export function validMonth(value: string | null) {
  return value && /^\d{4}-(0[1-9]|1[0-2])$/.test(value) ? value : currentMonth()
}

export function monthLabel(month: string) {
  const [year, monthNumber] = month.split('-').map(Number)
  const label = new Intl.DateTimeFormat('pt-BR', { month: 'long', year: 'numeric' }).format(new Date(year, monthNumber - 1, 1))
  return label.charAt(0).toUpperCase() + label.slice(1)
}

export function shiftMonth(month: string, amount: number) {
  const [year, monthNumber] = month.split('-').map(Number)
  const shifted = new Date(year, monthNumber - 1 + amount, 1)
  return `${shifted.getFullYear()}-${String(shifted.getMonth() + 1).padStart(2, '0')}`
}

export function decimalValue(value: string) {
  const raw = value.replace(/R\$\s?/i, '').trim()
  const normalized = raw.includes(',') ? raw.replace(/\./g, '').replace(',', '.') : raw
  const number = Number(normalized)
  return Number.isFinite(number) ? number : NaN
}

export function inputMoney(value: number) {
  return value ? value.toFixed(2).replace('.', ',') : ''
}
