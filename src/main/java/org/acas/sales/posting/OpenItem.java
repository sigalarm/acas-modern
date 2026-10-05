package org.acas.sales.posting;

import java.math.BigDecimal;

/**
 * Open item handed from the post extract (sl055) to sl060 via openitm2.dat
 * ({@code OI-Header} in slwsoi.cob).
 */
public final class OpenItem {

    public String customer = Pic.text("", 7);
    public int invoice;
    public int date;
    public int batchNos;
    public int batchItem;
    public int type;
    public String description = Pic.text("", 25);
    public char holdFlag = ' ';
    public char unapplied = ' ';
    public BigDecimal pc = Money.ZERO;
    public BigDecimal net = Money.ZERO;
    public BigDecimal extra = Money.ZERO;
    public BigDecimal carriage = Money.ZERO;
    public BigDecimal vat = Money.ZERO;
    public BigDecimal discount = Money.ZERO;
    public BigDecimal eVat = Money.ZERO;
    public BigDecimal cVat = Money.ZERO;
    public BigDecimal paid = Money.ZERO;
    public int status;
    public int deductDays;
    public BigDecimal deductAmt = Money.ZERO;
    public BigDecimal deductVat = Money.ZERO;
    public int days;
    public int cr;
    public char applied = ' ';
    public int dateCleared;
}
