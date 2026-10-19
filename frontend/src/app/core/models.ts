
export type Role = 'CUSTOMER' | 'MANAGER';
export type AccountType = 'PERSONAL' | 'MERCHANT';
export type PaymentType = 'P2P' | 'MERCHANT';
export type TransferDirection = 'INFLOW' | 'OUTFLOW';
export type MockBankMode = 'NORMAL' | 'OFFLINE' | 'REJECT_NEXT' | 'ACK_LOST';

/** Types of the JSON the backend sends and receives. */
export interface ProblemDetail {
  status?: number;
  title?: string;
  detail?: string;
  code?: string;
  errors?: string[];
}

export interface RegisterRequest {
  displayName: string;
  identifier: string;
  accountType: AccountType;
}

export interface LoginResponse {
  accessToken: string;
  expiresAt: string;
  userId: string;
  displayName: string;
  role: Role;
  accountType: AccountType;
}

export interface UserProfile {
  id: string;
  displayName: string;
  email: string | null;
  phone: string | null;
  role: Role;
  accountType: string;
}

export interface UserLookup {
  userId: string;
  displayName: string;
  accountType: AccountType;
}

export interface Balance {
  walletId: string;
  accountType: string;
  currency: string;
  availableBalance: number;
  pendingIncoming: number;
  pendingOutgoing: number;
}

export interface TransactionItem {
  transactionId: string;
  type: string;
  paymentType: PaymentType | null;
  direction: 'IN' | 'OUT';
  amount: number;
  currency: string;
  counterpartyName: string | null;
  description: string | null;
  createdAt: string;
}

export interface PayRequest {
  recipientId: string;
  amount: string;
  currency: string;
  transactionType: PaymentType;
  description?: string;
}

export interface PaymentResponse {
  transactionId: string;
  status: string;
  timestamp: string;
  amount: number;
  currency: string;
  availableBalance: number;
  replayed: boolean;
}

export interface CreateTransferRequest {
  sourceWalletId: string;
  bankRoutingNumber: string;
  externalAccountNumber: string;
  transferDirection: TransferDirection;
  amount: string;
  currency: string;
}

export interface Transfer {
  trackingId: string;
  transferDirection: TransferDirection;
  amount: number;
  currency: string;
  status: string;
  estimatedArrivalDate: string;
  maskedAccountNumber: string;
  attemptCount: number;
  lastError: string | null;
  createdAt: string;
}

export interface AdminMetrics {
  windowHours: number;
  totalVolume: number;
  paymentCount: number;
  activeUsers: number;
  rejectedPayments: number;
  systemErrorCount: number;
  averageProcessingMs: number;
  pendingTransfers: number;
  walletCount: number;
  outgoingFrozen: boolean;
}

export interface ErrorEvent {
  id: number;
  occurredAt: string;
  service: string;
  httpStatus: number;
  code: string;
  message: string | null;
  path: string | null;
}

export interface SystemStatus {
  outgoingFrozen: boolean;
  frozenReason: string | null;
  frozenAt: string | null;
  unfrozenAt: string | null;
}

export interface Reconciliation {
  id: string;
  status: 'RECONCILED' | 'MISMATCHED';
  walletsTotal: number;
  outgoingPending: number | null;
  ledgerOperating: number;
  ledgerPartner: number;
  bankOperating: number;
  bankPartner: number;
  difference: number;
  discrepancies: string[];
  outgoingFrozen: boolean;
  createdAt: string;
}

export interface SweepResult {
  sweepId: string;
  status: string;
  amount: number;
  reserveLimit: number;
  operatingBalanceAfter: number;
}

export interface BatchResult {
  batchId: string;
  sent: number;
  retrying: number;
  rejected: number;
  skippedFrozen: number;
}

export interface AdminTransaction {
  transactionId: string;
  type: string;
  paymentType: PaymentType | null;
  amount: number;
  payerName: string | null;
  payeeName: string | null;
  description: string | null;
  reversed: boolean;
  createdAt: string;
}

export interface MockBankStatus {
  mode: MockBankMode;
  settlementDelaySeconds: number;
  operatingBalance: number;
  partnerBalance: number;
}
