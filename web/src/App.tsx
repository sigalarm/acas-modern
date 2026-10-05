import { useEffect, useMemo, useState } from 'react';
import { ApiFailure, api } from './api';
import { AppropriationTable, type LineOverride } from './components/AppropriationTable';
import { BatchBar } from './components/BatchBar';
import { SupplierCard } from './components/SupplierCard';
import { formatDate, formatMoney, parseMoney } from './money';
import type {
  AppropriationView,
  BatchView,
  PaymentRequest,
  SavedPayment,
  SupplierSummary,
  SupplierView,
} from './types';

// pl080 accumulates the appropriation in PIC 9(6)V99.
const MAX_PAYMENT = 999999.99;

function message(error: unknown): string {
  return error instanceof Error ? error.message : String(error);
}

export function App() {
  const [batch, setBatch] = useState<BatchView | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [suppliers, setSuppliers] = useState<SupplierSummary[]>([]);
  const [date, setDate] = useState('');
  const [account, setAccount] = useState('');
  const [supplier, setSupplier] = useState<SupplierView | null>(null);
  const [lookupError, setLookupError] = useState<string | null>(null);
  const [allocate, setAllocate] = useState(false);
  const [amountText, setAmountText] = useState('');
  const [overrides, setOverrides] = useState<Record<number, LineOverride>>({});
  const [preview, setPreview] = useState<AppropriationView | null>(null);
  const [previewFor, setPreviewFor] = useState<string | null>(null);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [saved, setSaved] = useState<SavedPayment | null>(null);
  const [previewNonce, setPreviewNonce] = useState(0);
  const [staleNotice, setStaleNotice] = useState<string | null>(null);

  useEffect(() => {
    api.batch()
      .then((b) => {
        setBatch(b);
        setDate(b.defaultDate);
      })
      .catch((e: unknown) => setLoadError(message(e)));
    api.suppliers('').then(setSuppliers).catch((e: unknown) => setLoadError(message(e)));
  }, []);

  const accountKey = account.trim().toUpperCase();
  useEffect(() => {
    if (!accountKey) {
      setSupplier(null);
      setLookupError(null);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      api.supplier(accountKey, controller.signal)
        .then((s) => {
          setSupplier(s);
          setLookupError(null);
          setAllocate(false);
          setOverrides({});
        })
        .catch((e: unknown) => {
          if (controller.signal.aborted) {
            return;
          }
          setSupplier(null);
          setLookupError(e instanceof ApiFailure && e.status === 404
            ? `No supplier with account ${accountKey}`
            : message(e));
        });
    }, 250);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [accountKey]);

  const amount = parseMoney(amountText);
  let amountError: string | null = null;
  if (amountText.trim() && (amount === null || amount > MAX_PAYMENT)) {
    amountError = 'Enter an amount up to 999,999.99 with at most two decimals';
  } else if (amount === 0) {
    amountError = 'Amount must be more than zero';
  } else if (allocate && supplier && amount !== null && amount > supplier.unappliedBalance) {
    amountError = `More than the unapplied balance of ${formatMoney(supplier.unappliedBalance)}`;
  }

  const overrideErrors = Object.entries(overrides)
    .filter(([, o]) => o.amount !== undefined && o.amount.trim() !== '' && parseMoney(o.amount) === null)
    .map(([invoice]) => `Invoice ${invoice}: enter a valid amount`);

  const requestKey = useMemo(() => {
    if (!supplier || supplier.account !== accountKey || !date || amount === null || amount <= 0 || amountError || overrideErrors.length) {
      return null;
    }
    const request: PaymentRequest = {
      date,
      supplier: supplier.account,
      amount,
      allocateUnapplied: allocate,
      lines: Object.entries(overrides).map(([invoice, o]) => ({
        invoice: Number(invoice),
        amount: o.amount === undefined ? null : (parseMoney(o.amount) ?? 0),
        settleInFull: o.settleInFull ?? null,
      })),
    };
    return JSON.stringify(request);
  }, [supplier, accountKey, date, amount, amountError, overrideErrors.length, allocate, overrides]);

  useEffect(() => {
    if (!requestKey) {
      setPreview(null);
      setPreviewFor(null);
      setPreviewError(null);
      return;
    }
    const controller = new AbortController();
    const timer = setTimeout(() => {
      api.preview(JSON.parse(requestKey) as PaymentRequest, controller.signal)
        .then((p) => {
          setPreview(p);
          setPreviewFor(requestKey);
          setPreviewError(null);
        })
        .catch((e: unknown) => {
          if (!controller.signal.aborted) {
            setPreview(null);
            setPreviewFor(null);
            setPreviewError(message(e));
          }
        });
    }, 250);
    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [requestKey, previewNonce]);

  function clearForm() {
    setAccount('');
    setSupplier(null);
    setAllocate(false);
    setAmountText('');
    setOverrides({});
    setPreview(null);
    setPreviewFor(null);
    setPreviewError(null);
    setStaleNotice(null);
  }

  function changeAccount(value: string) {
    setAccount(value);
    if (supplier && supplier.account !== value.trim().toUpperCase()) {
      setSupplier(null);
      setAllocate(false);
      setOverrides({});
      setPreview(null);
      setPreviewFor(null);
    }
  }

  async function save() {
    if (!requestKey || !preview) {
      return;
    }
    setSaving(true);
    try {
      const request: PaymentRequest = { ...(JSON.parse(requestKey) as PaymentRequest), revision: preview.revision };
      const result = await api.save(request);
      setSaved(result);
      setBatch(result.batch);
      clearForm();
    } catch (e: unknown) {
      if (e instanceof ApiFailure && e.error?.code === 'STALE_PREVIEW') {
        setStaleNotice(message(e));
        setPreview(null);
        setPreviewFor(null);
        setPreviewNonce((n) => n + 1);
        api.batch().then(setBatch).catch((err: unknown) => setLoadError(message(err)));
        return;
      }
      if (e instanceof ApiFailure && e.error?.appropriation) {
        setPreview(e.error.appropriation);
      }
      setPreviewError(message(e));
    } finally {
      setSaving(false);
    }
  }

  async function closeBatch() {
    if (!batch || !window.confirm(`Close batch ${batch.batchNumber} with ${batch.itemCount} payment(s)?`)) {
      return;
    }
    try {
      setBatch(await api.closeBatch());
      setSaved(null);
      clearForm();
    } catch (e: unknown) {
      setLoadError(message(e));
    }
  }

  async function resetDemo() {
    try {
      const b = await api.reset();
      setBatch(b);
      setDate(b.defaultDate);
      setSaved(null);
      clearForm();
    } catch (e: unknown) {
      setLoadError(message(e));
    }
  }

  if (!batch) {
    return <main className="loading">{loadError ?? 'Loading…'}</main>;
  }

  const entryDisabled = batch.blocked || batch.full;
  const previewCurrent = preview !== null && previewFor === requestKey;
  const canSave = !entryDisabled && !saving && requestKey !== null && previewCurrent && preview.valid;
  const errors = [...overrideErrors, ...(preview?.errors ?? [])];

  return (
    <div className="app">
      <BatchBar batch={batch} onClose={closeBatch} onReset={resetDemo} />
      {loadError ? <p className="error" role="alert">{loadError}</p> : null}
      {batch.full ? <p className="error" role="alert">Batch Closed........Full! Close the batch to continue.</p> : null}
      {saved ? (
        <p className="success" role="status">
          Payment {saved.payment.reference} saved for {saved.payment.supplier}: {formatMoney(saved.payment.value)},
          {' '}{formatMoney(saved.payment.appropriated)} appropriated
          {saved.payment.deductionTaken > 0 ? `, ${formatMoney(saved.payment.deductionTaken)} discount taken` : ''}.
        </p>
      ) : null}

      <main className="layout">
        <form className="card entry" onSubmit={(e) => { e.preventDefault(); void save(); }}>
          <h2>Payment</h2>
          <fieldset disabled={entryDisabled}>
            <label>
              Payment date
              <input type="date" value={date} required onChange={(e) => setDate(e.target.value)} />
            </label>
            <label>
              Supplier account
              <input
                list="suppliers"
                value={account}
                maxLength={7}
                autoComplete="off"
                placeholder="e.g. ACME001"
                onChange={(e) => changeAccount(e.target.value)}
              />
              <datalist id="suppliers">
                {suppliers.map((s) => <option key={s.account} value={s.account}>{s.name}</option>)}
              </datalist>
            </label>
            {lookupError ? <p className="field-error" role="alert">{lookupError}</p> : null}
            {supplier ? <SupplierCard supplier={supplier} /> : null}
            {supplier && supplier.unappliedBalance > 0 ? (
              <label className="check">
                <input
                  type="checkbox"
                  checked={allocate}
                  onChange={(e) => {
                    setAllocate(e.target.checked);
                    setOverrides({});
                    if (e.target.checked && !amountText) {
                      setAmountText(supplier.unappliedBalance.toFixed(2));
                    }
                  }}
                />
                Allocate the unapplied balance instead of entering a new payment
              </label>
            ) : null}
            <label>
              {allocate ? 'Amount to allocate' : 'Payment amount'}
              <input
                inputMode="decimal"
                value={amountText}
                placeholder="0.00"
                onChange={(e) => {
                  setAmountText(e.target.value);
                  setOverrides({});
                }}
              />
            </label>
            {amountError ? <p className="field-error" role="alert">{amountError}</p> : null}
          </fieldset>
          <div className="actions">
            <button type="submit" disabled={!canSave}>{saving ? 'Saving…' : 'Save payment'}</button>
            <button type="button" className="secondary" onClick={clearForm}>Cancel</button>
          </div>
        </form>

        <section className="card preview" aria-label="Appropriation">
          <h2>Appropriation</h2>
          {preview ? (
            <>
              <p className="hint">
                Oldest invoices first, as ACAS appropriates. Change an amount to pay less (0 leaves the invoice
                unchanged).
              </p>
              <AppropriationTable
                appropriation={preview}
                overrides={overrides}
                disabled={entryDisabled}
                onOverride={(invoice, o) => setOverrides((current) => ({ ...current, [invoice]: o }))}
              />
            </>
          ) : (
            <p className="empty">Choose a supplier and enter an amount to see how it will be appropriated.</p>
          )}
          {staleNotice ? <p className="error" role="alert">{staleNotice}</p> : null}
          {errors.length || previewError ? (
            <ul className="error" role="alert">
              {errors.map((e) => <li key={e}>{e}</li>)}
              {previewError && !errors.length ? <li>{previewError}</li> : null}
            </ul>
          ) : null}
        </section>
      </main>

      {batch.payments.length ? (
        <section className="card payments" aria-label="Payments in this batch">
          <h2>Payments in batch {batch.batchNumber}</h2>
          <table>
            <thead>
              <tr>
                <th>Reference</th><th>Supplier</th><th>Date</th><th>Type</th>
                <th className="num">Value</th><th className="num">Appropriated</th><th className="num">Discount</th>
              </tr>
            </thead>
            <tbody>
              {batch.payments.map((p) => (
                <tr key={p.reference}>
                  <td>{p.batchNumber}/{p.batchItem} ({p.reference})</td>
                  <td>{p.supplier}</td>
                  <td>{formatDate(p.date)}</td>
                  <td>{p.transactionType === 6 ? 'Unapplied allocation' : 'Payment'}</td>
                  <td className="num">{formatMoney(p.value)}</td>
                  <td className="num">{formatMoney(p.appropriated)}</td>
                  <td className="num">{formatMoney(p.deductionTaken)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      ) : null}
    </div>
  );
}
