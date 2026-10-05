package org.acas.purchase.payment.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Request and response bodies of the payment entry API. */
public final class ApiModels {
    private ApiModels() {
    }

    public record BatchView(int batchNumber, int itemCount, int maxItems, BigDecimal batchTotal, boolean full,
                            boolean blocked, String blockedMessage, LocalDate defaultDate,
                            List<PaymentView> payments) {
    }

    public record SupplierSummary(String account, String name) {
    }

    public record SupplierView(String account, String name, List<String> addressLines, BigDecimal currentBalance,
                               BigDecimal unappliedBalance) {
    }

    /**
     * A payment to preview or save. {@code amount} is the payment value, or the part of the unapplied
     * balance to allocate when {@code allocateUnapplied} is set. {@code lines} override the amount
     * applied to individual invoices.
     */
    public record PaymentRequest(LocalDate date, String supplier, BigDecimal amount, boolean allocateUnapplied,
                                 List<LineDecision> lines) {
    }

    /**
     * {@code amount} null takes the line's suggested amount. {@code settleInFull} answers the settle
     * prompt; null takes pl080's default (yes).
     */
    public record LineDecision(long invoice, BigDecimal amount, Boolean settleInFull) {
    }

    public enum LineStatus { CLEARED, PART_PAID, NO_CHANGE, TOO_HIGH, NOT_REACHED }

    public record LineView(long invoice, LocalDate date, String ref, BigDecimal outstanding, BigDecimal discount,
                           BigDecimal amountDue, BigDecimal proposal, BigDecimal suggested, BigDecimal applied,
                           LineStatus status,
                           boolean settlePrompted, boolean settledInFull, String message) {
    }

    public record AppropriationView(int batchNumber, int batchItem, int transactionType, BigDecimal paymentValue,
                                    BigDecimal appropriated, BigDecimal unappropriated, BigDecimal deductionTaken,
                                    List<LineView> lines, List<String> errors) {
        public boolean valid() {
            return errors.isEmpty();
        }
    }

    public record PaymentView(long reference, String supplier, LocalDate date, int transactionType,
                              BigDecimal value, BigDecimal appropriated, BigDecimal deductionTaken, int batchNumber,
                              int batchItem) {
    }

    public record SavedPayment(PaymentView payment, AppropriationView appropriation, BatchView batch) {
    }

    public record OpenItemView(long invoice, LocalDate date, int type, String ref, String order, BigDecimal gross,
                               BigDecimal paid, BigDecimal deductAmt, int deductDays, int status,
                               LocalDate dateCleared, int batchNumber, int batchItem) {
    }

    public record ApiError(String code, String message, AppropriationView appropriation) {
    }
}
