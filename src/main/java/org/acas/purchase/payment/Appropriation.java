package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Appropriates one payment across the supplier's outstanding invoices, oldest key first, the way
 * pl080's {@code payment-appropriate} section does. Call {@link #next()} for each offered line,
 * answer it with {@link #pay} (and {@link #settle} after {@link LineOutcome#SETTLE_PROMPT}), then
 * {@link #finish()} once {@code next()} is empty.
 */
public final class Appropriation {
    private enum State { READY, OFFERED, AWAITING_SETTLE, ENDED, FINISHED }

    private final PaymentEntry entry;
    private final PurchaseLedger ledger;
    private final OpenItem header;
    private final LocalDate payDate;
    private final String supplierKey;
    private final BigDecimal payValue;
    private final int type;
    private State state = State.READY;
    private boolean started;
    private OpenItemKey cursor;
    /** {@code approp-amount}, {@code PIC 9(6)V99}. */
    private BigDecimal approp = Fixed.ZERO;
    /** {@code deduct-taken}, {@code PIC 9(6)V99}. */
    private BigDecimal deductTaken = Fixed.ZERO;
    private BigDecimal workNet = Fixed.ZERO;
    private BigDecimal workDed = Fixed.ZERO;
    private BigDecimal work1 = Fixed.ZERO;
    private BigDecimal work2 = Fixed.ZERO;
    private BigDecimal payPaid = Fixed.ZERO;
    private boolean discountDue;
    private OfferedLine offered;
    private boolean recordWritten;

    Appropriation(PaymentEntry entry, LocalDate payDate, String supplierKey, BigDecimal payValue, int type) {
        this.entry = entry;
        this.ledger = entry.ledger;
        this.header = entry.header;
        this.payDate = payDate;
        this.supplierKey = supplierKey;
        this.payValue = payValue;
        this.type = type;
    }

    public BigDecimal paymentValue() {
        return payValue;
    }

    public int transactionType() {
        return type;
    }

    public BigDecimal appropriated() {
        return approp;
    }

    public BigDecimal deductionTaken() {
        return deductTaken;
    }

    /**
     * The next invoice to appropriate against, or empty once the payment is fully appropriated or
     * the supplier has no more outstanding invoices. Returns the same line again until it is
     * answered.
     */
    public Optional<OfferedLine> next() {
        switch (state) {
            case OFFERED:
                return Optional.of(offered);
            case AWAITING_SETTLE:
                throw illegal("Answer the settle prompt first");
            case ENDED:
            case FINISHED:
                return Optional.empty();
            default:
                break;
        }
        if (started && approp.compareTo(payValue) >= 0) {
            state = State.ENDED;
            return Optional.empty();
        }
        while (true) {
            Optional<OpenItem> read = started
                    ? ledger.readFrom(cursor, false)
                    : ledger.readFrom(new OpenItemKey(supplierKey, 0), true);
            started = true;
            if (read.isEmpty()) {
                state = State.ENDED;
                return Optional.empty();
            }
            OpenItem record = read.get();
            cursor = record.key();
            header.copyFrom(record);
            if (header.type != OpenItem.TYPE_INVOICE) {
                continue;
            }
            if (!header.supplier.equals(supplierKey)) {
                state = State.ENDED;
                return Optional.empty();
            }
            if (header.batchNumber != 0) {
                continue;
            }
            workNet = header.gross();
            workDed = Fixed.unsigned(header.deductAmt, 3);
            if (workNet.compareTo(header.paid) == 0 && header.status == 0) {
                // pl080 closes the work area copy but rewrites the record buffer it read, so the
                // stored invoice is left unchanged.
                header.dateCleared = payDate;
                header.status = 1;
                ledger.rewrite(record);
                continue;
            }
            if (workNet.compareTo(header.paid) <= 0) {
                continue;
            }
            offer();
            return Optional.of(offered);
        }
    }

    /** Applies {@code amount} to the offered line. */
    public LineOutcome pay(BigDecimal amount) {
        if (state != State.OFFERED) {
            throw illegal("No line is offered");
        }
        if (amount == null || amount.signum() < 0 || amount.compareTo(PaymentEntry.MAX_AMOUNT) > 0) {
            throw new PaymentEntryException(PaymentEntryException.Reason.INVALID_AMOUNT,
                    "Amount must be between 0.00 and " + PaymentEntry.MAX_AMOUNT);
        }
        payPaid = Fixed.unsigned(amount, 7);
        if (payPaid.signum() == 0) {
            state = State.READY;
            return LineOutcome.NO_CHANGE;
        }
        header.batchNumber = ledger.nextBatch;
        header.batchItem = entry.itemCount;
        approp = Fixed.unsigned(approp.add(payPaid), 6);
        if (approp.compareTo(payValue) > 0) {
            return tooHigh();
        }
        if (payPaid.compareTo(work1) == 0) {
            clear();
            if (discountDue) {
                header.net = Fixed.signed(header.net.subtract(header.deductAmt), 7);
                header.deductAmt = Fixed.ZERO;
                deductTaken = Fixed.unsigned(deductTaken.add(work2), 6);
            }
            endLine();
            return LineOutcome.APPLIED;
        }
        if (!discountDue) {
            work1 = Fixed.unsigned(work1.subtract(work2), 7);
        }
        if (payPaid.compareTo(work1) > 0) {
            return tooHigh();
        }
        if (payPaid.compareTo(work1) != 0) {
            endLine();
            return LineOutcome.APPLIED;
        }
        state = State.AWAITING_SETTLE;
        return LineOutcome.SETTLE_PROMPT;
    }

    /** Answers the settle prompt: {@code true} settles the invoice in full, taking the deduction. */
    public LineOutcome settle(boolean settleInFull) {
        if (state != State.AWAITING_SETTLE) {
            throw illegal("No settle prompt is pending");
        }
        if (settleInFull) {
            clear();
            deductTaken = Fixed.unsigned(deductTaken.add(work2), 6);
            header.net = Fixed.signed(header.net.subtract(header.deductAmt), 7);
            header.deductAmt = Fixed.ZERO;
        }
        endLine();
        return LineOutcome.APPLIED;
    }

    /**
     * Whether {@link #finish()} stored the payment record. Like pl080, it is not stored when the
     * supplier already has an open item with the same invoice number.
     */
    public boolean recordWritten() {
        return recordWritten;
    }

    /** Whether the invoice last answered is now closed (status 1). */
    public boolean lineCleared() {
        return header.isClosed();
    }

    /**
     * The supplier's remaining unbatched, outstanding invoices after the current position: the
     * ones this payment will not reach if it ends now.
     */
    public List<OpenItem> remainingInvoices() {
        List<OpenItem> remaining = new ArrayList<>();
        Optional<OpenItem> read = started
                ? ledger.readFrom(cursor, false)
                : ledger.readFrom(new OpenItemKey(supplierKey, 0), true);
        while (read.isPresent()) {
            OpenItem item = read.get();
            if (!item.supplier.equals(supplierKey) && item.type == OpenItem.TYPE_INVOICE) {
                break;
            }
            if (item.type == OpenItem.TYPE_INVOICE && item.batchNumber == 0 && item.gross().compareTo(item.paid) > 0) {
                remaining.add(item);
            }
            read = ledger.readFrom(item.key(), false);
        }
        return remaining;
    }

    /** Writes the payment record (pl080's {@code main-end}) and returns it. */
    public OpenItem finish() {
        if (state != State.ENDED) {
            throw illegal("Appropriation has not ended");
        }
        // Fields not set here keep whatever the work area last held.
        header.carriage = Fixed.ZERO;
        header.vat = Fixed.ZERO;
        header.cVat = Fixed.ZERO;
        header.status = 0;
        header.cr = 0;
        header.days = 0;
        header.deductVat = Fixed.ZERO;
        header.discount = Fixed.ZERO;
        header.deductDays = 0;
        header.pc = Fixed.ZERO;
        header.applied = " ";
        header.supplier = supplierKey;
        header.date = payDate;
        header.type = type;
        header.paid = payValue;
        header.net = approp;
        header.deductAmt = Fixed.signed(deductTaken, 3);
        header.batchNumber = ledger.nextBatch;
        header.batchItem = entry.itemCount;
        header.invoice = Fixed.unsignedInt(header.batchNumber * 1000L + header.batchItem, 8);
        recordWritten = ledger.write(header);
        approp = Fixed.ZERO;
        deductTaken = Fixed.ZERO;
        state = State.FINISHED;
        return header.copy();
    }

    private void offer() {
        work1 = Fixed.unsigned(workNet, 7);
        work2 = workDed;
        LocalDate termEnd = header.date == null ? null : header.date.plusDays(1L + header.deductDays);
        discountDue = termEnd != null && termEnd.isAfter(payDate);
        BigDecimal discount = Fixed.ZERO;
        if (discountDue) {
            work1 = Fixed.unsigned(work1.subtract(work2), 7);
            discount = work2;
        }
        work1 = Fixed.unsigned(work1.subtract(header.paid), 7);
        payPaid = work1;
        if (payPaid.compareTo(payValue) > 0) {
            payPaid = Fixed.signed(payValue.subtract(approp), 7);
        }
        BigDecimal left = Fixed.signed(payValue.subtract(approp), 7);
        offered = new OfferedLine(header.invoice, header.date, header.ref(),
                Fixed.signed(workNet.subtract(header.paid), 7), discount, work1, payPaid, work1.min(left));
        state = State.OFFERED;
    }

    private LineOutcome tooHigh() {
        approp = Fixed.unsigned(approp.subtract(payPaid), 6);
        offer();
        return LineOutcome.TOO_HIGH;
    }

    private void clear() {
        header.dateCleared = payDate;
        header.status = 1;
    }

    private void endLine() {
        header.paid = Fixed.signed(header.paid.add(payPaid), 7);
        header.pc = Fixed.signed(header.pc.add(payPaid), 7);
        header.cr = payPaid.movePointRight(2).intValue();
        workNet = Fixed.signed(workNet.subtract(header.deductAmt), 7);
        if (header.paid.compareTo(workNet) == 0) {
            clear();
        }
        ledger.rewrite(header);
        state = State.READY;
    }

    private static PaymentEntryException illegal(String message) {
        return new PaymentEntryException(PaymentEntryException.Reason.ILLEGAL_STATE, message);
    }
}
