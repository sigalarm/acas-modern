package org.acas.sales.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class InvoicePostExtractTest {

    private static final List<String> CONFIGURED = List.of(
            "SYS|0.00|0.00",
            "ANAL|SA |4000|Sales A|SA",
            "ANAL|SA1|4001|Sales A1|SA1",
            "ANAL|Sv |2200|VAT|Sv",
            "ANAL|Svo|2201|Output VAT|Svo",
            "ANAL|Svp|2202|Receipt VAT|Svp",
            "ANAL|Sz |4900|Other|Sz",
            "ANAL|Szc|4901|Carriage|Szc",
            "ANAL|Szd|4902|Deductions|Szd",
            "VAL|SA |4000|Sales A|SA|0|0|0|0.00|0.00|0.00",
            "VAL|SA1|4001|Sales A1|SA1|0|0|0|0.00|0.00|0.00",
            "VAL|Sv |2200|VAT|Sv|0|0|0|0.00|0.00|0.00",
            "VAL|Svo|2201|Output VAT|Svo|0|0|0|0.00|0.00|0.00",
            "VAL|Svp|2202|Receipt VAT|Svp|0|0|0|0.00|0.00|0.00",
            "VAL|Sz |4900|Other|Sz|0|0|0|0.00|0.00|0.00",
            "VAL|Szc|4901|Carriage|Szc|0|0|0|0.00|0.00|0.00",
            "VAL|Szd|4902|Deductions|Szd|0|0|0|0.00|0.00|0.00");

    private static SalesLedger ledger(String... invoiceRecords) {
        return ParityFormat.parse(
                java.util.stream.Stream.concat(CONFIGURED.stream(), List.of(invoiceRecords).stream())
                        .toList());
    }

    private static ValueRecord value(SalesLedger ledger, String code) {
        return ledger.values().stream().filter(v -> v.code.equals(code)).findFirst().orElseThrow();
    }

    @Test
    void creditNoteIsNegatedOnTheOpenItemButCountedPositiveInMonthTotals() {
        SalesLedger ledger = ledger(
                "IH|1|CUS0011|20260901|ORD|3|R|Credit|0.00|30.00|0.00|2.00|6.00|0.00|0.00|0.00"
                        + "|I|P|L||||1|0|1.00|0.20|30|0||",
                "IL|1|1|P|A1|1|3|Return|30.00|30.00|0.00|6.00|1||");

        InvoicePostExtract.run(ledger);

        OpenItem item = ledger.openItems().get(0);
        assertEquals(new BigDecimal("-30.00"), item.net);
        assertEquals(new BigDecimal("-6.00"), item.vat);
        assertEquals(new BigDecimal("-1.00"), item.deductAmt);
        assertEquals(new BigDecimal("39.20"), ledger.creditNotesThisMonth());
        assertEquals(new BigDecimal("-30.00"), value(ledger, "SA1").vThis);
        assertEquals(new BigDecimal("-30.00"), value(ledger, "SA ").vThis);
        assertEquals(new BigDecimal("-6.00"), value(ledger, "Svo").vThis);
        assertEquals(new BigDecimal("-2.00"), value(ledger, "Szc").vThis);
    }

    @Test
    void postedInvoiceIsMarkedAppliedAndAnalysed() {
        SalesLedger ledger = ledger(
                "IH|1|CUS0011|20260901|ORD|2|R|Inv|0.00|10.00|0.00|0.00|2.00|0.00|0.00|0.00"
                        + "|I|P|L||||1|0|0.00|0.00|30|0||",
                "IL|1|1|P|A1|1|1|Line|10.00|10.00|0.00|2.00|1||");

        InvoicePostExtract.run(ledger);

        InvoiceHeader header = (InvoiceHeader) ledger.invoices().get(0);
        InvoiceLine line = (InvoiceLine) ledger.invoices().get(1);
        assertEquals('Z', header.status);
        assertEquals('A', header.statusA);
        assertEquals('Z', header.update);
        assertEquals('Z', line.update);
    }

    @Test
    void rerunOverFullyConfiguredLedgerChangesNothing() {
        SalesLedger ledger = ledger(
                "IH|1|CUS0011|20260901|ORD|2|R|Inv|0.00|10.00|0.00|1.00|2.00|0.00|0.00|0.00"
                        + "|I|P|L||||1|0|0.00|0.00|30|0||",
                "IL|1|1|P|A1|1|1|Line|10.00|10.00|0.00|2.00|1||");
        InvoicePostExtract.run(ledger);
        List<String> afterFirstRun = ParityFormat.dump(ledger);

        InvoicePostExtract.run(ledger);

        assertEquals(afterFirstRun, ParityFormat.dump(ledger));
    }

    @Test
    void unprintedInvoiceIsSkippedAndReported() {
        SalesLedger ledger = ledger(
                "IH|1|CUS0011|20260901|ORD|2|R|Inv|0.00|10.00|0.00|0.00|2.00|0.00|0.00|0.00"
                        + "|I|P|||||1|0|0.00|0.00|30|0||",
                "IL|1|1|P|A1|1|1|Line|10.00|10.00|0.00|2.00|1||");

        InvoicePostExtract.Result result = InvoicePostExtract.run(ledger);

        assertTrue(result.unprintedInvoicesSkipped());
        assertTrue(ledger.openItems().isEmpty());
        assertEquals(' ', ((InvoiceLine) ledger.invoices().get(1)).update);
    }

    @Test
    void printedInvoicesDoNotRaiseTheWarning() {
        SalesLedger ledger = ledger(
                "IH|1|CUS0011|20260901|ORD|2|R|Inv|0.00|10.00|0.00|0.00|2.00|0.00|0.00|0.00"
                        + "|I|P|L||||0|0|0.00|0.00|30|0||");

        assertFalse(InvoicePostExtract.run(ledger).unprintedInvoicesSkipped());
    }

    /** Legacy defect preserved for parity: see README.md, "Legacy defects". */
    @Test
    void legacyDefectLineWithoutValueRecordIsPostedToBlankCode() {
        SalesLedger ledger = ledger(
                "IH|1|CUS0011|20260901|ORD|2|R|Inv|0.00|10.00|0.00|0.00|0.00|0.00|0.00|0.00"
                        + "|I|P|L||||1|0|0.00|0.00|30|0||",
                "IL|1|1|P|Q1|1|1|Unknown code|10.00|10.00|0.00|0.00|1||");

        InvoicePostExtract.run(ledger);

        ValueRecord blank = value(ledger, "   ");
        assertEquals(InvoicePostExtract.EMERGENCY_NAME, blank.desc.strip());
        assertEquals(new BigDecimal("10.00"), blank.vThis);
        assertEquals(' ', ((InvoiceLine) ledger.invoices().get(1)).update);
    }
}
