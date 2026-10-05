package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Replays the keystrokes of a pl080 parity fixture against {@link PaymentEntry}, following the
 * legacy screen's prompt order: each key is typed over the field's pre-filled value, then Enter.
 */
final class LegacyKeyReplay {
    /** The {@code To-Day} the pl080h harness passes in. */
    private static final String HARNESS_TODAY = "02/10/2026";

    private final Deque<String> keys;
    private String today = HARNESS_TODAY;

    private LegacyKeyReplay(List<String> keys) {
        this.keys = new ArrayDeque<>(keys);
    }

    static void run(PurchaseLedger ledger, List<String> keys) {
        new LegacyKeyReplay(keys).run(ledger);
    }

    private void run(PurchaseLedger ledger) {
        PaymentEntry entry;
        try {
            entry = PaymentEntry.open(ledger);
        } catch (PaymentEntryException e) {
            key("", 1);
            requireAllKeysUsed();
            return;
        }
        boolean more = true;
        while (more) {
            more = payment(entry);
        }
        entry.close();
        requireAllKeysUsed();
    }

    /** One pass of {@code New-Payment-Main}; false when the operator leaves payment entry. */
    private boolean payment(PaymentEntry entry) {
        while (true) {
            Optional<LocalDate> date = dateInput();
            if (date.isEmpty()) {
                return false;
            }
            while (true) {
                if (entry.isFull()) {
                    return false;
                }
                String account = key(" ".repeat(7), 7).toUpperCase(Locale.ROOT);
                if (account.isBlank()) {
                    break;
                }
                Supplier supplier;
                try {
                    supplier = entry.supplier(account);
                } catch (PaymentEntryException e) {
                    continue;
                }
                Appropriation appropriation;
                if (supplier.unappliedBalance().signum() != 0 && allocateUnapplied()) {
                    Optional<Appropriation> allocation = unappliedAllocation(entry, date.get(), supplier);
                    if (allocation.isEmpty()) {
                        return true;
                    }
                    appropriation = allocation.get();
                } else {
                    BigDecimal value = money(BigDecimal.ZERO);
                    if (value.signum() == 0) {
                        continue;
                    }
                    appropriation = entry.startPayment(date.get(), supplier, value);
                }
                appropriate(appropriation);
                return moreData();
            }
        }
    }

    private Optional<LocalDate> dateInput() {
        while (true) {
            String typed = key(today, 10);
            if (typed.isBlank()) {
                return Optional.empty();
            }
            Optional<LocalDate> date = LegacyDate.parse(typed);
            if (date.isPresent()) {
                today = LegacyDate.format(date.get());
                return date;
            }
        }
    }

    private boolean allocateUnapplied() {
        String reply = "Y";
        while (true) {
            reply = key(reply, 1).toUpperCase(Locale.ROOT);
            if (reply.equals("Y") || reply.equals("N")) {
                return reply.equals("Y");
            }
        }
    }

    private Optional<Appropriation> unappliedAllocation(PaymentEntry entry, LocalDate date, Supplier supplier) {
        BigDecimal amount = supplier.unappliedBalance();
        while (true) {
            amount = money(amount);
            if (amount.signum() == 0) {
                return Optional.empty();
            }
            if (amount.compareTo(supplier.unappliedBalance()) <= 0) {
                return Optional.of(entry.startUnappliedAllocation(date, supplier, amount));
            }
        }
    }

    private void appropriate(Appropriation appropriation) {
        Optional<OfferedLine> line;
        while ((line = appropriation.next()).isPresent()) {
            LineOutcome outcome = appropriation.pay(money(line.get().proposal()));
            if (outcome == LineOutcome.SETTLE_PROMPT) {
                appropriation.settle(key("Y", 1).toUpperCase(Locale.ROOT).equals("Y"));
            }
        }
        appropriation.finish();
    }

    private boolean moreData() {
        while (true) {
            String reply = key("Y", 1).toUpperCase(Locale.ROOT);
            if (reply.equals("Y")) {
                return true;
            }
            if (reply.equals("N")) {
                return false;
            }
        }
    }

    /** An amount field ({@code 9999999.99}) pre-filled with {@code prefill}. */
    private BigDecimal money(BigDecimal prefill) {
        BigDecimal p = prefill.setScale(2);
        String shown = String.format("%07d.%02d", p.intValue(), p.remainder(BigDecimal.ONE).movePointRight(2).intValue());
        String typed = key(shown, 10);
        return new BigDecimal(typed.substring(0, 7) + "." + typed.substring(8, 10));
    }

    /** Types the next key over a field of {@code width} pre-filled with {@code prefill}. */
    private String key(String prefill, int width) {
        if (keys.isEmpty()) {
            throw new AssertionError("Ran out of keys at a prompt pre-filled with '" + prefill + "'");
        }
        String typed = keys.poll();
        String field = Fixed.text(prefill, width);
        return typed.length() >= width ? typed.substring(0, width) : typed + field.substring(typed.length());
    }

    private void requireAllKeysUsed() {
        if (!keys.isEmpty()) {
            throw new AssertionError("Unused keys: " + keys);
        }
    }
}
