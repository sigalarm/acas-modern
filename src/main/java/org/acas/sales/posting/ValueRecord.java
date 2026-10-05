package org.acas.sales.posting;

import java.math.BigDecimal;

/** Sales analysis value record ({@code WS-Value-Record} in wsval.cob), keyed by a 3-char code. */
public final class ValueRecord {

    public String code;
    public int gl;
    public String desc;
    public String print;
    public int tThis;
    public int tLast;
    public int tYear;
    public BigDecimal vThis = Money.ZERO;
    public BigDecimal vLast = Money.ZERO;
    public BigDecimal vYear = Money.ZERO;

    public ValueRecord(String code, int gl, String desc, String print) {
        this.code = Pic.text(code, 3);
        this.gl = gl;
        this.desc = Pic.text(desc, 24);
        this.print = Pic.text(print, 3);
    }

    /** The record left behind by a failed indexed read: the DAL moves spaces to it. */
    static ValueRecord blank() {
        return new ValueRecord("", 0, "", "");
    }

    /** {@code move WS-Analysis-Record to WS-Value-Record} followed by zeroing every counter. */
    static ValueRecord fromAnalysis(AnalysisRecord a) {
        return new ValueRecord(a.code(), a.gl(), a.desc(), a.print());
    }

    char second() {
        return code.charAt(2);
    }

    void setGroup(String group) {
        code = code.charAt(0) + Pic.text(group, 2);
    }

    void clearSecond() {
        code = code.substring(0, 2) + ' ';
    }

    void accumulate(int count, BigDecimal value) {
        tThis = Pic.unsigned((long) tThis + count, 5);
        tYear = Pic.unsigned((long) tYear + count, 5);
        vThis = Pic.signed(vThis.add(value), 8);
        vYear = Pic.signed(vYear.add(value), 8);
    }

    ValueRecord copy() {
        ValueRecord c = new ValueRecord(code, gl, desc, print);
        c.tThis = tThis;
        c.tLast = tLast;
        c.tYear = tYear;
        c.vThis = vThis;
        c.vLast = vLast;
        c.vYear = vYear;
        return c;
    }
}
