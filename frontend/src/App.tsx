import './App.css'
import { useCallback, useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import {
  Activity,
  ArrowDownLeft,
  ArrowUpRight,
  Bell,
  CheckCircle2,
  ChevronDown,
  ChevronRight,
  Clock3,
  CreditCard,
  Download,
  LayoutDashboard,
  LogOut,
  Menu,
  MoreHorizontal,
  Plus,
  RefreshCw,
  Search,
  ShieldCheck,
  Wallet,
  X,
  AlertCircle,
  XCircle,
} from 'lucide-react'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'

import { paymentApi } from './services/api'
import { useAuth } from './context/AuthContext'

type Payment = {
  id: string
  amount: number
  currency: string
  status: string
  createdAt: string
  idempotencyKey?: string
}


const chartData = [
  { day: 'Mon', income: 4200, expenses: 1800 },
  { day: 'Tue', income: 5800, expenses: 2400 },
  { day: 'Wed', income: 4900, expenses: 2100 },
  { day: 'Thu', income: 7200, expenses: 3200 },
  { day: 'Fri', income: 6100, expenses: 2800 },
  { day: 'Sat', income: 8500, expenses: 3600 },
  { day: 'Sun', income: 7600, expenses: 3100 },
]

const formatCurrency = (amount: number, currency = 'INR') =>
  new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)

const formatDate = (date: string) => {
  if (!date) return '—'

  return new Date(date).toLocaleDateString('en-IN', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  })
}

const getStatusClass = (status: string) => {
  switch (status?.toUpperCase()) {
    case 'SUCCESS':
    case 'COMPLETED':
    case 'SUCCEEDED':
      return 'success'
    case 'PENDING':
    case 'PROCESSING':
      return 'pending'
    case 'FAILED':
    case 'CANCELLED':
      return 'failed'
    default:
      return 'pending'
  }
}

function StatCard({
  title,
  value,
  change,
  icon,
  iconClass,
  description,
}: {
  title: string
  value: string
  change?: string
  icon: ReactNode
  iconClass: string
  description: string
}) {
  return (
    <div className="stat-card">
      <div className="stat-card-top">
        <span className="stat-title">{title}</span>
        <div className={`stat-icon ${iconClass}`}>{icon}</div>
      </div>

      <div className="stat-value">{value}</div>

      <div className="stat-footer">
        {change && (
          <span className="stat-change">
            <ArrowUpRight size={14} />
            {change}
          </span>
        )}
        <span className="stat-description">{description}</span>
      </div>
    </div>
  )
}

function StatusBadge({ status }: { status: string }) {
  const normalized = status?.toUpperCase() || 'UNKNOWN'
  const statusClass = getStatusClass(normalized)

  const StatusIcon =
    statusClass === 'success'
      ? CheckCircle2
      : statusClass === 'failed'
        ? XCircle
        : Clock3

  return (
    <span className={`status-badge ${statusClass}`}>
      <StatusIcon size={13} />
      {normalized}
    </span>
  )
}

function TransactionTable({
  rows,
  search,
  onSearch,
  filter,
  onFilter,
}: {
  rows: Payment[]
  search: string
  onSearch: (value: string) => void
  filter: string
  onFilter: (value: string) => void
}) {
  return (
    <div className="transactions-card">
      <div className="section-header">
        <div>
          <h3>Recent transactions</h3>
          <p>Monitor and manage your latest payments.</p>
        </div>

        <button className="text-button" type="button">
          View all <ArrowUpRight size={15} />
        </button>
      </div>

      <div className="table-toolbar">
        <div className="search-box">
          <Search size={16} />
          <input
            type="text"
            placeholder="Search transactions..."
            value={search}
            onChange={(event) => onSearch(event.target.value)}
          />
        </div>

        <select
          className="filter-select"
          value={filter}
          onChange={(event) => onFilter(event.target.value)}
          aria-label="Filter transactions by status"
        >
          <option value="ALL">All statuses</option>
          <option value="SUCCESS">Successful</option>
          <option value="PENDING">Pending</option>
          <option value="FAILED">Failed</option>
        </select>
      </div>

      <div className="table-wrapper">
        <table className="transaction-table">
          <thead>
            <tr>
              <th>Transaction ID</th>
              <th>Date</th>
              <th>Amount</th>
              <th>Status</th>
              <th>Action</th>
            </tr>
          </thead>

          <tbody>
            {rows.length > 0 ? (
              rows.map((payment) => (
                <tr key={payment.id}>
                  <td>
                    <div className="transaction-id">
                      <div className="transaction-avatar">
                        <CreditCard size={16} />
                      </div>
                      <span>
                        {payment.id.length > 16
                          ? `${payment.id.slice(0, 8)}...${payment.id.slice(-5)}`
                          : payment.id}
                      </span>
                    </div>
                  </td>

                  <td>{formatDate(payment.createdAt)}</td>

                  <td className="amount-cell">
                    {formatCurrency(payment.amount, payment.currency)}
                  </td>

                  <td>
                    <StatusBadge status={payment.status} />
                  </td>

                  <td>
                    <button
                      className="icon-button"
                      type="button"
                      title="Transaction options"
                      aria-label="Transaction options"
                    >
                      <MoreHorizontal size={18} />
                    </button>
                  </td>
                </tr>
              ))
            ) : (
              <tr>
                <td colSpan={5} className="empty-state">
                  No transactions found.
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}

function CreatePaymentModal({
  onClose,
  onCreate,
  loading,
  error,
}: {
  onClose: () => void
  onCreate: (amount: number, currency: string) => Promise<void>
  loading: boolean
  error: string
}) {
  const [amount, setAmount] = useState('')
  const [currency, setCurrency] = useState('INR')

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()

    const numericAmount = Number(amount)

    if (!numericAmount || numericAmount <= 0) return

    await onCreate(numericAmount, currency)
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="payment-modal"
        onClick={(event) => event.stopPropagation()}
      >
        <div className="modal-header">
          <div>
            <h2>Create payment</h2>
            <p>Enter the payment details below.</p>
          </div>

          <button
            className="icon-button"
            type="button"
            onClick={onClose}
            aria-label="Close modal"
          >
            <X size={19} />
          </button>
        </div>

        <form onSubmit={handleSubmit}>
          <label htmlFor="payment-amount">Payment amount</label>
          <input
            id="payment-amount"
            type="number"
            min="0.01"
            step="0.01"
            placeholder="Enter amount"
            value={amount}
            onChange={(event) => setAmount(event.target.value)}
            required
          />

          <label htmlFor="payment-currency">Currency</label>
          <select
            id="payment-currency"
            value={currency}
            onChange={(event) => setCurrency(event.target.value)}
          >
            <option value="INR">INR — Indian Rupee</option>
            <option value="USD">USD — US Dollar</option>
            <option value="EUR">EUR — Euro</option>
          </select>

          {error && (
            <div className="form-error">
              <AlertCircle size={15} />
              {error}
            </div>
          )}

          <div className="modal-actions">
            <button
              className="secondary-button"
              type="button"
              onClick={onClose}
              disabled={loading}
            >
              Cancel
            </button>

            <button
              className="primary-button"
              type="submit"
              disabled={loading || !amount || Number(amount) <= 0}
            >
              {loading ? (
                <>
                  <RefreshCw size={16} className="spin" />
                  Creating...
                </>
              ) : (
                <>
                  <Plus size={16} />
                  Create payment
                </>
              )}
            </button>
          </div>
        </form>
      </div>
    </div>
  )
}

function App() {
  const { user, logout } = useAuth()

  const [payments, setPayments] = useState<Payment[]>([])
  const [loading, setLoading] = useState(true)
  const [pageError, setPageError] = useState('')
  const [search, setSearch] = useState('')
  const [filter, setFilter] = useState('ALL')
  const [showPaymentModal, setShowPaymentModal] = useState(false)
  const [creatingPayment, setCreatingPayment] = useState(false)
  const [paymentError, setPaymentError] = useState('')
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false)

  const loadPayments = useCallback(async () => {
    setLoading(true)
    setPageError('')

    try {
      const data = await paymentApi.getAll()

      setPayments(Array.isArray(data) ? data : data.content || [])
    } catch (error: unknown) {
      const message =
        error instanceof Error
          ? error.message
          : 'Unable to load payments. Please try again.'

      setPageError(message)
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void loadPayments()
  }, [loadPayments])

  const handleCreatePayment = async (
    amount: number,
    currency: string,
  ) => {
    setCreatingPayment(true)
    setPaymentError('')

    try {
      await paymentApi.create(amount, currency)
      setShowPaymentModal(false)
      await loadPayments()
    } catch (error: unknown) {
      const message =
        error instanceof Error
          ? error.message
          : 'Payment could not be created. Please try again.'

      setPaymentError(message)
    } finally {
      setCreatingPayment(false)
    }
  }

  const filteredPayments = useMemo(() => {
    return payments.filter((payment) => {
      const matchesSearch =
        payment.id.toLowerCase().includes(search.toLowerCase()) ||
        payment.status.toLowerCase().includes(search.toLowerCase())

      const matchesFilter =
        filter === 'ALL' || payment.status.toUpperCase() === filter

      return matchesSearch && matchesFilter
    })
  }, [payments, search, filter])

  const totalVolume = payments
    .filter((payment) => getStatusClass(payment.status) === 'success')
    .reduce((total, payment) => total + payment.amount, 0)

  const successfulCount = payments.filter(
    (payment) => getStatusClass(payment.status) === 'success',
  ).length

  const pendingCount = payments.filter(
    (payment) => getStatusClass(payment.status) === 'pending',
  ).length

  const failedCount = payments.filter(
    (payment) => getStatusClass(payment.status) === 'failed',
  ).length

  return (
    <div className="dashboard-layout">
      {mobileMenuOpen && (
        <div
          className="mobile-overlay"
          onClick={() => setMobileMenuOpen(false)}
        />
      )}

      <aside className={`sidebar ${mobileMenuOpen ? 'sidebar-open' : ''}`}>
        <div className="brand">
          <div className="brand-icon">
            <Wallet size={21} />
          </div>
          <span>payflow<span className="brand-dot">.</span></span>
        </div>

        <div className="sidebar-section">
          <span className="sidebar-label">WORKSPACE</span>

          <nav className="sidebar-nav">
            <a href="#dashboard" className="nav-item active">
              <LayoutDashboard size={18} />
              Dashboard
            </a>

            <a href="#transactions" className="nav-item">
              <ArrowDownLeft size={18} />
              Transactions
            </a>

            <a href="#analytics" className="nav-item">
              <Activity size={18} />
              Analytics
            </a>

            <a href="#payments" className="nav-item">
              <CreditCard size={18} />
              Payments
            </a>
          </nav>
        </div>

        <div className="sidebar-section">
          <span className="sidebar-label">MANAGEMENT</span>

          <nav className="sidebar-nav">
            <a href="#security" className="nav-item">
              <ShieldCheck size={18} />
              Security
            </a>
          </nav>
        </div>

        <div className="sidebar-bottom">
          <div className="sidebar-help">
            <div className="help-icon">
              <ShieldCheck size={18} />
            </div>
            <div>
              <strong>Secure payments</strong>
              <span>Your workspace is protected.</span>
            </div>
          </div>

          <div className="profile">
            <div className="profile-avatar">
              {(user?.email || 'A').charAt(0).toUpperCase()}
            </div>

            <div className="profile-info">
              <strong>{user?.email || 'Account'}</strong>
              <span>PayFlow account</span>
            </div>

            <button
              className="icon-button"
              type="button"
              onClick={logout}
              title="Sign out"
              aria-label="Sign out"
            >
              <LogOut size={17} />
            </button>
          </div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="topbar-left">
            <button
              className="mobile-menu-button"
              type="button"
              onClick={() => setMobileMenuOpen(!mobileMenuOpen)}
              aria-label="Toggle menu"
            >
              <Menu size={20} />
            </button>

            <div className="breadcrumb">
              <span>Workspace</span>
              <ChevronRight size={14} />
              <strong>Dashboard</strong>
            </div>
          </div>

          <div className="topbar-actions">
            <span className="environment-badge">
              <span className="environment-dot" />
              Live API
            </span>

            <button
              className="icon-button notification-button"
              type="button"
              aria-label="Notifications"
            >
              <Bell size={18} />
              <span className="notification-dot" />
            </button>
          </div>
        </header>

        <div className="page-content" id="dashboard">
          <div className="page-heading">
            <div>
              <span className="eyebrow">OVERVIEW</span>
              <h1>Dashboard</h1>
              <p>Welcome back. Here's what's happening with your payments.</p>
            </div>

            <div className="heading-actions">
              <button
                className="secondary-button"
                type="button"
                onClick={() => void loadPayments()}
                disabled={loading}
              >
                <RefreshCw size={16} className={loading ? 'spin' : ''} />
                Refresh
              </button>

              <button
                className="primary-button"
                type="button"
                onClick={() => {
                  setPaymentError('')
                  setShowPaymentModal(true)
                }}
              >
                <Plus size={17} />
                Create payment
              </button>
            </div>
          </div>

          {pageError && (
            <div className="page-error">
              <AlertCircle size={17} />
              <span>{pageError}</span>
              <button type="button" onClick={() => void loadPayments()}>
                Retry
              </button>
            </div>
          )}

          <div className="stats-grid">
            <StatCard
              title="Successful payment volume"
              value={formatCurrency(totalVolume)}
              icon={<Wallet size={19} />}
              iconClass="purple"
              description="Based on loaded transactions"
            />

            <StatCard
              title="Successful payments"
              value={successfulCount.toLocaleString('en-IN')}
              icon={<CheckCircle2 size={19} />}
              iconClass="green"
              description="Completed transactions"
            />

            <StatCard
              title="Pending payments"
              value={pendingCount.toLocaleString('en-IN')}
              icon={<Clock3 size={19} />}
              iconClass="orange"
              description="Awaiting final status"
            />

            <StatCard
              title="Failed payments"
              value={failedCount.toLocaleString('en-IN')}
              icon={<XCircle size={19} />}
              iconClass="red"
              description="Unsuccessful transactions"
            />
          </div>

          <div className="dashboard-grid">
            <section className="chart-card" id="analytics">
              <div className="section-header">
                <div>
                  <h3>Payment activity</h3>
                  <p>Illustrative weekly activity overview.</p>
                </div>

                <button className="period-select" type="button">
                  This week <ChevronDown size={15} />
                </button>
              </div>

              <div className="chart-legend">
                <span><i className="legend-dot income-dot" /> Income</span>
                <span><i className="legend-dot expense-dot" /> Expenses</span>
              </div>

              <div className="chart-container">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart data={chartData}>
                    <defs>
                      <linearGradient id="incomeGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#8b5cf6" stopOpacity={0.3} />
                        <stop offset="95%" stopColor="#8b5cf6" stopOpacity={0} />
                      </linearGradient>
                      <linearGradient id="expenseGradient" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="0%" stopColor="#38bdf8" stopOpacity={0.2} />
                        <stop offset="95%" stopColor="#38bdf8" stopOpacity={0} />
                      </linearGradient>
                    </defs>

                    <CartesianGrid stroke="#202a3b" strokeDasharray="4 4" vertical={false} />
                    <XAxis
                      dataKey="day"
                      stroke="#78849a"
                      tickLine={false}
                      axisLine={false}
                    />
                    <YAxis
                      stroke="#78849a"
                      tickLine={false}
                      axisLine={false}
                      tickFormatter={(value) => `${value / 1000}k`}
                    />
                    <Tooltip
                      contentStyle={{
                        background: '#111a2b',
                        border: '1px solid #26334a',
                        borderRadius: '10px',
                        color: '#e5e7eb',
                      }}
                    />
                    <Area
                      type="monotone"
                      dataKey="income"
                      stroke="#a78bfa"
                      strokeWidth={2}
                      fill="url(#incomeGradient)"
                    />
                    <Area
                      type="monotone"
                      dataKey="expenses"
                      stroke="#38bdf8"
                      strokeWidth={2}
                      fill="url(#expenseGradient)"
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </section>

            <section className="quick-actions-card">
              <div className="section-header">
                <div>
                  <h3>Quick actions</h3>
                  <p>Manage your payment workflow.</p>
                </div>
              </div>

              <button
                className="quick-action"
                type="button"
                onClick={() => {
                  setPaymentError('')
                  setShowPaymentModal(true)
                }}
              >
                <span className="quick-action-icon purple">
                  <Plus size={19} />
                </span>
                <span>
                  <strong>Create payment</strong>
                  <small>Start a new payment</small>
                </span>
                <ChevronRight size={17} />
              </button>

              <button
                className="quick-action"
                type="button"
                onClick={() => void loadPayments()}
              >
                <span className="quick-action-icon blue">
                  <RefreshCw size={18} />
                </span>
                <span>
                  <strong>Refresh transactions</strong>
                  <small>Get the latest payment data</small>
                </span>
                <ChevronRight size={17} />
              </button>

              <button
                className="quick-action"
                type="button"
                onClick={() => {
                  const csv = [
                    ['Payment ID', 'Date', 'Amount', 'Currency', 'Status'],
                    ...payments.map((payment) => [
                      payment.id,
                      payment.createdAt,
                      String(payment.amount),
                      payment.currency,
                      payment.status,
                    ]),
                  ]
                    .map((row) =>
                      row
                        .map((cell) => `"${String(cell).replaceAll('"', '""')}"`)
                        .join(','),
                    )
                    .join('\n')

                  const blob = new Blob([csv], {
                    type: 'text/csv;charset=utf-8;',
                  })
                  const url = URL.createObjectURL(blob)
                  const link = document.createElement('a')
                  link.href = url
                  link.download = 'payflow-transactions.csv'
                  link.click()
                  URL.revokeObjectURL(url)
                }}
              >
                <span className="quick-action-icon green">
                  <Download size={18} />
                </span>
                <span>
                  <strong>Export transactions</strong>
                  <small>Download your payment data</small>
                </span>
                <ChevronRight size={17} />
              </button>
            </section>
          </div>

          <section id="transactions">
            {loading ? (
              <div className="loading-card">
                <RefreshCw size={20} className="spin" />
                <span>Loading transactions...</span>
              </div>
            ) : (
              <TransactionTable
                rows={filteredPayments}
                search={search}
                onSearch={setSearch}
                filter={filter}
                onFilter={setFilter}
              />
            )}
          </section>

          <footer className="dashboard-footer">
            <span>© 2026 PayFlow. All rights reserved.</span>
            <span>
              <span className="footer-status-dot" />
              Connected to backend
            </span>
          </footer>
        </div>
      </main>

      {showPaymentModal && (
        <CreatePaymentModal
          onClose={() => setShowPaymentModal(false)}
          onCreate={handleCreatePayment}
          loading={creatingPayment}
          error={paymentError}
        />
      )}
    </div>
  )
}

export default App
