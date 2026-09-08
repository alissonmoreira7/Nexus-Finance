import { useCallback, useEffect, useRef, useState } from 'react'
import { Building2, Camera, LogOut, Mail, Pencil, Save, ShieldCheck, Trash2, UserRound, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import AnimatedSuccess from '../../components/feedback/AnimatedSuccess'
import MobileLayout from '../../components/layout/MobileLayout'
import { useAuth, type User } from '../../contexts/AuthContext'
import { useAccounts } from '../../hooks/useAccounts'
import { api } from '../../services/api'
import '../app-pages.css'
import './style.css'

interface Profile extends User {
  cpf: string
  profileImage: string | null
}

const acceptedImageTypes = ['image/jpeg', 'image/png', 'image/webp']

function formatCpf(value: string) {
  return value.replace(/\D/g, '').slice(0, 11)
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d)/, '$1.$2')
    .replace(/(\d{3})(\d{1,2})$/, '$1-$2')
}

async function prepareProfilePhoto(file: File): Promise<File> {
  if (!acceptedImageTypes.includes(file.type)) throw new Error('Escolha uma foto JPEG, PNG ou WebP.')
  if (file.size > 10_000_000) throw new Error('A imagem original deve ter no máximo 10 MB.')

  const image = await createImageBitmap(file)
  const sourceSize = Math.min(image.width, image.height)
  const sourceX = (image.width - sourceSize) / 2
  const sourceY = (image.height - sourceSize) / 2
  const outputSize = Math.min(640, sourceSize)
  const canvas = document.createElement('canvas')
  canvas.width = outputSize
  canvas.height = outputSize
  const context = canvas.getContext('2d')
  if (!context) throw new Error('Não foi possível preparar a foto.')

  context.drawImage(image, sourceX, sourceY, sourceSize, sourceSize, 0, 0, outputSize, outputSize)
  image.close()

  const blob = await new Promise<Blob>((resolve, reject) => {
    canvas.toBlob(result => result ? resolve(result) : reject(new Error('Não foi possível preparar a foto.')), 'image/webp', .86)
  })
  if (blob.size > 1_000_000) throw new Error('A foto ficou maior que 1 MB. Escolha outra imagem.')
  return new File([blob], 'foto-perfil.webp', { type: 'image/webp' })
}

export default function Perfil() {
  const { user, updateUser, logout } = useAuth()
  const { accounts } = useAccounts()
  const navigate = useNavigate()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [profile, setProfile] = useState<Profile | null>(null)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [cpf, setCpf] = useState('')
  const [previewImage, setPreviewImage] = useState<string | null>(null)
  const [pendingPhoto, setPendingPhoto] = useState<File | null>(null)
  const [removePhoto, setRemovePhoto] = useState(false)
  const [editing, setEditing] = useState(false)
  const [loading, setLoading] = useState(true)
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState('')
  const [showSuccess, setShowSuccess] = useState(false)

  const fillForm = useCallback((data: Profile) => {
    setName(data.name)
    setEmail(data.email)
    setCpf(formatCpf(data.cpf))
    setPreviewImage(data.profileImage)
    setPendingPhoto(null)
    setRemovePhoto(false)
  }, [])

  useEffect(() => {
    let cancelled = false
    const loadProfile = async () => {
      await Promise.resolve()
      try {
        const response = await api<Profile>('/users/me')
        if (!cancelled) {
          setProfile(response)
          fillForm(response)
        }
      } catch (requestError) {
        if (!cancelled) setError(requestError instanceof Error ? requestError.message : 'Não foi possível carregar o perfil.')
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    void loadProfile()
    return () => { cancelled = true }
  }, [fillForm])

  useEffect(() => {
    return () => {
      if (previewImage?.startsWith('blob:')) URL.revokeObjectURL(previewImage)
    }
  }, [previewImage])

  useEffect(() => {
    if (!showSuccess) return
    const timer = window.setTimeout(() => setShowSuccess(false), 1300)
    return () => window.clearTimeout(timer)
  }, [showSuccess])

  const choosePhoto = async (file: File | undefined) => {
    if (!file) return
    setError('')
    try {
      const prepared = await prepareProfilePhoto(file)
      setPendingPhoto(prepared)
      setRemovePhoto(false)
      setPreviewImage(URL.createObjectURL(prepared))
    } catch (photoError) {
      setError(photoError instanceof Error ? photoError.message : 'Não foi possível preparar a foto.')
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = ''
    }
  }

  const removeSelectedPhoto = () => {
    setPendingPhoto(null)
    setPreviewImage(null)
    setRemovePhoto(true)
  }

  const cancelEditing = () => {
    if (profile) fillForm(profile)
    setError('')
    setEditing(false)
  }

  const saveProfile = async (event: React.FormEvent) => {
    event.preventDefault()
    setError('')
    if (!name.trim()) { setError('Informe seu nome.'); return }
    if (cpf.replace(/\D/g, '').length !== 11) { setError('CPF deve conter 11 dígitos.'); return }

    const form = new FormData()
    form.set('name', name.trim())
    form.set('email', email.trim())
    form.set('cpf', cpf.replace(/\D/g, ''))
    form.set('removeImage', String(removePhoto))
    if (pendingPhoto) form.set('photo', pendingPhoto)

    setSaving(true)
    try {
      const updated = await api<Profile>('/users/me', { method: 'PUT', body: form })
      setProfile(updated)
      fillForm(updated)
      updateUser(updated)
      setEditing(false)
      setShowSuccess(true)
    } catch (requestError) {
      setError(requestError instanceof Error ? requestError.message : 'Não foi possível atualizar o perfil.')
    } finally {
      setSaving(false)
    }
  }

  const signOut = () => {
    logout()
    navigate('/')
  }

  const displayName = profile?.name ?? user?.name ?? ''
  const initials = displayName.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase()
  const maskedCpf = profile?.cpf ? `•••.•••.••${profile.cpf.slice(-3)}` : 'Não informado'

  return <MobileLayout title="Perfil" subtitle="Seus dados pessoais e preferências.">
    {error && <div className="state-box error" role="alert">{error}</div>}
    {loading && <div className="state-box">Carregando perfil...</div>}

    {!loading && <>
      <section className="surface profile-hero profile-photo-section">
        <div className="profile-photo-wrap">
          <div className="avatar profile-photo">
            {previewImage ? <img src={previewImage} alt={`Foto de ${displayName}`} /> : (initials || 'NF')}
          </div>
          {editing && <button type="button" className="profile-photo-button" onClick={() => fileInputRef.current?.click()} aria-label="Escolher foto de perfil">
            <Camera size={18} />
          </button>}
          <input
            ref={fileInputRef}
            className="sr-only"
            type="file"
            accept="image/jpeg,image/png,image/webp"
            onChange={event => void choosePhoto(event.target.files?.[0])}
          />
        </div>
        <div className="profile-hero-copy">
          <h2>{displayName}</h2>
          <span>Membro Nexus Finance</span>
          {editing && previewImage && <button type="button" className="profile-remove-photo" onClick={removeSelectedPhoto}>
            <Trash2 size={16} /> Remover foto
          </button>}
        </div>
      </section>

      {editing ? <form className="surface profile-edit-form" onSubmit={saveProfile}>
        <div className="manual-field">
          <label htmlFor="profile-name"><UserRound size={17} />Nome completo</label>
          <input id="profile-name" value={name} maxLength={100} onChange={event => setName(event.target.value)} disabled={saving} required />
        </div>
        <div className="manual-field">
          <label htmlFor="profile-email"><Mail size={17} />E-mail</label>
          <input id="profile-email" type="email" value={email} maxLength={100} onChange={event => setEmail(event.target.value)} disabled={saving} required />
        </div>
        <div className="manual-field">
          <label htmlFor="profile-cpf"><ShieldCheck size={17} />CPF</label>
          <input id="profile-cpf" inputMode="numeric" value={cpf} maxLength={14} onChange={event => setCpf(formatCpf(event.target.value))} disabled={saving} required />
        </div>
        <div className="profile-form-actions">
          <button type="button" className="secondary-action" onClick={cancelEditing} disabled={saving}><X size={18} />Cancelar</button>
          <button type="submit" className="primary-action" disabled={saving}><Save size={18} />{saving ? 'Salvando...' : 'Salvar alterações'}</button>
        </div>
      </form> : <>
        <section className="surface profile-list">
          <div><Mail size={20} /><span><small>E-mail</small><strong>{profile?.email ?? user?.email}</strong></span></div>
          <div><ShieldCheck size={20} /><span><small>CPF protegido</small><strong>{maskedCpf}</strong></span></div>
          <div><Building2 size={20} /><span><small>Contas conectadas</small><strong>{accounts.length}</strong></span></div>
        </section>
        <button type="button" className="profile-edit-action" onClick={() => setEditing(true)}><Pencil size={18} />Editar dados pessoais</button>
      </>}

      {!editing && <button className="logout-action" onClick={signOut}><LogOut size={19} />Sair da conta</button>}
    </>}

    {showSuccess && <AnimatedSuccess fullscreen title="Perfil atualizado!" message="Suas alterações foram salvas." />}
  </MobileLayout>
}
