import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import ResponsiveMenu from '../menu/ResponsiveMenu'
import { useAuth } from '../../contexts/AuthContext'
import './MobileLayout.css'

interface Props {
  title?: string
  subtitle?: string
  headerAction?: ReactNode
  wide?: boolean
  children: ReactNode
}

export default function MobileLayout({ title, subtitle, headerAction, wide = false, children }: Props) {
  const { user } = useAuth()
  const { pathname } = useLocation()
  const initials = user?.name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'NF'
  const showHeader = pathname === '/dashboard'

  return <div className="app-shell">
    {showHeader && (
      <header className="app-header">
        <Link
          to="/perfil"
          className="profile-shortcut"
          aria-label={`Abrir perfil${user?.name ? ` de ${user.name}` : ''}`}
          title="Abrir perfil"
        >
           <span aria-hidden="true">{initials}</span>
        </Link>
        {headerAction}
      </header>
    )}

    <main className={`app-main${wide ? ' app-main-wide' : ''}`}>
      {(title || subtitle) && <div className="page-heading">
        {title && <h1>{title}</h1>}
        {subtitle && <p>{subtitle}</p>}
      </div>}
      {children}
    </main>
    <ResponsiveMenu />
  </div>
}
