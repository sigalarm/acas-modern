import { formatMoney } from '../money';
import type { BatchView } from '../types';

interface Props {
  batch: BatchView;
  onClose: () => void;
  onReset: () => void;
}

export function BatchBar({ batch, onClose, onReset }: Props) {
  return (
    <header className="batch-bar">
      <div>
        <h1>Payment Data Entry</h1>
        <p className="subtitle">ACAS Purchase Ledger</p>
      </div>
      {batch.blocked ? (
        <p className="batch-blocked" role="alert">{batch.blockedMessage}</p>
      ) : (
        <dl className="batch-stats" aria-label="Batch">
          <div><dt>Batch</dt><dd data-testid="batch-number">{batch.batchNumber}</dd></div>
          <div><dt>Payments</dt><dd data-testid="batch-items">{batch.itemCount} / {batch.maxItems}</dd></div>
          <div><dt>Total paid</dt><dd data-testid="batch-total">{formatMoney(batch.batchTotal)}</dd></div>
        </dl>
      )}
      <div className="batch-actions">
        <button type="button" className="secondary" onClick={onClose} disabled={batch.blocked}>
          Close batch
        </button>
        <button type="button" className="link" onClick={onReset}>Reset demo data</button>
      </div>
    </header>
  );
}
