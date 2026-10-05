package org.acas.sales.posting;

import java.math.BigDecimal;

/** Invoice header ({@code Invoice-Header} in slwsinv2.cob). */
public final class InvoiceHeader implements InvoiceRecord {

    public static final int TYPE_RECEIPT = 1;
    public static final int TYPE_INVOICE = 2;
    public static final int TYPE_CREDIT_NOTE = 3;
    public static final int TYPE_PROFORMA = 4;

    public int invoice;
    public String customer = Pic.text("", 7);
    public int date;
    public String order = Pic.text("", 10);
    public int type;
    public String ref = Pic.text("", 10);
    public String description = Pic.text("", 32);
    public BigDecimal pc = Money.ZERO;
    public BigDecimal net = Money.ZERO;
    public BigDecimal extra = Money.ZERO;
    public BigDecimal carriage = Money.ZERO;
    public BigDecimal vat = Money.ZERO;
    public BigDecimal discount = Money.ZERO;
    public BigDecimal eVat = Money.ZERO;
    public BigDecimal cVat = Money.ZERO;
    /** P = pending, I = invoiced, Z = applied. */
    public char status = ' ';
    public char statusP = ' ';
    /** L = invoice printed. */
    public char statusL = ' ';
    public char statusC = ' ';
    /** A = applied to account. */
    public char statusA = ' ';
    public char statusI = ' ';
    public int lines;
    public int deductDays;
    public BigDecimal deductAmt = Money.ZERO;
    public BigDecimal deductVat = Money.ZERO;
    public int days;
    public int cr;
    public char dayBookFlag = ' ';
    /** Z = analysed by the post extract. */
    public char update = ' ';

    @Override
    public InvoiceKey key() {
        return new InvoiceKey(invoice, 0);
    }

    boolean isApplied() {
        return status == 'Z';
    }

    boolean isAnalysed() {
        return update == 'Z';
    }

    @Override
    public InvoiceHeader copy() {
        InvoiceHeader c = new InvoiceHeader();
        c.invoice = invoice;
        c.customer = customer;
        c.date = date;
        c.order = order;
        c.type = type;
        c.ref = ref;
        c.description = description;
        c.pc = pc;
        c.net = net;
        c.extra = extra;
        c.carriage = carriage;
        c.vat = vat;
        c.discount = discount;
        c.eVat = eVat;
        c.cVat = cVat;
        c.status = status;
        c.statusP = statusP;
        c.statusL = statusL;
        c.statusC = statusC;
        c.statusA = statusA;
        c.statusI = statusI;
        c.lines = lines;
        c.deductDays = deductDays;
        c.deductAmt = deductAmt;
        c.deductVat = deductVat;
        c.days = days;
        c.cr = cr;
        c.dayBookFlag = dayBookFlag;
        c.update = update;
        return c;
    }
}
