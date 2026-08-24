import { useRef, useState } from 'react'
import { NavLink } from 'react-router-dom'
import { Home, Menu as MenuIcon, ReceiptText, Upload } from 'lucide-react'
import AllFeaturesSheet from './AllFeaturesSheet'
import './ResponsiveMenu.css'

const regularItems = [
  { to: '/dashboard', label: 'Início', icon: Home },
  { to: '/importar', label: 'Importar', icon: Upload },
  { to: '/historico', label: 'Histórico', icon: ReceiptText },
]

function MenuItem({ to, label, icon: Icon }: typeof regularItems[number]) {
  return <NavLink to={to} className={({ isActive }) => `bottom-nav-item${isActive ? ' active' : ''}`}>
    <Icon size={21} strokeWidth={2.2} aria-hidden="true" /><span>{label}</span>
  </NavLink>
}

function ResponsiveMenu() {
  const [isAllOpen, setIsAllOpen] = useState(false)
  const allButtonRef = useRef<HTMLButtonElement>(null)

  return <>
    <nav className="bottom-nav" aria-label="Navegação principal">
      {regularItems.map(item => <MenuItem key={item.to} {...item} />)}
      <button ref={allButtonRef} type="button" className={`bottom-nav-item bottom-nav-button${isAllOpen ? ' active' : ''}`}
        onClick={() => setIsAllOpen(true)} aria-haspopup="dialog" aria-expanded={isAllOpen} aria-controls="all-features-sheet">
        <MenuIcon size={21} strokeWidth={2.2} aria-hidden="true" /><span>Todos</span>
      </button>
    </nav>
    {isAllOpen && <AllFeaturesSheet onClose={() => setIsAllOpen(false)} triggerRef={allButtonRef} />}
  </>
}
export default ResponsiveMenu
