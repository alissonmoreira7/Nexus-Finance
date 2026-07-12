import { NavLink } from 'react-router-dom'
import { Home, Upload, ReceiptText, UserRound, Plus } from 'lucide-react'
import './ResponsiveMenu.css'

const regularItems = [
  { to: '/dashboard', label: 'Início', icon: Home },
  { to: '/importar', label: 'Importar', icon: Upload },
  { to: '/historico', label: 'Histórico', icon: ReceiptText },
  { to: '/perfil', label: 'Perfil', icon: UserRound },
]

function MenuItem({ to, label, icon: Icon }: typeof regularItems[number]) {
  return <NavLink to={to} className={({ isActive }) => `bottom-nav-item${isActive ? ' active' : ''}`}>
    <Icon size={21} strokeWidth={2.2} aria-hidden="true" /><span>{label}</span>
  </NavLink>
}

function ResponsiveMenu() {
  return <nav className="bottom-nav" aria-label="Navegação principal">
    <MenuItem {...regularItems[0]} /><MenuItem {...regularItems[1]} />
    <NavLink to="/adicionar" aria-label="Adicionar transação"
      className={({ isActive }) => `add-transaction-nav${isActive ? ' active' : ''}`}>
      <span className="add-icon"><Plus size={30} strokeWidth={2.5} /></span><span>Adicionar</span>
    </NavLink>
    <MenuItem {...regularItems[2]} /><MenuItem {...regularItems[3]} />
  </nav>
}
export default ResponsiveMenu
