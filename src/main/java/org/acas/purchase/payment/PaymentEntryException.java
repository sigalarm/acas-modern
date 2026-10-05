package org.acas.purchase.payment;

/** A payment entry request pl080 would not accept. */
public final class PaymentEntryException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /** Why the request was refused. */
    public enum Reason {
        /** PL121: purchase invoices are not posted, so payment entry is not allowed. */
        INVOICES_NOT_POSTED,
        /** The batch already holds 999 payments ("Batch Closed........Full!"). */
        BATCH_FULL,
        UNKNOWN_SUPPLIER,
        INVALID_DATE,
        INVALID_AMOUNT,
        /** An unapplied-balance allocation larger than the supplier's unapplied balance. */
        EXCEEDS_UNAPPLIED,
        NO_UNAPPLIED_BALANCE,
        /** The appropriation is not in a state that allows the call. */
        ILLEGAL_STATE
    }

    private final Reason reason;

    public PaymentEntryException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
