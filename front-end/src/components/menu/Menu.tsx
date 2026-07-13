import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Menu as MenuIcon, X, LogOut, Settings, User } from 'lucide-react'
import './Menu.css'
import { useAuth } from '../../contexts/AuthContext'

function Menu() {
  const [isOpen, setIsOpen] = useState(false)
  const { logout } = useAuth()
  const navigate = useNavigate()

  const toggleMenu = () => {
    setIsOpen(!isOpen)
  }

  const handleLogout = () => {
    logout()
    navigate('/')
  }

  return (
    <nav className="menu-container">
      <div className="menu-header">
        <Link to="/dashboard" className="menu-logo">
          <span>Nexus<em>Finance</em></span>
        </Link>

        <button
          className="menu-toggle"
          onClick={toggleMenu}
          aria-label="Toggle menu"
        >
          {isOpen ? <X size={24} /> : <MenuIcon size={24} />}
        </button>
      </div>

      <ul className={`menu-list ${isOpen ? 'open' : ''}`}>
        <li>
          <Link to="/dashboard" className="menu-item">
            Dashboard
          </Link>
        </li>
        <li>
          <Link to="/transacoes" className="menu-item">
            Transações
          </Link>
        </li>
        <li>
          <Link to="/categorias" className="menu-item">
            Categorias
          </Link>
        </li>
        <li>
          <Link to="/relatorios" className="menu-item">
            Relatórios
          </Link>
        </li>
      </ul>

      <div className="menu-actions">
        <Link to="/perfil" className="menu-icon-btn" title="Perfil">
          <User size={20} />
        </Link>
        <Link to="/configuracoes" className="menu-icon-btn" title="Configurações">
          <Settings size={20} />
        </Link>
        <button
          className="menu-icon-btn logout"
          onClick={handleLogout}
          title="Sair"
        >
            <LogOut size={20} />
        </button>
      </div>
    </nav>
  )
}

export default Menu
