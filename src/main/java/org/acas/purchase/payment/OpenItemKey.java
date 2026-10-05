package org.acas.purchase.payment;

import java.util.Comparator;

/** {@code oi5-key}: supplier {@code x(7)} then invoice {@code 9(8)}, in ISAM key order. */
record OpenItemKey(String supplier, long invoice) implements Comparable<OpenItemKey> {
    private static final Comparator<OpenItemKey> ORDER =
            Comparator.comparing(OpenItemKey::supplier).thenComparingLong(OpenItemKey::invoice);

    OpenItemKey {
        supplier = Fixed.text(supplier, 7);
    }

    @Override
    public int compareTo(OpenItemKey other) {
        return ORDER.compare(this, other);
    }
}
