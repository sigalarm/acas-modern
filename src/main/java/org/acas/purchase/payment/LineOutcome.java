package org.acas.purchase.payment;

/** What pl080 does with the amount entered against an offered line. */
public enum LineOutcome {
    /** Zero entered: the invoice is left untouched ("....No change"). */
    NO_CHANGE,
    /**
     * The amount would take the appropriation past the payment value, or exceeds what is owed on
     * the invoice ("Payment Too High"). The same line is offered again.
     */
    TOO_HIGH,
    /**
     * The amount equals the amount due less a settlement discount the payment is too late for;
     * pl080 asks whether to settle the invoice in full. Answer with {@link Appropriation#settle}.
     */
    SETTLE_PROMPT,
    /** The amount was applied to the invoice. */
    APPLIED
}
