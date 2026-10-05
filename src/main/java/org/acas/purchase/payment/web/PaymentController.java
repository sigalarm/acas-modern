package org.acas.purchase.payment.web;

import java.util.List;
import org.acas.purchase.payment.web.ApiModels.AppropriationView;
import org.acas.purchase.payment.web.ApiModels.BatchView;
import org.acas.purchase.payment.web.ApiModels.OpenItemView;
import org.acas.purchase.payment.web.ApiModels.PaymentRequest;
import org.acas.purchase.payment.web.ApiModels.SavedPayment;
import org.acas.purchase.payment.web.ApiModels.SupplierSummary;
import org.acas.purchase.payment.web.ApiModels.SupplierView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Purchase ledger payment data entry (legacy pl080). */
@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentService service;

    public PaymentController(PaymentService service) {
        this.service = service;
    }

    @GetMapping("/batch")
    public BatchView batch() {
        return service.batch();
    }

    @PostMapping("/batch/close")
    public BatchView closeBatch() {
        return service.closeBatch();
    }

    @GetMapping("/suppliers")
    public List<SupplierSummary> suppliers(@RequestParam(name = "query", required = false) String query) {
        return service.suppliers(query);
    }

    @GetMapping("/suppliers/{account}")
    public ResponseEntity<SupplierView> supplier(@PathVariable("account") String account) {
        return ResponseEntity.of(service.supplier(account));
    }

    @GetMapping("/suppliers/{account}/open-items")
    public List<OpenItemView> openItems(@PathVariable("account") String account) {
        return service.openItems(account);
    }

    @PostMapping("/payments/preview")
    public AppropriationView preview(@RequestBody PaymentRequest request) {
        return service.preview(request);
    }

    @PostMapping("/payments")
    public SavedPayment save(@RequestBody PaymentRequest request) {
        return service.save(request);
    }

    @PostMapping("/demo/reset")
    public BatchView reset() {
        return service.reset();
    }
}
