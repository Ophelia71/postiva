export type AccountStatus = 'ACTIVE' | 'LOCKED'

export type AdminUser = {
  id: string
  fullName: string
  email: string
  role: 'USER'
  status: AccountStatus
  createdAt: string
  updatedAt: string
  lockedAt?: string | null
  lockedBy?: string | null
  lockReason?: string | null
}

export type AdminAudit = {
  id: string
  actorUserId: string
  actorName?: string | null
  targetUserId: string
  action: 'LOCK_USER' | 'UNLOCK_USER'
  previousStatus: AccountStatus
  newStatus: AccountStatus
  reason: string
  createdAt: string
}

export type AdminPage<T> = {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export type AiBudgetWindow = {
  window: 'DAILY' | 'MONTHLY'
  startsOn: string
  budgetUsd: number
  spentUsd: number
  actualProviderUsd: number
  reservedUsd: number
  totalUsd: number
  percentUsed: number
  warningSent: boolean
  actualReconciledAt?: string | null
}

export type AiBudgetStatus = {
  enabled: boolean
  newRequestsAllowed: boolean
  databaseEnabled: boolean
  environmentEnabled: boolean
  daily: AiBudgetWindow
  monthly: AiBudgetWindow
  unknownCharges: number
  settledRequests: number
  costBasis: string
  providerReconciliationEnabled: boolean
  configuredModel: string
  inputUsdPerMillionTokens: number
  cachedInputUsdPerMillionTokens: number
  cacheWriteInputUsdPerMillionTokens: number
  outputUsdPerMillionTokens: number
}

export function adminDate(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  return Number.isNaN(date.getTime()) ? '—' : new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'medium', timeStyle: 'short',
  }).format(date)
}

export function adminPageNumber(value: string | null) {
  const page = Number(value)
  return Number.isSafeInteger(page) && page >= 0 && page <= 1_000_000 ? page : 0
}
