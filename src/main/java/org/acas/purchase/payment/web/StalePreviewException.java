package org.acas.purchase.payment.web;

/** A save whose preview was computed against a ledger that has since changed. */
final class StalePreviewException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    StalePreviewException() {
        super("The ledger has changed since this payment was previewed; review the new preview and save again");
    }
}
