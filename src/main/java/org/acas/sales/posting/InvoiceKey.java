package org.acas.sales.posting;

import java.util.Comparator;

/** Invoice file primary key: invoice number plus item number (00 = header, 01.. = lines). */
public record InvoiceKey(int invoice, int item) implements Comparable<InvoiceKey> {

    private static final Comparator<InvoiceKey> ORDER =
            Comparator.comparingInt(InvoiceKey::invoice).thenComparingInt(InvoiceKey::item);

    @Override
    public int compareTo(InvoiceKey other) {
        return ORDER.compare(this, other);
    }
}
