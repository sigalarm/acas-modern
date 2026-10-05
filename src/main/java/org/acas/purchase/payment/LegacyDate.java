package org.acas.purchase.payment;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** UK-form dates as pl080 accepts and shows them (the {@code maps04} rules). */
public final class LegacyDate {
    private static final DateTimeFormatter UK = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private LegacyDate() {
    }

    /**
     * Parses {@code dd/mm/ccyy}; '.', ',' and '-' are accepted as separators. Returns empty where
     * maps04 returns a zero date.
     */
    public static Optional<LocalDate> parse(String text) {
        String d = Fixed.text(text, 10).replace('.', '/').replace(',', '/').replace('-', '/');
        if (d.chars().filter(c -> c == '/').count() != 2) {
            return Optional.empty();
        }
        String days = d.substring(0, 2);
        String month = d.substring(3, 5);
        String year = d.substring(6, 10);
        if (!digits(days) || !digits(month) || !digits(year)) {
            return Optional.empty();
        }
        int y = Integer.parseInt(year);
        if (y < 1601) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.of(y, Integer.parseInt(month), Integer.parseInt(days)));
        } catch (DateTimeException e) {
            return Optional.empty();
        }
    }

    public static String format(LocalDate date) {
        return date == null ? "" : date.format(UK);
    }

    private static boolean digits(String s) {
        return s.chars().allMatch(c -> c >= '0' && c <= '9');
    }
}
