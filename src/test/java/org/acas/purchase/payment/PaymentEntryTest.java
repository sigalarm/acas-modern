package org.acas.purchase.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** pl080 business rules, as confirmed in purchase/pl080.cbl and the captured legacy runs. */
class PaymentEntryTest {
    private static final LocalDate PAY_DATE = LocalDate.of(2026, 10, 5);

    private static PurchaseLedger ledger(String sys, String... items) {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(sys);
        lines.add("SUP|ACME001|Acme|1 High St;Leeds|0|50.00");
        lines.add("SUP|BETA002|Beta|York|0|0");
        lines.addAll(List.of(items));
        return LedgerFormat.parse(lines);
    }

    private static String invoice(long number, String date, String net, String vat, int deductDays, String deduct) {
        return "OI5|ACME001|" + number + "|" + date + "|0|0|2|INV" + number + "|PO|||0|" + net + "|0|0|" + vat
                + "|0|0|0|0|0|" + deductDays + "|" + deduct + "|0|30|0||";
    }

    private static BigDecimal m(String v) {
        return new BigDecimal(v);
    }

    private static OpenItem item(PurchaseLedger ledger, long invoice) {
        return ledger.openItems().stream().filter(o -> o.invoice() == invoice).findFirst().orElseThrow();
    }

    @Test
    void refusesEntryWhileInvoicesAreNotPosted() {
        PaymentEntryException e = assertThrows(PaymentEntryException.class,
                () -> PaymentEntry.open(ledger("SYS|4|1|0|;")));
        assertEquals(PaymentEntryException.Reason.INVOICES_NOT_POSTED, e.reason());
    }

    @Test
    void parsesDatesLikeMaps04() {
        assertEquals(PAY_DATE, LegacyDate.parse("05/10/2026").orElseThrow());
        assertEquals(PAY_DATE, LegacyDate.parse("05.10.2026").orElseThrow());
        assertEquals(PAY_DATE, LegacyDate.parse("05-10-2026").orElseThrow());
        assertTrue(LegacyDate.parse("31/02/2026").isEmpty());
        assertTrue(LegacyDate.parse("5/10/2026").isEmpty());
        assertTrue(LegacyDate.parse("05/13/2026").isEmpty());
        assertTrue(LegacyDate.parse("").isEmpty());
    }

    @Test
    void looksSuppliersUpUpperCased() {
        PaymentEntry entry = PaymentEntry.open(ledger("SYS|4|0|0|;"));
        assertEquals("Acme", entry.supplier("acme001").name());
        PaymentEntryException e = assertThrows(PaymentEntryException.class, () -> entry.supplier("NOSUCH1"));
        assertEquals(PaymentEntryException.Reason.UNKNOWN_SUPPLIER, e.reason());
    }

    @Test
    void takesSettlementDiscountInsideTheTerm() {
        PurchaseLedger ledger = ledger("SYS|7|0|0|;", invoice(100, "01/09/2026", "100.00", "20.00", 60, "2.40"));
        PaymentEntry entry = PaymentEntry.open(ledger);
        Appropriation a = entry.startPayment(PAY_DATE, entry.supplier("ACME001"), m("200.00"));

        OfferedLine line = a.next().orElseThrow();
        assertEquals(m("120.00"), line.outstanding());
        assertEquals(m("2.40"), line.discount());
        assertEquals(m("117.60"), line.amountDue());
        assertEquals(m("117.60"), line.proposal());
        assertEquals(LineOutcome.APPLIED, a.pay(line.proposal()));
        assertTrue(a.lineCleared());
        assertTrue(a.next().isEmpty());
        OpenItem payment = a.finish();

        assertEquals(7001, payment.invoice());
        assertEquals(OpenItem.TYPE_PAYMENT, payment.type());
        assertEquals(m("117.60"), payment.net());
        assertEquals(m("2.40"), payment.deductAmt());
        OpenItem paid = item(ledger, 100);
        assertEquals(m("97.60"), paid.net());
        assertEquals(PAY_DATE, paid.dateCleared());
    }

    @Test
    void proposalIsCappedByThePaymentValue() {
        PurchaseLedger ledger = ledger("SYS|7|0|0|;", invoice(100, "01/08/2026", "100.00", "20.00", 0, "0"));
        PaymentEntry entry = PaymentEntry.open(ledger);
        Appropriation a = entry.startPayment(PAY_DATE, entry.supplier("ACME001"), m("30.00"));
        OfferedLine line = a.next().orElseThrow();
        assertEquals(m("30.00"), line.proposal());
        assertEquals(LineOutcome.APPLIED, a.pay(line.proposal()));
        assertFalse(a.lineCleared());
    }

    @Test
    void reoffersTheLineWhenThePaymentIsTooHigh() {
        PurchaseLedger ledger = ledger("SYS|7|0|0|;", invoice(100, "01/08/2026", "50.00", "10.00", 0, "0"));
        PaymentEntry entry = PaymentEntry.open(ledger);
        Appropriation a = entry.startPayment(PAY_DATE, entry.supplier("ACME001"), m("500.00"));
        OfferedLine line = a.next().orElseThrow();
        assertEquals(LineOutcome.TOO_HIGH, a.pay(m("70.00")));
        assertEquals(line, a.next().orElseThrow());
        assertEquals(Fixed.ZERO, a.appropriated());
        assertEquals(LineOutcome.TOO_HIGH, a.pay(m("600.00")));
        assertEquals(LineOutcome.NO_CHANGE, a.pay(BigDecimal.ZERO));
    }

    @Test
    void lateDiscountAsksToSettleAndAnsweringNoStillClosesTheInvoice() {
        PurchaseLedger ledger = ledger("SYS|7|0|0|;",
                invoice(100, "01/08/2026", "100.00", "20.00", 10, "2.40"),
                invoice(101, "01/08/2026", "100.00", "20.00", 10, "2.40"));
        PaymentEntry entry = PaymentEntry.open(ledger);
        Appropriation a = entry.startPayment(PAY_DATE, entry.supplier("ACME001"), m("500.00"));

        assertEquals(m("0.00"), a.next().orElseThrow().discount());
        assertEquals(LineOutcome.SETTLE_PROMPT, a.pay(m("117.60")));
        a.settle(false);
        a.next().orElseThrow();
        assertEquals(LineOutcome.SETTLE_PROMPT, a.pay(m("117.60")));
        a.settle(true);
        a.next();
        OpenItem payment = a.finish();

        OpenItem no = item(ledger, 100);
        OpenItem yes = item(ledger, 101);
        assertTrue(no.isClosed());
        assertEquals(m("2.40"), no.deductAmt());
        assertTrue(yes.isClosed());
        assertEquals(m("97.60"), yes.net());
        assertEquals(m("2.40"), payment.deductAmt());
    }

    @Test
    void allocatesUnappliedBalance() {
        PurchaseLedger ledger = ledger("SYS|0|0|0|;", invoice(100, "01/08/2026", "100.00", "20.00", 0, "0"));
        PaymentEntry entry = PaymentEntry.open(ledger);
        Supplier acme = entry.supplier("ACME001");
        PaymentEntryException e = assertThrows(PaymentEntryException.class,
                () -> entry.startUnappliedAllocation(PAY_DATE, acme, m("60.00")));
        assertEquals(PaymentEntryException.Reason.EXCEEDS_UNAPPLIED, e.reason());
        assertThrows(PaymentEntryException.class,
                () -> entry.startUnappliedAllocation(PAY_DATE, entry.supplier("BETA002"), m("1.00")));

        Appropriation a = entry.startUnappliedAllocation(PAY_DATE, acme, m("30.00"));
        assertEquals(m("20.00"), acme.unappliedBalance());
        a.pay(a.next().orElseThrow().proposal());
        a.next();
        OpenItem record = a.finish();
        assertEquals(OpenItem.TYPE_UNAPPLIED_ALLOCATION, record.type());
        assertEquals(1001, record.invoice());
        assertEquals(Fixed.ZERO, entry.batchTotal());
    }

    @Test
    void numbersPaymentsWithinTheBatchAndClosesIt() {
        PurchaseLedger ledger = ledger("SYS|0|0|0|;");
        PaymentEntry entry = PaymentEntry.open(ledger);
        assertEquals(1, entry.batchNumber());
        for (int i = 1; i <= 3; i++) {
            Appropriation a = entry.startPayment(PAY_DATE, entry.supplier("BETA002"), m("10.00"));
            assertTrue(a.next().isEmpty());
            assertEquals(1000 + i, a.finish().invoice());
        }
        assertEquals(3, entry.itemCount());
        assertEquals(m("30.00"), entry.batchTotal());
        entry.close();
        assertEquals(2, ledger.nextBatch());
        assertThrows(PaymentEntryException.class, () -> entry.startPayment(PAY_DATE, entry.supplier("BETA002"),
                m("1.00")));
    }

    @Test
    void batchHoldsAtMost999Payments() {
        PaymentEntry entry = PaymentEntry.open(ledger("SYS|5|0|0|;"));
        Supplier beta = entry.supplier("BETA002");
        for (int i = 0; i < PaymentEntry.MAX_ITEMS; i++) {
            Appropriation a = entry.startPayment(PAY_DATE, beta, m("1.00"));
            a.next();
            a.finish();
        }
        assertTrue(entry.isFull());
        PaymentEntryException e = assertThrows(PaymentEntryException.class,
                () -> entry.startPayment(PAY_DATE, beta, m("1.00")));
        assertEquals(PaymentEntryException.Reason.BATCH_FULL, e.reason());
    }

    @Test
    void rejectsInvalidAmounts() {
        PaymentEntry entry = PaymentEntry.open(ledger("SYS|5|0|0|;"));
        Supplier beta = entry.supplier("BETA002");
        for (String bad : List.of("0", "-1.00", "10000000.00", "1.001")) {
            PaymentEntryException e = assertThrows(PaymentEntryException.class,
                    () -> entry.startPayment(PAY_DATE, beta, m(bad)));
            assertEquals(PaymentEntryException.Reason.INVALID_AMOUNT, e.reason());
        }
    }

    @Test
    void paymentRecordKeepsReferenceFieldsOfTheLastItemRead() {
        PurchaseLedger ledger = ledger("SYS|3|0|0|;", invoice(100, "01/08/2026", "50.00", "10.00", 0, "0"));
        PaymentEntry entry = PaymentEntry.open(ledger);
        Appropriation a = entry.startPayment(PAY_DATE, entry.supplier("ACME001"), m("60.00"));
        a.pay(a.next().orElseThrow().proposal());
        a.next();
        OpenItem record = a.finish();
        assertEquals("INV100", record.ref());
        assertEquals(PAY_DATE, record.dateCleared());
    }
}
