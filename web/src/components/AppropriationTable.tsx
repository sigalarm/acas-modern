import { formatDate, formatMoney } from '../money';
import type { AppropriationView, LineStatus } from '../types';

export interface LineOverride {
  amount?: string;
  settleInFull?: boolean;
}

interface Props {
  appropriation: AppropriationView;
  overrides: Record<number, LineOverride>;
  onOverride: (invoice: number, override: LineOverride) => void;
  disabled: boolean;
}

const STATUS_LABEL: Record<LineStatus, string> = {
  CLEARED: 'Cleared',
  PART_PAID: 'Part paid',
  NO_CHANGE: 'No change',
  TOO_HIGH: 'Too high',
  NOT_REACHED: 'Not reached',
};

export function AppropriationTable({ appropriation, overrides, onOverride, disabled }: Props) {
  if (appropriation.lines.length === 0) {
    return <p className="empty">No outstanding invoices: the whole amount stays unappropriated.</p>;
  }
  return (
    <div className="table-scroll">
    <table className="appropriation">
      <thead>
        <tr>
          <th>Folio</th>
          <th>Date</th>
          <th>Reference</th>
          <th className="num">Outstanding</th>
          <th className="num">Discount</th>
          <th className="num">Amount due</th>
          <th className="num">Pay</th>
          <th>Status</th>
        </tr>
      </thead>
      <tbody>
        {appropriation.lines.map((line) => {
          const override = overrides[line.invoice] ?? {};
          const reached = line.status !== 'NOT_REACHED';
          const amountText = override.amount ?? (line.applied === null ? '' : line.applied.toFixed(2));
          return (
            <tr key={line.invoice} className={`status-${line.status.toLowerCase()}`}>
              <td>{line.invoice}</td>
              <td>{formatDate(line.date)}</td>
              <td>{line.ref}</td>
              <td className="num">{formatMoney(line.outstanding)}</td>
              <td className="num">{line.discount > 0 ? formatMoney(line.discount) : ''}</td>
              <td className="num">{formatMoney(line.amountDue)}</td>
              <td className="num">
                {reached ? (
                  <input
                    name={`pay-${line.invoice}`}
                    aria-label={`Pay invoice ${line.invoice}`}
                    className="amount"
                    inputMode="decimal"
                    value={amountText}
                    disabled={disabled}
                    onChange={(e) => onOverride(line.invoice, { ...override, amount: e.target.value })}
                  />
                ) : null}
              </td>
              <td>
                <span className={`badge badge-${line.status.toLowerCase()}`}>{STATUS_LABEL[line.status]}</span>
                {line.settlePrompted ? (
                  <label className="settle">
                    <input
                      type="checkbox"
                      name={`settle-${line.invoice}`}
                      checked={override.settleInFull ?? line.settledInFull}
                      disabled={disabled}
                      onChange={(e) => onOverride(line.invoice, { ...override, settleInFull: e.target.checked })}
                    />
                    Settle in full
                  </label>
                ) : null}
                {line.message && line.status === 'TOO_HIGH' ? <div className="line-error">{line.message}</div> : null}
              </td>
            </tr>
          );
        })}
      </tbody>
      <tfoot>
        <tr>
          <td colSpan={3}>
            {appropriation.transactionType === 6 ? 'Unapplied balance allocated' : 'Payment'}{' '}
            {formatMoney(appropriation.paymentValue)}
          </td>
          <td colSpan={2} className="num">Discount taken {formatMoney(appropriation.deductionTaken)}</td>
          <td colSpan={2} className="num">Appropriated {formatMoney(appropriation.appropriated)}</td>
          <td className={appropriation.unappropriated > 0 ? 'highlight' : undefined}>
            Unappropriated {formatMoney(appropriation.unappropriated)}
          </td>
        </tr>
      </tfoot>
    </table>
    </div>
  );
}
