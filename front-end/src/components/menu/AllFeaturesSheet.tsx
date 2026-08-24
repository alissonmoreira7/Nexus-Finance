import { useEffect, useRef } from 'react'
import type { RefObject } from 'react'
import { ReceiptText, Settings, Target, TrendingUp, Wallet, X } from 'lucide-react'

interface Props {
  onClose: () => void
  triggerRef: RefObject<HTMLButtonElement | null>
}

const futureFeatures = [
  { label: 'Orçamentos', description: 'Planeje limites mensais', icon: Wallet },
  { label: 'Metas', description: 'Acompanhe seus objetivos', icon: Target },
  { label: 'Categorias', description: 'Personalize seus lançamentos', icon: ReceiptText },
  { label: 'Investimentos', description: 'Acompanhe sua carteira', icon: TrendingUp },
  { label: 'Configurações', description: 'Ajuste sua experiência', icon: Settings },
]

export default function AllFeaturesSheet({ onClose, triggerRef }: Props) {
  const panelRef = useRef<HTMLElement>(null)
  const closeRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const previousOverflow = document.body.style.overflow
    const trigger = triggerRef.current
    document.body.style.overflow = 'hidden'
    closeRef.current?.focus()

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault()
        onClose()
        return
      }
      if (event.key !== 'Tab' || !panelRef.current) return

      const controls = Array.from(panelRef.current.querySelectorAll<HTMLElement>('button:not([disabled]), [href], [tabindex]:not([tabindex="-1"])'))
      if (!controls.length) return
      const first = controls[0]
      const last = controls[controls.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault(); last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault(); first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.body.style.overflow = previousOverflow
      document.removeEventListener('keydown', handleKeyDown)
      trigger?.focus()
    }
  }, [onClose, triggerRef])

  return <div className="all-features-backdrop" onMouseDown={event => { if (event.target === event.currentTarget) onClose() }}>
    <section ref={panelRef} id="all-features-sheet" className="all-features-sheet" role="dialog" aria-modal="true" aria-labelledby="all-features-title">
      <span className="sheet-handle" aria-hidden="true" />
      <header className="sheet-header">
        <div><span>Explorar</span><h2 id="all-features-title">Todos os recursos</h2></div>
        <button ref={closeRef} type="button" className="sheet-close" onClick={onClose} aria-label="Fechar todos os recursos"><X size={22} /></button>
      </header>
      <div className="future-features" aria-label="Recursos futuros">
        {futureFeatures.map(({ label, description, icon: Icon }) => <div key={label} className="future-feature" aria-disabled="true">
          <span className="future-feature-icon"><Icon size={20} aria-hidden="true" /></span>
          <span className="future-feature-copy"><strong>{label}</strong><small>{description}</small></span>
          <span className="coming-soon">Em breve</span>
        </div>)}
      </div>
    </section>
  </div>
}
