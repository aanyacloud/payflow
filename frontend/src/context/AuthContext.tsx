
import {
  createContext,
  useContext,
  useState,
  type ReactNode,
} from 'react'

import {
  authApi,
  type AuthResponse,
} from '../services/api'

interface AuthContextType {
  user: AuthResponse | null
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextType | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthResponse | null>(() => {
    const token = localStorage.getItem('payflow_token')
    const savedUser = localStorage.getItem('payflow_user')

    if (!token || !savedUser) return null

    try {
      return JSON.parse(savedUser) as AuthResponse
    } catch {
      localStorage.removeItem('payflow_token')
      localStorage.removeItem('payflow_user')
      return null
    }
  })

  const saveSession = (data: AuthResponse) => {
    localStorage.setItem('payflow_token', data.accessToken)
    localStorage.setItem('payflow_user', JSON.stringify(data))
    setUser(data)
  }

  const login = async (email: string, password: string) => {
    const data = await authApi.login(email, password)
    saveSession(data)
  }

  const register = async (email: string, password: string) => {
    const data = await authApi.register(email, password)
    saveSession(data)
  }

  const logout = () => {
    localStorage.removeItem('payflow_token')
    localStorage.removeItem('payflow_user')
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, login, register, logout }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const context = useContext(AuthContext)

  if (!context) {
    throw new Error('useAuth must be used inside AuthProvider')
  }

  return context
}
