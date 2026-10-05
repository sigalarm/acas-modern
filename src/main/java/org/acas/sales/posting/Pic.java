package org.acas.sales.posting;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COBOL picture-clause storage semantics. Arithmetic without ON SIZE ERROR silently drops
 * high-order digits, and unsigned fields drop the sign.
 */
final class Pic {

    private Pic() {
    }

    /** Pads or truncates to a fixed-width alphanumeric field ({@code PIC X(width)}). */
    static String text(String value, int width) {
        String v = value == null ? "" : value;
        if (v.length() >= width) {
            return v.substring(0, width);
        }
        return v + " ".repeat(width - v.length());
    }

    /** First character of a {@code PIC X} field, space when empty. */
    static char flag(String value) {
        return value == null || value.isEmpty() ? ' ' : value.charAt(0);
    }

    /** Stores into {@code PIC S9(intDigits)V99}. */
    static BigDecimal signed(BigDecimal value, int intDigits) {
        BigDecimal scaled = value.setScale(2, RoundingMode.DOWN);
        BigDecimal limit = BigDecimal.TEN.pow(intDigits);
        BigDecimal truncated = scaled.remainder(limit);
        return truncated.signum() == 0 ? BigDecimal.ZERO.setScale(2) : truncated;
    }

    /** Stores into {@code PIC 9(digits) COMP} (unsigned binary, truncated). */
    static int unsigned(long value, int digits) {
        return (int) (Math.abs(value) % pow10(digits));
    }

    /** Stores into {@code PIC S9(digits) COMP} (signed binary, truncated). */
    static int signedInt(long value, int digits) {
        return (int) (value % pow10(digits));
    }

    private static long pow10(int digits) {
        long p = 1;
        for (int i = 0; i < digits; i++) {
            p *= 10;
        }
        return p;
    }
}
