// Mirrors org.acas.purchase.payment.web.ApiModels. Money is a decimal number with two places.

export interface PaymentView {
  reference: number;
  supplier: string;
  date: string;
  transactionType: number;
  value: number;
  appropriated: number;
  deductionTaken: number;
  batchNumber: number;
  batchItem: number;
}

export interface BatchView {
  batchNumber: number;
  itemCount: number;
  maxItems: number;
  batchTotal: number;
  full: boolean;
  blocked: boolean;
  blockedMessage: string | null;
  defaultDate: string;
  payments: PaymentView[];
  revision: number;
}

export interface SupplierSummary {
  account: string;
  name: string;
}

export interface SupplierView {
  account: string;
  name: string;
  addressLines: string[];
  currentBalance: number;
  unappliedBalance: number;
}

export interface LineDecision {
  invoice: number;
  amount: number | null;
  settleInFull: boolean | null;
}

export interface PaymentRequest {
  date: string;
  supplier: string;
  amount: number;
  allocateUnapplied: boolean;
  lines: LineDecision[];
  revision?: number;
}

export type LineStatus = 'CLEARED' | 'PART_PAID' | 'NO_CHANGE' | 'TOO_HIGH' | 'NOT_REACHED';

export interface LineView {
  invoice: number;
  date: string;
  ref: string;
  outstanding: number;
  discount: number;
  amountDue: number | null;
  proposal: number | null;
  suggested: number | null;
  applied: number | null;
  status: LineStatus;
  settlePrompted: boolean;
  settledInFull: boolean;
  message: string | null;
}

export interface AppropriationView {
  batchNumber: number;
  batchItem: number;
  transactionType: number;
  paymentValue: number;
  appropriated: number;
  unappropriated: number;
  deductionTaken: number;
  lines: LineView[];
  errors: string[];
  valid: boolean;
  revision: number;
}

export interface SavedPayment {
  payment: PaymentView;
  appropriation: AppropriationView;
  batch: BatchView;
}

export interface ApiError {
  code: string;
  message: string;
  appropriation: AppropriationView | null;
}
