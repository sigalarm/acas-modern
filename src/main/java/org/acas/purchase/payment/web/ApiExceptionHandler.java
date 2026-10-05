package org.acas.purchase.payment.web;

import org.acas.purchase.payment.PaymentEntryException;
import org.acas.purchase.payment.web.ApiModels.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class ApiExceptionHandler {
    @ExceptionHandler(PaymentEntryException.class)
    ResponseEntity<ApiError> refused(PaymentEntryException e) {
        HttpStatus status = switch (e.reason()) {
            case UNKNOWN_SUPPLIER -> HttpStatus.NOT_FOUND;
            case INVOICES_NOT_POSTED, BATCH_FULL, ILLEGAL_STATE -> HttpStatus.CONFLICT;
            default -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return ResponseEntity.status(status).body(new ApiError(e.reason().name(), e.getMessage(), null));
    }

    @ExceptionHandler(StalePreviewException.class)
    ResponseEntity<ApiError> stale(StalePreviewException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ApiError("STALE_PREVIEW", e.getMessage(), null));
    }

    @ExceptionHandler(PaymentRejectedException.class)
    ResponseEntity<ApiError> rejected(PaymentRejectedException e) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(new ApiError("APPROPRIATION_ERRORS", e.getMessage(), e.appropriation()));
    }
}
