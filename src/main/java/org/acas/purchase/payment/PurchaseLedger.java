package org.acas.purchase.payment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * The state pl080 reads and writes: the supplier file ({@code purchled.dat}), the purchase open
 * item file ({@code openitm5.dat}) and the system record fields it uses ({@code wssystem.cob}).
 */
public final class PurchaseLedger {
    private final TreeMap<String, Supplier> suppliers = new TreeMap<>();
    private final TreeMap<OpenItemKey, OpenItem> openItems = new TreeMap<>();
    /** {@code BL-Next-Batch}. */
    int nextBatch;
    /** {@code P-Flag-I}: 1 while purchase invoices are waiting to be posted. */
    int invoicesNotPosted;
    /** {@code P-Flag-P}: set to 1 once payments have been entered. */
    int paymentsEntered;
    /** {@code PL-Delim}: separates the lines of a supplier address. */
    char addressDelimiter = ' ';
    /** {@code Oi-5-Flag}. */
    char openItemsChanged = ' ';

    public PurchaseLedger copy() {
        PurchaseLedger c = new PurchaseLedger();
        suppliers.forEach((k, s) -> c.suppliers.put(k, s.copy()));
        openItems.forEach((k, o) -> c.openItems.put(k, o.copy()));
        c.nextBatch = nextBatch;
        c.invoicesNotPosted = invoicesNotPosted;
        c.paymentsEntered = paymentsEntered;
        c.addressDelimiter = addressDelimiter;
        c.openItemsChanged = openItemsChanged;
        return c;
    }

    public Optional<Supplier> supplier(String key) {
        return Optional.ofNullable(suppliers.get(Fixed.text(key, 7)));
    }

    public List<Supplier> suppliers() {
        return List.copyOf(suppliers.values());
    }

    public List<OpenItem> openItems() {
        List<OpenItem> items = new ArrayList<>(openItems.size());
        openItems.values().forEach(o -> items.add(o.copy()));
        return items;
    }

    public List<OpenItem> openItems(String supplierKey) {
        String key = Fixed.text(supplierKey, 7);
        return openItems().stream().filter(o -> o.supplier.equals(key)).toList();
    }

    public int nextBatch() {
        return nextBatch;
    }

    public boolean invoicesNotPosted() {
        return invoicesNotPosted == 1;
    }

    public char addressDelimiter() {
        return addressDelimiter;
    }

    void put(Supplier supplier) {
        suppliers.put(supplier.key(), supplier);
    }

    /** {@code OTM5-Write}: does nothing if the key already exists, as the legacy write fails. */
    void write(OpenItem item) {
        openItems.putIfAbsent(item.key(), item.copy());
    }

    /** {@code OTM5-Rewrite}. */
    void rewrite(OpenItem item) {
        openItems.replace(item.key(), item.copy());
    }

    /** {@code OTM5-Read-Next} after a start at or after {@code from}. */
    Optional<OpenItem> readFrom(OpenItemKey from, boolean inclusive) {
        Map.Entry<OpenItemKey, OpenItem> e = inclusive ? openItems.ceilingEntry(from) : openItems.higherEntry(from);
        return e == null ? Optional.empty() : Optional.of(e.getValue().copy());
    }
}
