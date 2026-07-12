import { useEffect, useState } from 'react'
import { Building2, LogOut, Mail, ShieldCheck } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAuth } from '../../contexts/AuthContext'
import { useAccounts } from '../../hooks/useAccounts'
import { api } from '../../services/api'
import '../app-pages.css'

interface Profile { idUser: string; name: string; email: string; cpf: string }

export default function Perfil() {
  const { user, logout } = useAuth()
  const { accounts } = useAccounts()
  const navigate = useNavigate()
  const [profile, setProfile] = useState<Profile | null>(null)
  const [error, setError] = useState('')
  useEffect(() => { api<Profile>('/users/me').then(setProfile).catch(e => setError(e instanceof Error ? e.message : 'Não foi possível carregar o perfil')) }, [])
  const signOut = () => { logout(); navigate('/') }
  const name = profile?.name ?? user?.name ?? ''
  const initials = name.split(/\s+/).slice(0, 2).map(part => part[0]).join('').toUpperCase()

  return <MobileLayout eyebrow="Sua conta" title="Perfil" subtitle="Dados pessoais e segurança da sessão.">
    {error && <div className="state-box error" role="alert">{error}</div>}
    <section className="surface profile-hero"><div className="avatar">{initials || 'NF'}</div><div><h2>{name}</h2><span>Membro Nexus Finance</span></div></section>
    <section className="surface profile-list">
      <div><Mail size={20} /><span><small>E-mail</small><strong>{profile?.email ?? user?.email}</strong></span></div>
      <div><ShieldCheck size={20} /><span><small>CPF protegido</small><strong>{profile?.cpf ? `•••.•••.••${profile.cpf.slice(-3)}` : 'Carregando...'}</strong></span></div>
      <div><Building2 size={20} /><span><small>Contas conectadas</small><strong>{accounts.length}</strong></span></div>
    </section>
    <button className="logout-action" onClick={signOut}><LogOut size={19} /> Sair da conta</button>
  </MobileLayout>
}
