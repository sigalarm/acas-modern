package org.acas.sales.posting;

import java.math.BigDecimal;

/** Invoice line item ({@code Invoice-Line} in slwsinv2.cob). */
public final class InvoiceLine implements InvoiceRecord {

    public int invoice;
    public int line;
    public String product = Pic.text("", 13);
    /** Product analysis code; the value-file key is {@code "S" + pa}. */
    public String pa = Pic.text("", 2);
    public int qty;
    /** '3' = credit-note line. */
    public char type = ' ';
    public String description = Pic.text("", 32);
    public BigDecimal net = Money.ZERO;
    public BigDecimal unit = Money.ZERO;
    public BigDecimal discount = Money.ZERO;
    public BigDecimal vat = Money.ZERO;
    public int vatCode;
    /** Z = analysed by the post extract. */
    public char update = ' ';
    public char backOrdered = ' ';

    @Override
    public InvoiceKey key() {
        return new InvoiceKey(invoice, line);
    }

    boolean isAnalysed() {
        return update == 'Z';
    }

    boolean isComment() {
        return product.startsWith("/");
    }

    @Override
    public InvoiceLine copy() {
        InvoiceLine c = new InvoiceLine();
        c.invoice = invoice;
        c.line = line;
        c.product = product;
        c.pa = pa;
        c.qty = qty;
        c.type = type;
        c.description = description;
        c.net = net;
        c.unit = unit;
        c.discount = discount;
        c.vat = vat;
        c.vatCode = vatCode;
        c.update = update;
        c.backOrdered = backOrdered;
        return c;
    }
}
