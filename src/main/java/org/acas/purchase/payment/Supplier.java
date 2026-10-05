package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** The fields of a purchase ledger supplier record ({@code wspl.cob}) that payment entry uses. */
public final class Supplier {
    private final String key;
    private final String name;
    private final String address;
    private final BigDecimal current;
    private BigDecimal unapplied;

    public Supplier(String key, String name, String address, BigDecimal current, BigDecimal unapplied) {
        this.key = Fixed.text(key, 7);
        this.name = Fixed.text(name, 30);
        this.address = Fixed.text(address, 96);
        this.current = Fixed.signed(current, 8);
        this.unapplied = Fixed.signed(unapplied, 8);
    }

    Supplier copy() {
        return new Supplier(key, name, address, current, unapplied);
    }

    public String key() {
        return key;
    }

    public String name() {
        return name.stripTrailing();
    }

    public String address() {
        return address.stripTrailing();
    }

    public BigDecimal currentBalance() {
        return current;
    }

    public BigDecimal unappliedBalance() {
        return unapplied;
    }

    void consumeUnapplied(BigDecimal amount) {
        unapplied = Fixed.signed(unapplied.subtract(amount), 8);
    }

    /**
     * Splits the address the way pl080 displays it: three lines delimited by the ledger's address
     * delimiter, then the rest of the field, each cut to 36 characters.
     */
    public List<String> addressLines(char delimiter) {
        List<String> lines = new ArrayList<>(4);
        int pos = 0;
        for (int i = 0; i < 3; i++) {
            int end = address.indexOf(delimiter, pos);
            int stop = end < 0 ? address.length() : end;
            lines.add(cut(address.substring(Math.min(pos, address.length()), Math.max(pos, stop))));
            pos = end < 0 ? address.length() : end + 1;
        }
        lines.add(cut(address.substring(Math.min(pos, address.length()))));
        return lines;
    }

    private static String cut(String line) {
        return (line.length() > 36 ? line.substring(0, 36) : line).stripTrailing();
    }
}
