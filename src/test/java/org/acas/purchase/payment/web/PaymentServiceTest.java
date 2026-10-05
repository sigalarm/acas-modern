package org.acas.purchase.payment.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.acas.purchase.payment.web.ApiModels.AppropriationView;
import org.acas.purchase.payment.web.ApiModels.PaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

class PaymentServiceTest {
    @Test
    void rejectsAPaymentWhoseReferenceIsAlreadyTaken() throws IOException {
        String seed = new ClassPathResource("demo/purchase-ledger.fixture").getContentAsString(StandardCharsets.US_ASCII)
                + "OI5|ACME001|42001|30/09/2026|0|0|2|AC-42001|PO-5505|||0|100.00|0|0|20.00|0|0|0|0|0|0|0|0|30|0||\n";
        PaymentService service = new PaymentService(new ByteArrayResource(seed.getBytes(StandardCharsets.US_ASCII)));
        long revision = service.batch().revision();
        PaymentRequest request = new PaymentRequest(LocalDate.of(2026, 10, 5), "ACME001", new BigDecimal("100.00"),
                false, List.of(), revision);

        AppropriationView preview = service.preview(request);
        assertFalse(preview.valid());
        assertTrue(preview.errors().get(0).contains("42001"), preview.errors().toString());

        assertThrows(PaymentRejectedException.class, () -> service.save(request));
        assertEquals(0, service.batch().itemCount());
        assertEquals(new BigDecimal("0.00"), service.openItems("ACME001").get(0).paid().setScale(2));
    }
}
