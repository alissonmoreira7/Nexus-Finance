import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { GoogleOAuthProvider } from '@react-oauth/google'
import './index.css'
import Login from './pages/Login/Login.tsx'
import Cadastrar from './pages/Cadastrar/Cadastrar.tsx'


createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <GoogleOAuthProvider clientId="596082796704-2ldapav0lmujqi2ee816ntu2qh0s03f6.apps.googleusercontent.com">
        <Routes>
          <Route path="/" element={<Login />} />
          <Route path="/cadastrar" element={<Cadastrar />} />
        </Routes>
      </GoogleOAuthProvider>
    </BrowserRouter>
  </StrictMode>,
)