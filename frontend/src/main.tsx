
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { AuthProvider, useAuth } from './context/AuthContext'
import AuthPage from './components/AuthPage'
import App from './App'
import './index.css'

function Root() {
  const { user } = useAuth()

  return user ? <App /> : <AuthPage />
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <AuthProvider>
      <Root />
    </AuthProvider>
  </StrictMode>,
)
