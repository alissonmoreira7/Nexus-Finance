import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { GoogleOAuthProvider } from '@react-oauth/google'
import { AuthProvider } from './contexts/AuthContext'
import { PrivateRoute } from './components/PrivateRoute'
import './index.css'
import Login from './pages/Login/Login.tsx'
import Cadastrar from './pages/Cadastrar/Cadastrar.tsx'
import Dashboard from './pages/Dashboard/Dashboard.tsx'
import Importar from './pages/Importar/Importar.tsx'
import Historico from './pages/Historico/Historico.tsx'
import DetalheTransacao from './pages/Historico/DetalheTransacao.tsx'
import Perfil from './pages/Perfil/Perfil.tsx'
import Adicionar from './pages/Adicionar/Adicionar.tsx'
import Relatorios from './pages/Relatorios/Relatorios.tsx'
import GastosPrevistos from './pages/GastosPrevistos/GastosPrevistos.tsx'
import RendaMes from './pages/GastosPrevistos/RendaMes.tsx'
import GastoPrevistoForm from './pages/GastosPrevistos/GastoPrevistoForm.tsx'


createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <GoogleOAuthProvider clientId="596082796704-2ldapav0lmujqi2ee816ntu2qh0s03f6.apps.googleusercontent.com">
        <AuthProvider>
          <Routes>
            <Route path="/" element={<Login />} />
            <Route path="/cadastrar" element={<Cadastrar />} />
            <Route path="/dashboard" element={<PrivateRoute><Dashboard /></PrivateRoute>} />
            <Route path="/importar" element={<PrivateRoute><Importar /></PrivateRoute>} />
            <Route path="/historico" element={<PrivateRoute><Historico /></PrivateRoute>} />
            <Route path="/historico/:transactionId" element={<PrivateRoute><DetalheTransacao /></PrivateRoute>} />
            <Route path="/perfil" element={<PrivateRoute><Perfil /></PrivateRoute>} />
            <Route path="/adicionar" element={<PrivateRoute><Adicionar /></PrivateRoute>} />
            <Route path="/relatorios" element={<PrivateRoute><Relatorios /></PrivateRoute>} />
            <Route path="/gastos-previstos" element={<PrivateRoute><GastosPrevistos /></PrivateRoute>} />
            <Route path="/gastos-previstos/renda" element={<PrivateRoute><RendaMes /></PrivateRoute>} />
            <Route path="/gastos-previstos/novo" element={<PrivateRoute><GastoPrevistoForm /></PrivateRoute>} />
            <Route path="/gastos-previstos/:expenseId/editar" element={<PrivateRoute><GastoPrevistoForm /></PrivateRoute>} />
          </Routes>
        </AuthProvider>
      </GoogleOAuthProvider>
    </BrowserRouter>
  </StrictMode>,
)
