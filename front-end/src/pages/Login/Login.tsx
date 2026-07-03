import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useGoogleLogin } from '@react-oauth/google'
import './style.css'
import nexus_logo from '../../assets/nexus_logo.png'
import EmailInput from '../../components/input/EmailInput'
import PasswordInput from '../../components/input/SenhaInput'

function Login() {
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')

  const loginComGoogle = useGoogleLogin({
    onSuccess: (response) => {
      console.log('Token do Google:', response.access_token)
    },
    onError: () => {
      console.log('Falha na autenticação com o Google')
    }
  })

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    console.log('Enviando login:', { email, senha })
  }

  return (
    <div className='geral-login'>
      <div className='container-login'> 
        <div className="container-logo">
          <img src={nexus_logo} alt="Nexus Finance logo" /> 
          <h1>Nexus<em>Finance</em></h1>
          <p className="label-caps">OPEN FINANCE</p>
        </div>  

        <div className='sub-titulo'>
          <h1>Bem-vindo</h1>
          <p>Entre na sua conta para acessar seus insights financeiros</p>
        </div>

        <form onSubmit={handleSubmit} className="form-login">
          <EmailInput value={email} onChange={setEmail} />
          <PasswordInput value={senha} onChange={setSenha} />

          <button type="submit" className="btn-entrar">
            Entrar
          </button>

          <div className="divisor">
            <span>ou continue com</span>
          </div>

          <button
            type="button"
            className="btn-google-custom"
            onClick={() => loginComGoogle()}
          >
            <svg version="1.1" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48" className="logo-google">
              <g>
                <path fill="#EA4335" d="M24 9.5c3.54 0 6.71 1.22 9.21 3.6l6.85-6.85C35.9 2.38 30.47 0 24 0 14.62 0 6.51 5.38 2.56 13.22l7.98 6.19C12.43 13.72 17.74 9.5 24 9.5z"/>
                <path fill="#4285F4" d="M46.98 24.55c0-1.57-.15-3.09-.38-4.55H24v9.02h12.94c-.58 2.96-2.26 5.48-4.78 7.18l7.73 6c4.51-4.18 7.09-10.36 7.09-17.65z"/>
                <path fill="#FBBC05" d="M10.53 28.59c-.48-1.45-.76-2.99-.76-4.59s.27-3.14.76-4.59l-7.98-6.19C.92 16.46 0 20.12 0 24c0 3.88.92 7.54 2.56 10.78l7.97-6.19z"/>
                <path fill="#34A853" d="M24 48c6.48 0 11.93-2.13 15.89-5.81l-7.73-6c-2.15 1.45-4.92 2.3-8.16 2.3-6.26 0-11.57-4.22-13.47-9.91l-7.98 6.19C6.51 42.62 14.62 48 24 48z"/>
              </g>
            </svg>
            <span>Google</span>
          </button>
        </form>

        <div className='criar-conta'>
          <p>Não tem uma conta?</p>
          <Link to="/cadastrar">Cadastre-se grátis</Link>
        </div>
      </div>
    </div>
  )
}

export default Login