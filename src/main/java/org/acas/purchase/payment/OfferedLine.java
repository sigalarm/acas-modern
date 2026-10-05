package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An outstanding invoice offered for appropriation: one line of pl080's
 * "Folio No / Date / Amount / Deductable / Paid" table.
 *
 * @param outstanding gross amount still owed, before any settlement discount
 * @param discount    settlement discount available because the payment is inside the invoice's
 *                    discount term; zero otherwise
 * @param amountDue   what clears the invoice now ({@code work-1}): outstanding less the discount
 * @param proposal    the amount pl080 pre-fills: the amount due, or the payment value less what is
 *                    already appropriated when the amount due exceeds the whole payment value. It
 *                    can exceed what is left of the payment, which pl080 then rejects as too high.
 * @param suggested   the amount due capped at what is left of the payment; not a legacy value
 */
public record OfferedLine(long invoice, LocalDate date, String ref, BigDecimal outstanding, BigDecimal discount,
                          BigDecimal amountDue, BigDecimal proposal, BigDecimal suggested) {
}
