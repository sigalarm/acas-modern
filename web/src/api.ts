import type {
  ApiError,
  AppropriationView,
  BatchView,
  PaymentRequest,
  SavedPayment,
  SupplierSummary,
  SupplierView,
} from './types';

export class ApiFailure extends Error {
  readonly status: number;
  readonly error: ApiError | null;

  constructor(status: number, error: ApiError | null) {
    super(error?.message ?? `Request failed (${status})`);
    this.status = status;
    this.error = error;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: init.body ? { 'Content-Type': 'application/json' } : undefined,
  });
  if (!response.ok) {
    const body = response.headers.get('Content-Type')?.includes('json')
      ? ((await response.json()) as ApiError)
      : null;
    throw new ApiFailure(response.status, body);
  }
  return (await response.json()) as T;
}

export const api = {
  batch: () => request<BatchView>('/api/batch'),
  closeBatch: () => request<BatchView>('/api/batch/close', { method: 'POST' }),
  reset: () => request<BatchView>('/api/demo/reset', { method: 'POST' }),
  suppliers: (query: string) => request<SupplierSummary[]>(`/api/suppliers?query=${encodeURIComponent(query)}`),
  supplier: (account: string, signal?: AbortSignal) =>
    request<SupplierView>(`/api/suppliers/${encodeURIComponent(account)}`, { signal }),
  preview: (payment: PaymentRequest, signal?: AbortSignal) =>
    request<AppropriationView>('/api/payments/preview', { method: 'POST', body: JSON.stringify(payment), signal }),
  save: (payment: PaymentRequest) =>
    request<SavedPayment>('/api/payments', { method: 'POST', body: JSON.stringify(payment) }),
};
