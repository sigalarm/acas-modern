import { formatMoney } from '../money';
import type { SupplierView } from '../types';

export function SupplierCard({ supplier }: { supplier: SupplierView }) {
  return (
    <section className="supplier-card" aria-label="Supplier">
      <h3>{supplier.name}</h3>
      <address>
        {supplier.addressLines.filter((line) => line).map((line) => <div key={line}>{line}</div>)}
      </address>
      <dl>
        <div><dt>Current balance</dt><dd>{formatMoney(supplier.currentBalance)}</dd></div>
        <div>
          <dt>Unapplied balance</dt>
          <dd className={supplier.unappliedBalance > 0 ? 'highlight' : undefined}>
            {formatMoney(supplier.unappliedBalance)}
          </dd>
        </div>
      </dl>
    </section>
  );
}
