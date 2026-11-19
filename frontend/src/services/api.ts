
import axios from 'axios'

export const api = axios.create({
  baseURL: '/api/v1',
  headers: {
    'Content-Type': 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('payflow_token')

  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }

  return config
})

export interface AuthResponse {
  userId: string
  email: string
  role: string
  tokenType: string
  accessToken: string
}

export interface Payment {
  id: string
  amount: number
  currency: string
  status: string
  createdAt: string
  idempotencyKey: string
}

export interface Refund {
  id: string
  paymentId: string
  amount: number
  status: string
  idempotencyKey: string
  createdAt: string
}

export interface PaymentPage {
  content: Payment[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export const authApi = {
  login: async (email: string, password: string) => {
    const response = await api.post<AuthResponse>(
      '/auth/login',
      { email, password },
    )
    return response.data
  },

  register: async (email: string, password: string) => {
    const response = await api.post<AuthResponse>(
      '/auth/register',
      { email, password },
    )
    return response.data
  },
}

export const paymentApi = {
  getAll: async (page = 0, size = 10) => {
    const response = await api.get<PaymentPage>('/payments', {
      params: { page, size },
    })
    return response.data
  },

  getById: async (id: string) => {
    const response = await api.get<Payment>(`/payments/${id}`)
    return response.data
  },

  create: async (amount: number, currency: string) => {
    const response = await api.post<Payment>(
      '/payments',
      { amount, currency },
      {
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
      },
    )
    return response.data
  },

  getHistory: async (id: string) => {
    const response = await api.get(`/payments/${id}/history`)
    return response.data
  },
}

export const refundApi = {
  getAll: async (paymentId: string) => {
    const response = await api.get<Refund[]>(
      `/payments/${paymentId}/refunds`,
    )
    return response.data
  },

  create: async (paymentId: string, amount: number) => {
    const response = await api.post<Refund>(
      `/payments/${paymentId}/refunds`,
      { amount },
      {
        headers: {
          'Idempotency-Key': crypto.randomUUID(),
        },
      },
    )
    return response.data
  },
}
