package org.acas.sales.posting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Pipe-delimited text format shared with the legacy harness ({@code legacy-harness/sl055h.cbl}).
 * Fixtures are loaded into a {@link SalesLedger}; ledgers are dumped in the order the indexed
 * files are read back (key order), followed by open items in write order and the month totals.
 */
public final class ParityFormat {

    private ParityFormat() {
    }

    public static SalesLedger parse(List<String> lines) {
        SalesLedger ledger = new SalesLedger();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\\|", -1);
            boolean written = switch (f[0]) {
                case "SYS" -> {
                    ledger.setMonthTotals(money(f, 1), money(f, 2));
                    yield true;
                }
                case "ANAL" -> ledger.writeAnalysis(
                        new AnalysisRecord(text(f, 1), integer(f, 2), text(f, 3), text(f, 4)));
                case "VAL" -> ledger.writeValue(parseValue(f));
                case "IH" -> ledger.writeInvoice(parseHeader(f));
                case "IL" -> ledger.writeInvoice(parseLine(f));
                default -> throw new IllegalArgumentException("unknown fixture record: " + line);
            };
            if (!written) {
                throw new IllegalArgumentException("duplicate key: " + line);
            }
        }
        return ledger;
    }

    private static ValueRecord parseValue(String[] f) {
        ValueRecord v = new ValueRecord(text(f, 1), integer(f, 2), text(f, 3), text(f, 4));
        v.tThis = integer(f, 5);
        v.tLast = integer(f, 6);
        v.tYear = integer(f, 7);
        v.vThis = money(f, 8);
        v.vLast = money(f, 9);
        v.vYear = money(f, 10);
        return v;
    }

    private static InvoiceHeader parseHeader(String[] f) {
        InvoiceHeader h = new InvoiceHeader();
        h.invoice = integer(f, 1);
        h.customer = Pic.text(text(f, 2), 7);
        h.date = integer(f, 3);
        h.order = Pic.text(text(f, 4), 10);
        h.type = integer(f, 5);
        h.ref = Pic.text(text(f, 6), 10);
        h.description = Pic.text(text(f, 7), 32);
        h.pc = money(f, 8);
        h.net = money(f, 9);
        h.extra = money(f, 10);
        h.carriage = money(f, 11);
        h.vat = money(f, 12);
        h.discount = money(f, 13);
        h.eVat = money(f, 14);
        h.cVat = money(f, 15);
        h.status = Pic.flag(text(f, 16));
        h.statusP = Pic.flag(text(f, 17));
        h.statusL = Pic.flag(text(f, 18));
        h.statusC = Pic.flag(text(f, 19));
        h.statusA = Pic.flag(text(f, 20));
        h.statusI = Pic.flag(text(f, 21));
        h.lines = integer(f, 22);
        h.deductDays = integer(f, 23);
        h.deductAmt = money(f, 24);
        h.deductVat = money(f, 25);
        h.days = integer(f, 26);
        h.cr = integer(f, 27);
        h.dayBookFlag = Pic.flag(text(f, 28));
        h.update = Pic.flag(text(f, 29));
        return h;
    }

    private static InvoiceLine parseLine(String[] f) {
        InvoiceLine l = new InvoiceLine();
        l.invoice = integer(f, 1);
        l.line = integer(f, 2);
        l.product = Pic.text(text(f, 3), 13);
        l.pa = Pic.text(text(f, 4), 2);
        l.qty = integer(f, 5);
        l.type = Pic.flag(text(f, 6));
        l.description = Pic.text(text(f, 7), 32);
        l.net = money(f, 8);
        l.unit = money(f, 9);
        l.discount = money(f, 10);
        l.vat = money(f, 11);
        l.vatCode = integer(f, 12);
        l.update = Pic.flag(text(f, 13));
        l.backOrdered = Pic.flag(text(f, 14));
        return l;
    }

    public static List<String> dump(SalesLedger ledger) {
        List<String> out = new ArrayList<>();
        for (InvoiceRecord record : ledger.invoices()) {
            if (record instanceof InvoiceHeader h) {
                out.add(row("IH", h.invoice, h.customer, h.date, h.order, h.type,
                        h.ref, h.description, h.pc, h.net, h.extra, h.carriage, h.vat,
                        h.discount, h.eVat, h.cVat, h.status, h.statusP, h.statusL, h.statusC,
                        h.statusA, h.statusI, h.lines, h.deductDays, h.deductAmt, h.deductVat,
                        h.days, h.cr, h.dayBookFlag, h.update));
            } else {
                InvoiceLine l = (InvoiceLine) record;
                out.add(row("IL", l.invoice, l.line, l.product, l.pa, l.qty,
                        l.type, l.description, l.net, l.unit, l.discount, l.vat, l.vatCode,
                        l.update, l.backOrdered));
            }
        }
        for (ValueRecord v : ledger.values()) {
            out.add(row("VAL", v.code, v.gl, v.desc, v.print, v.tThis, v.tLast, v.tYear,
                    v.vThis, v.vLast, v.vYear));
        }
        for (AnalysisRecord a : ledger.analysis()) {
            out.add(row("ANAL", a.code(), a.gl(), a.desc(), a.print()));
        }
        for (OpenItem o : ledger.openItems()) {
            out.add(row("OI", o.customer, o.invoice, o.date, o.batchNos, o.batchItem, o.type,
                    o.description, o.holdFlag, o.unapplied, o.pc, o.net, o.extra, o.carriage,
                    o.vat, o.discount, o.eVat, o.cVat, o.paid, o.status, o.deductDays,
                    o.deductAmt, o.deductVat, o.days, o.cr, o.applied, o.dateCleared));
        }
        out.add(row("SYS", ledger.invoicesThisMonth(), ledger.creditNotesThisMonth()));
        return out;
    }

    private static String row(Object... fields) {
        StringJoiner joiner = new StringJoiner("|");
        for (Object field : fields) {
            joiner.add(format(field));
        }
        return joiner.toString();
    }

    private static String format(Object field) {
        if (field instanceof BigDecimal money) {
            return money.setScale(2).toPlainString();
        }
        return field.toString().stripTrailing();
    }

    private static String text(String[] f, int i) {
        return i < f.length ? f[i] : "";
    }

    private static int integer(String[] f, int i) {
        String s = text(f, i).trim();
        return s.isEmpty() ? 0 : Integer.parseInt(s);
    }

    private static BigDecimal money(String[] f, int i) {
        String s = text(f, i).trim();
        return s.isEmpty() ? Money.ZERO : new BigDecimal(s).setScale(2);
    }
}
