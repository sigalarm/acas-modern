package org.acas.purchase.payment;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COBOL fixed-point storage semantics used by pl080: storing without ON SIZE ERROR drops
 * high-order digits, and unsigned fields drop the sign.
 */
final class Fixed {
    static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private Fixed() {
    }

    /** Stores into {@code PIC S9(intDigits)V99}. */
    static BigDecimal signed(BigDecimal value, int intDigits) {
        BigDecimal truncated = value.setScale(2, RoundingMode.DOWN).remainder(BigDecimal.TEN.pow(intDigits));
        return truncated.signum() == 0 ? ZERO : truncated;
    }

    /** Stores into {@code PIC 9(intDigits)V99}. */
    static BigDecimal unsigned(BigDecimal value, int intDigits) {
        return signed(value, intDigits).abs();
    }

    /** Stores into an unsigned integer field of {@code digits} digits. */
    static long unsignedInt(long value, int digits) {
        return Math.abs(value) % BigDecimal.TEN.pow(digits).longValueExact();
    }

    /** Pads or truncates to a {@code PIC X(width)} field. */
    static String text(String value, int width) {
        String v = value == null ? "" : value;
        return v.length() >= width ? v.substring(0, width) : v + " ".repeat(width - v.length());
    }

    static BigDecimal money(String value) {
        return value == null || value.isBlank() ? ZERO : new BigDecimal(value.trim()).setScale(2, RoundingMode.DOWN);
    }
}
