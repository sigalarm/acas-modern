package org.acas.purchase.payment.web;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.acas.purchase.payment.Appropriation;
import org.acas.purchase.payment.LedgerFormat;
import org.acas.purchase.payment.LineOutcome;
import org.acas.purchase.payment.OfferedLine;
import org.acas.purchase.payment.OpenItem;
import org.acas.purchase.payment.PaymentEntry;
import org.acas.purchase.payment.PaymentEntryException;
import org.acas.purchase.payment.PurchaseLedger;
import org.acas.purchase.payment.Supplier;
import org.acas.purchase.payment.web.ApiModels.AppropriationView;
import org.acas.purchase.payment.web.ApiModels.BatchView;
import org.acas.purchase.payment.web.ApiModels.LineDecision;
import org.acas.purchase.payment.web.ApiModels.LineStatus;
import org.acas.purchase.payment.web.ApiModels.LineView;
import org.acas.purchase.payment.web.ApiModels.OpenItemView;
import org.acas.purchase.payment.web.ApiModels.PaymentRequest;
import org.acas.purchase.payment.web.ApiModels.PaymentView;
import org.acas.purchase.payment.web.ApiModels.SavedPayment;
import org.acas.purchase.payment.web.ApiModels.SupplierSummary;
import org.acas.purchase.payment.web.ApiModels.SupplierView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

/** Holds the in-memory purchase ledger and the open payment batch. */
@Service
public class PaymentService {
    /**
     * Largest payment that can be appropriated: pl080 accepts a {@code PIC 9(7)V99} payment but
     * accumulates the appropriation in {@code PIC 9(6)V99}, which would wrap.
     */
    static final BigDecimal MAX_PAYMENT = new BigDecimal("999999.99");

    private final Resource seed;
    private long revision;
    private PurchaseLedger ledger;
    private PaymentEntry entry;
    private final List<PaymentView> payments = new ArrayList<>();

    public PaymentService(@Value("${acas.purchase.ledger:classpath:demo/purchase-ledger.fixture}") Resource seed) {
        this.seed = seed;
        reset();
    }

    public final synchronized BatchView reset() {
        try {
            ledger = LedgerFormat.parse(seed.getContentAsString(StandardCharsets.US_ASCII).lines().toList());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        revision++;
        openBatch();
        return batch();
    }

    public synchronized BatchView batch() {
        boolean blocked = entry == null;
        return new BatchView(blocked ? 0 : entry.batchNumber(), blocked ? 0 : entry.itemCount(),
                PaymentEntry.MAX_ITEMS, blocked ? BigDecimal.ZERO.setScale(2) : entry.batchTotal(),
                !blocked && entry.isFull(), blocked,
                blocked ? "PL121 Invoices Not Posted; Payment Entry Not Allowed" : null, LocalDate.now(),
                List.copyOf(payments), revision);
    }

    public synchronized BatchView closeBatch() {
        requireEntry().close();
        revision++;
        openBatch();
        return batch();
    }

    public synchronized List<SupplierSummary> suppliers(String query) {
        String q = query == null ? "" : query.strip().toUpperCase(Locale.ROOT);
        return ledger.suppliers().stream()
                .filter(s -> s.key().startsWith(q) || s.name().toUpperCase(Locale.ROOT).contains(q))
                .map(s -> new SupplierSummary(s.key().strip(), s.name()))
                .toList();
    }

    public synchronized Optional<SupplierView> supplier(String account) {
        return ledger.supplier(account.toUpperCase(Locale.ROOT))
                .map(s -> new SupplierView(s.key().strip(), s.name(), s.addressLines(ledger.addressDelimiter()),
                        s.currentBalance(), s.unappliedBalance()));
    }

    public synchronized List<OpenItemView> openItems(String account) {
        return ledger.openItems(account.toUpperCase(Locale.ROOT)).stream().map(PaymentService::openItem).toList();
    }

    /** Appropriates the payment against a copy of the ledger; nothing is saved. */
    public synchronized AppropriationView preview(PaymentRequest request) {
        PaymentEntry copy = requireEntry().copy(ledger.copy());
        return appropriate(copy, request, revision).view();
    }

    /** Saves the payment if its appropriation has no errors. */
    public synchronized SavedPayment save(PaymentRequest request) {
        if (request.revision() == null || request.revision() != revision) {
            throw new StalePreviewException();
        }
        AppropriationView preview = preview(request);
        if (!preview.valid()) {
            throw new PaymentRejectedException(preview);
        }
        Result result = appropriate(requireEntry(), request, revision);
        revision++;
        PaymentView payment = payment(result.record());
        payments.add(payment);
        return new SavedPayment(payment, result.view(), batch());
    }

    private record Result(AppropriationView view, OpenItem record) {
    }

    private static Result appropriate(PaymentEntry entry, PaymentRequest request, long revision) {
        if (request.date() == null) {
            throw new PaymentEntryException(PaymentEntryException.Reason.INVALID_DATE, "Payment date is required");
        }
        if (request.amount() != null && request.amount().compareTo(MAX_PAYMENT) > 0) {
            throw new PaymentEntryException(PaymentEntryException.Reason.INVALID_AMOUNT,
                    "Amount must be between 0.01 and " + MAX_PAYMENT);
        }
        Supplier supplier = entry.supplier(request.supplier());
        Appropriation appropriation = request.allocateUnapplied()
                ? entry.startUnappliedAllocation(request.date(), supplier, request.amount())
                : entry.startPayment(request.date(), supplier, request.amount());
        Map<Long, LineDecision> decisions = request.lines() == null ? Map.of()
                : request.lines().stream().collect(Collectors.toMap(LineDecision::invoice, Function.identity(),
                        (a, b) -> b));
        List<LineView> lines = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Optional<OfferedLine> next;
        while ((next = appropriation.next()).isPresent()) {
            OfferedLine line = next.get();
            LineDecision decision = decisions.get(line.invoice());
            BigDecimal amount = decision == null || decision.amount() == null ? line.suggested()
                    : lineAmount(line.invoice(), decision.amount());
            BigDecimal remaining = appropriation.paymentValue().subtract(appropriation.appropriated());
            LineOutcome outcome = amount.compareTo(remaining) > 0 ? LineOutcome.TOO_HIGH : appropriation.pay(amount);
            if (outcome == LineOutcome.TOO_HIGH) {
                String message = "Payment Too High";
                errors.add("Invoice " + line.invoice() + ": " + message);
                lines.add(line(line, amount, LineStatus.TOO_HIGH, false, false, message));
                appropriation.pay(BigDecimal.ZERO);
                continue;
            }
            if (outcome == LineOutcome.NO_CHANGE) {
                lines.add(line(line, BigDecimal.ZERO.setScale(2), LineStatus.NO_CHANGE, false, false, null));
                continue;
            }
            boolean prompted = outcome == LineOutcome.SETTLE_PROMPT;
            boolean settled = false;
            if (prompted) {
                settled = decision == null || decision.settleInFull() == null || decision.settleInFull();
                appropriation.settle(settled);
            }
            LineStatus status = appropriation.lineCleared() ? LineStatus.CLEARED : LineStatus.PART_PAID;
            lines.add(line(line, amount.setScale(2), status, prompted, settled, null));
        }
        for (OpenItem item : appropriation.remainingInvoices()) {
            lines.add(new LineView(item.invoice(), item.date(), item.ref(), item.net().add(item.carriage())
                    .add(item.vat()).add(item.cVat()).subtract(item.paid()), BigDecimal.ZERO.setScale(2), null,
                    null, null, null, LineStatus.NOT_REACHED, false, false, "Payment fully appropriated"));
        }
        BigDecimal appropriated = appropriation.appropriated();
        BigDecimal deduction = appropriation.deductionTaken();
        OpenItem record = appropriation.finish();
        if (!appropriation.recordWritten()) {
            errors.add("Supplier " + record.supplier().strip() + " already has an item numbered " + record.invoice()
                    + "; this payment reference cannot be used");
        }
        AppropriationView view = new AppropriationView(entry.batchNumber(), entry.itemCount(),
                appropriation.transactionType(), appropriation.paymentValue(), appropriated,
                appropriation.paymentValue().subtract(appropriated), deduction, lines, errors, revision);
        return new Result(view, record);
    }

    private static BigDecimal lineAmount(long invoice, BigDecimal amount) {
        if (amount.signum() < 0 || amount.stripTrailingZeros().scale() > 2) {
            throw new PaymentEntryException(PaymentEntryException.Reason.INVALID_AMOUNT,
                    "Invoice " + invoice + ": amount must be zero or more, in whole pence");
        }
        return amount.setScale(2);
    }

    private static LineView line(OfferedLine line, BigDecimal applied, LineStatus status, boolean prompted,
                                 boolean settled, String message) {
        return new LineView(line.invoice(), line.date(), line.ref(), line.outstanding(), line.discount(),
                line.amountDue(), line.proposal(), line.suggested(), applied, status, prompted, settled, message);
    }

    private static PaymentView payment(OpenItem record) {
        return new PaymentView(record.invoice(), record.supplier().strip(), record.date(), record.type(),
                record.paid(), record.net(), record.deductAmt(), record.batchNumber(), record.batchItem());
    }

    private static OpenItemView openItem(OpenItem o) {
        return new OpenItemView(o.invoice(), o.date(), o.type(), o.ref(), o.order(),
                o.net().add(o.carriage()).add(o.vat()).add(o.cVat()), o.paid(), o.deductAmt(), o.deductDays(),
                o.status(), o.dateCleared(), o.batchNumber(), o.batchItem());
    }

    private void openBatch() {
        payments.clear();
        try {
            entry = PaymentEntry.open(ledger);
        } catch (PaymentEntryException e) {
            entry = null;
        }
    }

    private PaymentEntry requireEntry() {
        if (entry == null) {
            throw new PaymentEntryException(PaymentEntryException.Reason.INVOICES_NOT_POSTED,
                    "PL121 Invoices Not Posted; Payment Entry Not Allowed");
        }
        return entry;
    }
}
