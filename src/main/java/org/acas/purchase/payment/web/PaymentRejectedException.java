package org.acas.purchase.payment.web;

import org.acas.purchase.payment.web.ApiModels.AppropriationView;

/** A payment that cannot be saved because its appropriation has errors. */
final class PaymentRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient AppropriationView appropriation;

    PaymentRejectedException(AppropriationView appropriation) {
        super(String.join("; ", appropriation.errors()));
        this.appropriation = appropriation;
    }

    AppropriationView appropriation() {
        return appropriation;
    }
}
