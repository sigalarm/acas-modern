package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.StringJoiner;

/**
 * The pipe-delimited text format the {@code pl080h} legacy harness loads and dumps, used for parity
 * fixtures and for the demo ledger. Lines starting with '#' and {@code KEY} records (keystrokes for
 * the legacy screen) are ignored by {@link #parse}.
 */
public final class LedgerFormat {
    private LedgerFormat() {
    }

    public static PurchaseLedger parse(List<String> lines) {
        PurchaseLedger ledger = new PurchaseLedger();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = fields(line, 28);
            switch (f[0]) {
                case "SYS":
                    ledger.nextBatch = Integer.parseInt(f[1]);
                    ledger.invoicesNotPosted = Integer.parseInt(f[2]);
                    ledger.paymentsEntered = Integer.parseInt(f[3]);
                    ledger.addressDelimiter = f[4].isEmpty() ? ' ' : f[4].charAt(0);
                    break;
                case "SUP":
                    ledger.put(new Supplier(f[1], f[2], f[3], Fixed.money(f[4]), Fixed.money(f[5])));
                    break;
                case "OI5":
                    ledger.write(openItem(f));
                    break;
                case "KEY":
                    break;
                default:
                    throw new IllegalArgumentException("Unknown record " + f[0]);
            }
        }
        return ledger;
    }

    public static List<String> dump(PurchaseLedger ledger) {
        List<String> out = new ArrayList<>();
        for (Supplier s : ledger.suppliers()) {
            out.add(join("SUP", s.key().stripTrailing(), s.name(), s.address(), money(s.currentBalance()),
                    money(s.unappliedBalance())));
        }
        for (OpenItem o : ledger.openItems()) {
            out.add(join("OI5", o.supplier.stripTrailing(), Long.toString(o.invoice), LegacyDate.format(o.date),
                    Integer.toString(o.batchNumber), Integer.toString(o.batchItem), Integer.toString(o.type),
                    o.ref(), o.order(), o.holdFlag(), o.unappliedFlag(), money(o.pc), money(o.net), money(o.extra),
                    money(o.carriage), money(o.vat), money(o.discount), money(o.eVat), money(o.cVat),
                    money(o.paid), Integer.toString(o.status), Integer.toString(o.deductDays),
                    money(o.deductAmt), money(o.deductVat), Integer.toString(o.days), Long.toString(o.cr),
                    o.applied(), LegacyDate.format(o.dateCleared)));
        }
        out.add(join("SYS", Integer.toString(ledger.nextBatch), Integer.toString(ledger.invoicesNotPosted),
                Integer.toString(ledger.paymentsEntered), String.valueOf(ledger.addressDelimiter).strip(),
                String.valueOf(ledger.openItemsChanged).strip()));
        return out;
    }

    private static OpenItem openItem(String[] f) {
        OpenItem o = new OpenItem();
        o.supplier = Fixed.text(f[1], 7);
        o.invoice = Long.parseLong(f[2]);
        o.date = date(f[3]);
        o.batchNumber = integer(f[4]);
        o.batchItem = integer(f[5]);
        o.type = integer(f[6]);
        o.ref = Fixed.text(f[7], 10);
        o.order = Fixed.text(f[8], 10);
        o.holdFlag = Fixed.text(f[9], 1);
        o.unapplied = Fixed.text(f[10], 1);
        o.pc = Fixed.signed(Fixed.money(f[11]), 7);
        o.net = Fixed.signed(Fixed.money(f[12]), 7);
        o.extra = Fixed.signed(Fixed.money(f[13]), 7);
        o.carriage = Fixed.signed(Fixed.money(f[14]), 7);
        o.vat = Fixed.signed(Fixed.money(f[15]), 7);
        o.discount = Fixed.signed(Fixed.money(f[16]), 7);
        o.eVat = Fixed.signed(Fixed.money(f[17]), 7);
        o.cVat = Fixed.signed(Fixed.money(f[18]), 7);
        o.paid = Fixed.signed(Fixed.money(f[19]), 7);
        o.status = integer(f[20]);
        o.deductDays = integer(f[21]);
        o.deductAmt = Fixed.signed(Fixed.money(f[22]), 3);
        o.deductVat = Fixed.signed(Fixed.money(f[23]), 3);
        o.days = integer(f[24]);
        o.cr = f[25].isBlank() ? 0 : Long.parseLong(f[25].trim());
        o.applied = Fixed.text(f[26], 1);
        o.dateCleared = date(f[27]);
        return o;
    }

    private static LocalDate date(String text) {
        if (text.isBlank()) {
            return null;
        }
        return LegacyDate.parse(text).orElseThrow(() -> new IllegalArgumentException("Bad date " + text));
    }

    private static int integer(String text) {
        return text.isBlank() ? 0 : Integer.parseInt(text.trim());
    }

    private static String money(BigDecimal value) {
        return value.setScale(2).toPlainString();
    }

    private static String[] fields(String line, int count) {
        String[] parts = line.split("\\|", -1);
        String[] f = Arrays.copyOf(parts, Math.max(count, parts.length));
        for (int i = 0; i < f.length; i++) {
            if (f[i] == null) {
                f[i] = "";
            }
        }
        return f;
    }

    private static String join(String... values) {
        StringJoiner j = new StringJoiner("|");
        for (String v : values) {
            j.add(v);
        }
        return j.toString();
    }
}
