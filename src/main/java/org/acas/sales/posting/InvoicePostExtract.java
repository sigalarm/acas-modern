package org.acas.sales.posting;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Sales invoice post extract: the first phase of the sales invoice posting pipeline, ported
 * from ACAS {@code sales/sl055.cbl}.
 *
 * <p>For every unanalysed line item, adds the line value to the sales analysis value record for
 * its product analysis code and that code's parent. For every printed, unapplied invoice or
 * credit note, writes an open item for sl060 to post, adds the invoice to the sales ledger month
 * totals and marks it applied. Finally stores the VAT, receipt-VAT, carriage and deduction
 * totals under the special analysis codes {@code Svo}, {@code ?vp}, {@code ?zc} and
 * {@code ?zd}.
 *
 * <p>Behaviour, including its defects, matches the legacy program as observed by the parity
 * fixtures under {@code src/test/resources/parity}. See README.md for the defects.
 */
public final class InvoicePostExtract {

    static final String EMERGENCY_NAME = "Emergency Name";

    /** Outcome of a run. */
    public record Result(boolean unprintedInvoicesSkipped) {
    }

    private static final class Totals {
        int count;
        BigDecimal value = Money.ZERO;

        void add(BigDecimal amount) {
            count = Pic.signedInt(count + 1L, 5);
            value = Pic.signed(value.add(amount), 7);
        }
    }

    private enum Next {
        READ_NEXT,
        SKIP_INVOICE
    }

    private final SalesLedger ledger;
    private final Totals vat = new Totals();
    private final Totals receiptVat = new Totals();
    private final Totals carriage = new Totals();
    private final Totals deductions = new Totals();
    private ValueRecord va = ValueRecord.blank();
    private boolean unprintedInvoicesSkipped;

    private InvoicePostExtract(SalesLedger ledger) {
        this.ledger = ledger;
    }

    public static Result run(SalesLedger ledger) {
        return new InvoicePostExtract(ledger).run();
    }

    private Result run() {
        Map.Entry<InvoiceKey, InvoiceRecord> entry = ledger.firstInvoice();
        while (entry != null) {
            InvoiceRecord record = entry.getValue().copy();
            Next next = record instanceof InvoiceHeader header
                    ? analyseHeader(header)
                    : analyseLine((InvoiceLine) record);
            if (next == Next.SKIP_INVOICE) {
                entry = ledger.invoiceNotLessThan(new InvoiceKey(record.key().invoice() + 1, 0));
            } else {
                entry = ledger.invoiceAfter(record.key());
            }
        }
        va.code = "Svo";
        storeSpecial(vat);
        va.setGroup("vp");
        storeSpecial(receiptVat);
        va.setGroup("zc");
        storeSpecial(carriage);
        va.setGroup("zd");
        storeSpecial(deductions);
        return new Result(unprintedInvoicesSkipped);
    }

    private Next analyseLine(InvoiceLine line) {
        if (line.isAnalysed() || line.isComment()) {
            return Next.READ_NEXT;
        }
        BigDecimal amount = line.type == '3' ? line.net.negate() : line.net;
        boolean exists = readValue("S" + line.pa);
        if (!exists) {
            createValue();
        }
        va.accumulate(1, amount);
        if (exists) {
            ledger.rewriteValue(va);
        } else {
            ledger.writeValue(va);
        }
        if (va.second() == ' ') {
            return Next.READ_NEXT;
        }
        va.clearSecond();
        if (!readValue(va.code)) {
            return Next.READ_NEXT;
        }
        va.accumulate(1, amount);
        ledger.rewriteValue(va);
        line.update = 'Z';
        ledger.rewriteInvoice(line);
        return Next.READ_NEXT;
    }

    private Next analyseHeader(InvoiceHeader header) {
        if (header.isAnalysed() && header.isApplied()) {
            return Next.READ_NEXT;
        }
        if (header.type == InvoiceHeader.TYPE_PROFORMA) {
            return Next.SKIP_INVOICE;
        }
        if (header.status == 'P' || header.statusL != 'L') {
            unprintedInvoicesSkipped = true;
            return Next.SKIP_INVOICE;
        }
        extract(header);
        if (header.isAnalysed()) {
            ledger.rewriteInvoice(header);
            return Next.READ_NEXT;
        }
        BigDecimal headerVat = signedForType(
                Pic.signed(header.cVat.add(header.vat).add(header.eVat), 7), header);
        if (headerVat.signum() != 0) {
            if (header.type == InvoiceHeader.TYPE_RECEIPT) {
                receiptVat.add(headerVat);
            } else {
                vat.add(headerVat);
            }
        }
        BigDecimal headerCarriage = signedForType(header.carriage, header);
        if (headerCarriage.signum() != 0) {
            carriage.add(headerCarriage);
        }
        BigDecimal headerDeduction = signedForType(header.deductAmt, header);
        if (headerDeduction.signum() != 0) {
            deductions.add(headerDeduction);
        }
        header.update = 'Z';
        ledger.rewriteInvoice(header);
        return Next.READ_NEXT;
    }

    private static BigDecimal signedForType(BigDecimal amount, InvoiceHeader header) {
        return header.type == InvoiceHeader.TYPE_CREDIT_NOTE ? amount.negate() : amount;
    }

    private void extract(InvoiceHeader header) {
        if (header.isApplied()) {
            return;
        }
        OpenItem item = new OpenItem();
        item.pc = header.pc;
        item.invoice = header.invoice;
        item.customer = header.customer;
        item.date = header.date;
        item.description = Pic.text(header.order, 25);
        item.net = header.net;
        item.extra = header.extra;
        item.carriage = header.carriage;
        item.vat = header.vat;
        item.cVat = header.cVat;
        item.eVat = header.eVat;
        item.discount = header.discount;
        item.deductAmt = header.deductAmt;
        item.deductVat = header.deductVat;
        item.deductDays = header.deductDays;
        item.type = header.type;
        if (header.type == InvoiceHeader.TYPE_CREDIT_NOTE) {
            item.deductAmt = item.deductAmt.negate();
            item.deductVat = item.deductVat.negate();
            item.net = item.net.negate();
            item.extra = item.extra.negate();
            item.carriage = item.carriage.negate();
            item.vat = item.vat.negate();
            item.cVat = item.cVat.negate();
            item.eVat = item.eVat.negate();
            item.discount = item.discount.negate();
        }
        item.cr = header.cr;
        if (header.type != InvoiceHeader.TYPE_RECEIPT) {
            BigDecimal amount = Pic.signed(header.net.add(header.extra).add(header.carriage)
                    .add(header.discount).add(header.vat).add(header.cVat).add(header.eVat)
                    .add(header.deductAmt).add(header.deductVat), 7);
            if (header.type == InvoiceHeader.TYPE_INVOICE) {
                ledger.addToInvoicesThisMonth(amount);
            } else if (header.type == InvoiceHeader.TYPE_CREDIT_NOTE) {
                ledger.addToCreditNotesThisMonth(amount);
            }
        }
        header.status = 'Z';
        header.statusA = 'A';
        ledger.addOpenItem(item);
    }

    private void storeSpecial(Totals totals) {
        boolean exists = readValue(va.code);
        if (!exists) {
            createValue();
        }
        va.accumulate(totals.count, totals.value);
        if (exists) {
            ledger.rewriteValue(va);
        } else {
            ledger.writeValue(va);
        }
        va.clearSecond();
        if (!readValue(va.code)) {
            return;
        }
        va.accumulate(totals.count, totals.value);
        ledger.rewriteValue(va);
    }

    /**
     * Indexed read into the value working record. As in the legacy file handler (acas013), a
     * failed read leaves the record blank, key included.
     */
    private boolean readValue(String code) {
        ValueRecord found = ledger.readValue(code);
        va = found == null ? ValueRecord.blank() : found;
        return found != null;
    }

    /** Builds a new value record from the analysis file for the current code (sl055 db000). */
    private void createValue() {
        AnalysisRecord analysis = ledger.readAnalysis(va.code);
        while (analysis == null) {
            createEmergencyAnalysis();
            analysis = ledger.readAnalysis(va.code);
        }
        va = ValueRecord.fromAnalysis(analysis);
        if (va.second() == ' ') {
            return;
        }
        String childCode = va.code;
        va.clearSecond();
        AnalysisRecord parent = ledger.readAnalysis(va.code);
        if (parent == null) {
            return;
        }
        va = ValueRecord.fromAnalysis(parent);
        ledger.writeValue(va);
        va.code = childCode;
        AnalysisRecord child = ledger.readAnalysis(childCode);
        if (child != null) {
            va = ValueRecord.fromAnalysis(child);
        }
    }

    /** Placeholder analysis record for an unknown code and its parent (sl055 db010). */
    private void createEmergencyAnalysis() {
        AnalysisRecord emergency = new AnalysisRecord(va.code, 0, EMERGENCY_NAME, "");
        ledger.writeAnalysis(emergency);
        if (emergency.code().charAt(2) != ' ') {
            ledger.writeAnalysis(new AnalysisRecord(
                    emergency.code().substring(0, 2), 0, EMERGENCY_NAME, ""));
        }
    }
}
