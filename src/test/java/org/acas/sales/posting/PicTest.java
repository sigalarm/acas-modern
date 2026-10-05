package org.acas.sales.posting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PicTest {

    @Test
    void signedDecimalDropsHighOrderDigits() {
        assertEquals(new BigDecimal("2345678.90"), Pic.signed(new BigDecimal("12345678.90"), 7));
        assertEquals(new BigDecimal("-2345678.90"), Pic.signed(new BigDecimal("-12345678.90"), 7));
        assertEquals(new BigDecimal("0.00"), Pic.signed(new BigDecimal("10000000.00"), 7));
    }

    @Test
    void unsignedBinaryDropsSignAndHighOrderDigits() {
        assertEquals(1, Pic.unsigned(100001, 5));
        assertEquals(3, Pic.unsigned(-3, 5));
    }

    @Test
    void textIsPaddedOrTruncated() {
        assertEquals("AB ", Pic.text("AB", 3));
        assertEquals("ABC", Pic.text("ABCD", 3));
    }
}
