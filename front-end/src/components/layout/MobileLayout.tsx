import type { ReactNode } from 'react'
import ResponsiveMenu from '../menu/ResponsiveMenu'
import './MobileLayout.css'

interface Props { eyebrow?: string; title: string; subtitle?: string; action?: ReactNode; children: ReactNode }

export default function MobileLayout({ eyebrow, title, subtitle, action, children }: Props) {
  return <div className="app-shell">
    <header className="app-header">
      <div><span className="app-brand">NEXUS <em>FINANCE</em></span></div>
      {action}
    </header>
    <main className="app-main">
      <div className="page-heading">
        {eyebrow && <span className="page-eyebrow">{eyebrow}</span>}
        <h1>{title}</h1>
        {subtitle && <p>{subtitle}</p>}
      </div>
      {children}
    </main>
    <ResponsiveMenu />
  </div>
}
