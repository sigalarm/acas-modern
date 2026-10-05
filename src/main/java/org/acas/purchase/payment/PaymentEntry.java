package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;

/**
 * One pl080 payment batch: payments entered against a {@link PurchaseLedger} until the batch is
 * closed. Not thread safe.
 */
public final class PaymentEntry {
    public static final int MAX_ITEMS = 999;
    static final BigDecimal MAX_AMOUNT = new BigDecimal("9999999.99");

    final PurchaseLedger ledger;
    /** {@code k}: payments entered in this batch. */
    int itemCount;
    /** {@code batch-value}: ordinary payments entered in this batch. */
    BigDecimal batchTotal = Fixed.ZERO;
    /** pl080's single {@code OI-Header} work area, which carries over between records and payments. */
    final OpenItem header = new OpenItem();
    private boolean closed;

    private PaymentEntry(PurchaseLedger ledger) {
        this.ledger = ledger;
    }

    public static PaymentEntry open(PurchaseLedger ledger) {
        if (ledger.invoicesNotPosted()) {
            throw new PaymentEntryException(PaymentEntryException.Reason.INVOICES_NOT_POSTED,
                    "PL121 Invoices Not Posted; Payment Entry Not Allowed");
        }
        return new PaymentEntry(ledger);
    }

    /** A copy of this batch, working on {@code ledgerCopy}, for previews. */
    public PaymentEntry copy(PurchaseLedger ledgerCopy) {
        PaymentEntry c = new PaymentEntry(ledgerCopy);
        c.itemCount = itemCount;
        c.batchTotal = batchTotal;
        c.header.copyFrom(header);
        c.closed = closed;
        return c;
    }

    public PurchaseLedger ledger() {
        return ledger;
    }

    /** The batch number the next payment gets (pl080 replaces a zero batch number with 1). */
    public int batchNumber() {
        return ledger.nextBatch == 0 ? 1 : ledger.nextBatch;
    }

    public int itemCount() {
        return itemCount;
    }

    public BigDecimal batchTotal() {
        return batchTotal;
    }

    public boolean isFull() {
        return itemCount == MAX_ITEMS;
    }

    public boolean isClosed() {
        return closed;
    }

    /** Looks a supplier up by account number, upper-cased as pl080 does. */
    public Supplier supplier(String account) {
        String key = account == null ? "" : account.toUpperCase(Locale.ROOT);
        return ledger.supplier(key).orElseThrow(() -> new PaymentEntryException(
                PaymentEntryException.Reason.UNKNOWN_SUPPLIER, "No supplier with account " + key.strip()));
    }

    /** Starts an ordinary payment (transaction type 5). */
    public Appropriation startPayment(LocalDate date, Supplier supplier, BigDecimal amount) {
        checkOpen();
        BigDecimal value = amount(amount);
        ledger.paymentsEntered = 1;
        batchTotal = Fixed.unsigned(batchTotal.add(value), 7);
        return start(date, supplier, value, OpenItem.TYPE_PAYMENT);
    }

    /**
     * Allocates part or all of the supplier's unapplied balance (transaction type 6). The supplier
     * record is updated immediately, as pl080 rewrites it before appropriation starts.
     */
    public Appropriation startUnappliedAllocation(LocalDate date, Supplier supplier, BigDecimal amount) {
        checkOpen();
        if (supplier.unappliedBalance().signum() == 0) {
            throw new PaymentEntryException(PaymentEntryException.Reason.NO_UNAPPLIED_BALANCE,
                    "Supplier has no unapplied balance");
        }
        BigDecimal value = amount(amount);
        if (value.compareTo(supplier.unappliedBalance()) > 0) {
            throw new PaymentEntryException(PaymentEntryException.Reason.EXCEEDS_UNAPPLIED,
                    "Allocation exceeds the unapplied balance of " + supplier.unappliedBalance());
        }
        supplier.consumeUnapplied(value);
        return start(date, supplier, value, OpenItem.TYPE_UNAPPLIED_ALLOCATION);
    }

    /** Ends the batch: the next batch number is reserved and the open item file marked changed. */
    public void close() {
        if (closed) {
            throw new PaymentEntryException(PaymentEntryException.Reason.ILLEGAL_STATE, "Batch is closed");
        }
        ledger.nextBatch = (short) (ledger.nextBatch + 1);
        ledger.openItemsChanged = 'Y';
        closed = true;
    }

    private Appropriation start(LocalDate date, Supplier supplier, BigDecimal value, int type) {
        if (ledger.nextBatch == 0) {
            ledger.nextBatch = 1;
        }
        itemCount++;
        return new Appropriation(this, date, supplier.key(), value, type);
    }

    private void checkOpen() {
        if (closed) {
            throw new PaymentEntryException(PaymentEntryException.Reason.ILLEGAL_STATE, "Batch is closed");
        }
        if (isFull()) {
            throw new PaymentEntryException(PaymentEntryException.Reason.BATCH_FULL, "Batch Closed........Full!");
        }
    }

    private static BigDecimal amount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0 || amount.scale() > 2
                && amount.stripTrailingZeros().scale() > 2) {
            throw new PaymentEntryException(PaymentEntryException.Reason.INVALID_AMOUNT,
                    "Amount must be between 0.01 and " + MAX_AMOUNT);
        }
        return amount.setScale(2);
    }
}
