import { render, screen } from '@testing-library/react';
import { AppropriationTable } from './AppropriationTable';
import type { AppropriationView } from '../types';

const view: AppropriationView = {
  batchNumber: 42,
  batchItem: 1,
  transactionType: 5,
  paymentValue: 700,
  appropriated: 700,
  unappropriated: 0,
  deductionTaken: 9.6,
  errors: [],
  valid: true,
  revision: 1,
  lines: [
    { invoice: 1001, date: '2026-09-01', ref: 'AC-1001', outstanding: 480, discount: 9.6, amountDue: 470.4,
      proposal: 470.4, suggested: 470.4, applied: 470.4, status: 'CLEARED', settlePrompted: false,
      settledInFull: false, message: null },
    { invoice: 1002, date: '2026-09-15', ref: 'AC-1002', outstanding: 300, discount: 0, amountDue: 300,
      proposal: 300, suggested: 229.6, applied: 229.6, status: 'PART_PAID', settlePrompted: true,
      settledInFull: true, message: null },
    { invoice: 1003, date: '2026-09-20', ref: 'AC-1003', outstanding: 100, discount: 0, amountDue: null,
      proposal: null, suggested: null, applied: null, status: 'NOT_REACHED', settlePrompted: false,
      settledInFull: false, message: 'Payment fully appropriated' },
  ],
};

it('shows discount, amounts to pay and line status', () => {
  render(<AppropriationTable appropriation={view} overrides={{}} onOverride={() => {}} disabled={false} />);
  expect(screen.getByLabelText<HTMLInputElement>('Pay invoice 1001').value).toBe('470.40');
  expect(screen.getByLabelText<HTMLInputElement>('Pay invoice 1002').value).toBe('229.60');
  expect(screen.queryByLabelText('Pay invoice 1003')).toBeNull();
  expect(screen.getByText('£9.60', { selector: 'td' })).toBeTruthy();
  expect(screen.getByText('Not reached')).toBeTruthy();
  expect(screen.getByLabelText<HTMLInputElement>('Settle in full').checked).toBe(true);
});
