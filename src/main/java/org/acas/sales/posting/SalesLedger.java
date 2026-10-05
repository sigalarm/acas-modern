package org.acas.sales.posting;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * The files sl055 works on, with indexed-file semantics: writes fail on a duplicate key,
 * rewrites fail on a missing key, and reads return copies.
 */
public final class SalesLedger {

    private final NavigableMap<InvoiceKey, InvoiceRecord> invoices = new TreeMap<>();
    private final NavigableMap<String, ValueRecord> values = new TreeMap<>();
    private final NavigableMap<String, AnalysisRecord> analysis = new TreeMap<>();
    private final List<OpenItem> openItems = new ArrayList<>();
    private BigDecimal invoicesThisMonth = Money.ZERO;
    private BigDecimal creditNotesThisMonth = Money.ZERO;

    public boolean writeInvoice(InvoiceRecord record) {
        return invoices.putIfAbsent(record.key(), record.copy()) == null;
    }

    boolean rewriteInvoice(InvoiceRecord record) {
        return invoices.replace(record.key(), record.copy()) != null;
    }

    Map.Entry<InvoiceKey, InvoiceRecord> firstInvoice() {
        return invoices.firstEntry();
    }

    Map.Entry<InvoiceKey, InvoiceRecord> invoiceAfter(InvoiceKey key) {
        return invoices.higherEntry(key);
    }

    Map.Entry<InvoiceKey, InvoiceRecord> invoiceNotLessThan(InvoiceKey key) {
        return invoices.ceilingEntry(key);
    }

    public boolean writeValue(ValueRecord record) {
        return values.putIfAbsent(record.code, record.copy()) == null;
    }

    boolean rewriteValue(ValueRecord record) {
        return values.replace(record.code, record.copy()) != null;
    }

    ValueRecord readValue(String code) {
        ValueRecord v = values.get(code);
        return v == null ? null : v.copy();
    }

    public boolean writeAnalysis(AnalysisRecord record) {
        return analysis.putIfAbsent(record.code(), record) == null;
    }

    AnalysisRecord readAnalysis(String code) {
        return analysis.get(code);
    }

    void addOpenItem(OpenItem item) {
        openItems.add(item);
    }

    public List<InvoiceRecord> invoices() {
        return invoices.values().stream().map(InvoiceRecord::copy).toList();
    }

    public List<ValueRecord> values() {
        return values.values().stream().map(ValueRecord::copy).toList();
    }

    public List<AnalysisRecord> analysis() {
        return List.copyOf(analysis.values());
    }

    public List<OpenItem> openItems() {
        return List.copyOf(openItems);
    }

    public BigDecimal invoicesThisMonth() {
        return invoicesThisMonth;
    }

    public BigDecimal creditNotesThisMonth() {
        return creditNotesThisMonth;
    }

    public void setMonthTotals(BigDecimal invoices, BigDecimal creditNotes) {
        invoicesThisMonth = Pic.signed(invoices, 8);
        creditNotesThisMonth = Pic.signed(creditNotes, 8);
    }

    void addToInvoicesThisMonth(BigDecimal amount) {
        invoicesThisMonth = Pic.signed(invoicesThisMonth.add(amount), 8);
    }

    void addToCreditNotesThisMonth(BigDecimal amount) {
        creditNotesThisMonth = Pic.signed(creditNotesThisMonth.add(amount), 8);
    }
}
