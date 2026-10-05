package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A purchase open item ({@code OI-Header} in {@code plwsoi.cob}, stored in {@code openitm5.dat}).
 * Type 2 records are supplier invoices, types 5 and 6 are payments. Dates are {@code null} where
 * the legacy field holds zero.
 */
public final class OpenItem {
    public static final int TYPE_INVOICE = 2;
    public static final int TYPE_PAYMENT = 5;
    public static final int TYPE_UNAPPLIED_ALLOCATION = 6;

    String supplier = Fixed.text("", 7);
    long invoice;
    LocalDate date;
    int batchNumber;
    int batchItem;
    int type;
    String ref = Fixed.text("", 10);
    String order = Fixed.text("", 10);
    String holdFlag = " ";
    String unapplied = " ";
    BigDecimal pc = Fixed.ZERO;
    /** Also {@code OI-Approp} (the appropriated amount) on payment records. */
    BigDecimal net = Fixed.ZERO;
    BigDecimal extra = Fixed.ZERO;
    BigDecimal carriage = Fixed.ZERO;
    BigDecimal vat = Fixed.ZERO;
    BigDecimal discount = Fixed.ZERO;
    BigDecimal eVat = Fixed.ZERO;
    BigDecimal cVat = Fixed.ZERO;
    BigDecimal paid = Fixed.ZERO;
    int status;
    int deductDays;
    BigDecimal deductAmt = Fixed.ZERO;
    BigDecimal deductVat = Fixed.ZERO;
    int days;
    long cr;
    String applied = " ";
    LocalDate dateCleared;

    OpenItem copy() {
        OpenItem c = new OpenItem();
        c.copyFrom(this);
        return c;
    }

    void copyFrom(OpenItem o) {
        supplier = o.supplier;
        invoice = o.invoice;
        date = o.date;
        batchNumber = o.batchNumber;
        batchItem = o.batchItem;
        type = o.type;
        ref = o.ref;
        order = o.order;
        holdFlag = o.holdFlag;
        unapplied = o.unapplied;
        pc = o.pc;
        net = o.net;
        extra = o.extra;
        carriage = o.carriage;
        vat = o.vat;
        discount = o.discount;
        eVat = o.eVat;
        cVat = o.cVat;
        paid = o.paid;
        status = o.status;
        deductDays = o.deductDays;
        deductAmt = o.deductAmt;
        deductVat = o.deductVat;
        days = o.days;
        cr = o.cr;
        applied = o.applied;
        dateCleared = o.dateCleared;
    }

    OpenItemKey key() {
        return new OpenItemKey(supplier, invoice);
    }

    /** Net + carriage + VAT + carriage VAT, as pl080 sums it into {@code work-net}. */
    BigDecimal gross() {
        return Fixed.signed(net.add(carriage).add(vat).add(cVat), 7);
    }

    public String supplier() {
        return supplier;
    }

    public long invoice() {
        return invoice;
    }

    public LocalDate date() {
        return date;
    }

    public int batchNumber() {
        return batchNumber;
    }

    public int batchItem() {
        return batchItem;
    }

    public int type() {
        return type;
    }

    public String ref() {
        return ref.stripTrailing();
    }

    public String order() {
        return order.stripTrailing();
    }

    public String holdFlag() {
        return holdFlag.stripTrailing();
    }

    public String unappliedFlag() {
        return unapplied.stripTrailing();
    }

    public BigDecimal pc() {
        return pc;
    }

    public BigDecimal net() {
        return net;
    }

    public BigDecimal extra() {
        return extra;
    }

    public BigDecimal carriage() {
        return carriage;
    }

    public BigDecimal vat() {
        return vat;
    }

    public BigDecimal discount() {
        return discount;
    }

    public BigDecimal eVat() {
        return eVat;
    }

    public BigDecimal cVat() {
        return cVat;
    }

    public BigDecimal paid() {
        return paid;
    }

    public int status() {
        return status;
    }

    public boolean isClosed() {
        return status == 1;
    }

    public int deductDays() {
        return deductDays;
    }

    public BigDecimal deductAmt() {
        return deductAmt;
    }

    public BigDecimal deductVat() {
        return deductVat;
    }

    public int days() {
        return days;
    }

    public long cr() {
        return cr;
    }

    public String applied() {
        return applied.stripTrailing();
    }

    public LocalDate dateCleared() {
        return dateCleared;
    }
}
